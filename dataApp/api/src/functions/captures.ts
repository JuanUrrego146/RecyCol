/**
 * Registro y confirmación de capturas.
 *
 * `POST /api/captures` — valida los metadatos, reserva la ruta del blob, devuelve
 * una SAS de escritura de un solo blob y guarda el registro en `PENDING`. Es
 * **idempotente por `id`**: reintentarlo tras un corte de red devuelve una SAS
 * nueva sin duplicar nada, que es lo que hace segura la cola offline del cliente.
 *
 * `POST /api/captures/{id}/commit` — comprueba que la imagen llegó de verdad al
 * almacenamiento y solo entonces la cuenta. Sin este paso, un registro cuya
 * subida se cortó a mitad contaría como aporte en las misiones y nadie tendría la
 * foto.
 */

import { app, HttpRequest, HttpResponseInit, InvocationContext } from "@azure/functions";
import { readPrincipal } from "../auth";
import { blobExists, ensureContainer, uploadSasUrl } from "../blob";
import { capturesContainer, contributorsContainer, isNotFound } from "../cosmos";
import {
  CaptureDocument,
  ContributorDocument,
  DAILY_CAPTURE_LIMIT,
  ValidationError,
  accountIdFor,
  assignSplit,
  blobPathFor,
  isAccountId,
  parseCaptureRecord,
  todayStamp,
} from "../model";
import { recordCollected } from "../stats";

export async function registerCapture(
  request: HttpRequest,
  context: InvocationContext,
): Promise<HttpResponseInit> {
  let record;
  try {
    record = parseCaptureRecord(await request.json());
  } catch (error) {
    if (error instanceof ValidationError) {
      return { status: 400, jsonBody: { message: error.message } };
    }
    return { status: 400, jsonBody: { message: "Cuerpo JSON inválido" } };
  }

  // La identidad la manda la sesión, no el cuerpo de la petición.
  //
  // Sin esto, cualquiera podría poner en `contributorId` la cuenta de otro
  // estudiante y atribuirle fotos —para inflarle el conteo o para ensuciárselo—,
  // y ese conteo es justamente lo que un profesor va a mirar para dar puntos.
  const principal = readPrincipal(request);
  if (principal) {
    if (record.contributorId !== accountIdFor(principal.userId)) {
      return {
        status: 403,
        jsonBody: { message: "El aporte no corresponde a la sesión activa." },
      };
    }
  } else if (isAccountId(record.contributorId)) {
    return {
      status: 401,
      jsonBody: { message: "Este aporte dice venir de una cuenta, pero no hay sesión iniciada." },
    };
  }

  const container = capturesContainer();

  // Idempotencia: si ya está registrada, se devuelve una firma nueva y se sale.
  // Volver a escribir el documento permitiría cambiar la etiqueta de una captura
  // ya revisada mandando el mismo id.
  try {
    const { resource } = await container
      .item(record.id, record.contributorId)
      .read<CaptureDocument>();
    if (resource) {
      return {
        status: 200,
        jsonBody: {
          captureId: resource.id,
          uploadUrl: uploadSasUrl(resource.blobPath),
          blobPath: resource.blobPath,
        },
      };
    }
  } catch (error) {
    if (!isNotFound(error)) throw error;
  }

  const contributor = await touchContributor(record.contributorId, record.consentVersion);
  if (contributor.quotaUsed >= DAILY_CAPTURE_LIMIT) {
    return {
      status: 429,
      jsonBody: {
        message: `Has llegado al máximo de ${DAILY_CAPTURE_LIMIT} aportes por día. Gracias, en serio: vuelve mañana.`,
      },
    };
  }

  // La misma foto reenviada: se rechaza antes de gastar almacenamiento. La
  // deduplicación seria —contra el pool y contra RealWaste— la hace el pipeline
  // de ML; esta solo evita el duplicado obvio dentro del mismo aportante.
  const duplicate = await findDuplicate(record.contributorId, record.phash);
  if (duplicate) {
    return {
      status: 409,
      jsonBody: { message: "Esta foto ya se había aportado." },
    };
  }

  await ensureContainer();
  const blobPath = blobPathFor(record);
  const document: CaptureDocument = {
    ...record,
    blobPath,
    status: "PENDING",
    imageUploaded: false,
    split: contributor.split,
    registeredAt: new Date().toISOString(),
    uploadedAt: null,
    reviewedAt: null,
    reviewNote: null,
  };

  await container.items.create(document);
  context.log(`Captura registrada ${record.id} (${record.material}, ${contributor.split})`);

  return {
    status: 201,
    jsonBody: { captureId: record.id, uploadUrl: uploadSasUrl(blobPath), blobPath },
  };
}

export async function commitCapture(
  request: HttpRequest,
  context: InvocationContext,
): Promise<HttpResponseInit> {
  const captureId = request.params.id;
  if (!captureId) return { status: 400, jsonBody: { message: "Falta el identificador" } };

  // La partición es el aportante, así que hace falta una consulta entre
  // particiones para localizar la captura por id. Es una lectura pequeña y solo
  // ocurre una vez por foto.
  const { resources } = await capturesContainer()
    .items.query<CaptureDocument>({
      query: "SELECT * FROM c WHERE c.id = @id",
      parameters: [{ name: "@id", value: captureId }],
    })
    .fetchAll();

  const capture = resources[0];
  if (!capture) return { status: 404, jsonBody: { message: "Captura no encontrada" } };

  if (capture.imageUploaded) {
    // Reintento después de una respuesta perdida: no se cuenta dos veces.
    return { status: 200, jsonBody: { status: capture.status } };
  }

  if (!(await blobExists(capture.blobPath))) {
    return { status: 409, jsonBody: { message: "La imagen todavía no llegó al almacenamiento" } };
  }

  await capturesContainer()
    .item(capture.id, capture.contributorId)
    .patch([
      { op: "set", path: "/imageUploaded", value: true },
      { op: "set", path: "/uploadedAt", value: new Date().toISOString() },
    ]);

  await recordCollected(capture.material, capture.contamination !== null && capture.contamination !== "CLEAN");
  await bumpQuota(capture.contributorId);

  context.log(`Captura confirmada ${capture.id}`);
  return { status: 200, jsonBody: { status: capture.status } };
}

async function findDuplicate(contributorId: string, phash: string): Promise<boolean> {
  const { resources } = await capturesContainer()
    .items.query<{ id: string }>(
      {
        query: "SELECT TOP 1 c.id FROM c WHERE c.phash = @phash",
        parameters: [{ name: "@phash", value: phash }],
      },
      { partitionKey: contributorId },
    )
    .fetchAll();
  return resources.length > 0;
}

/**
 * Crea o refresca el documento del aportante.
 *
 * El `split` se calcula **solo al crearlo** y no se vuelve a tocar: es la
 * garantía de que el control propio lo aportan personas que no aparecen en
 * entrenamiento (§10, punto 3). Recalcularlo dejaría a alguien cruzando de lado
 * y arruinaría justo eso.
 */
async function touchContributor(
  contributorId: string,
  consentVersion: string,
): Promise<ContributorDocument> {
  const container = contributorsContainer();
  const today = todayStamp();

  try {
    const { resource } = await container.item(contributorId, contributorId).read<ContributorDocument>();
    if (resource) {
      if (resource.quotaDay !== today) {
        await container.item(contributorId, contributorId).patch([
          { op: "set", path: "/quotaDay", value: today },
          { op: "set", path: "/quotaUsed", value: 0 },
          { op: "set", path: "/lastSeenAt", value: new Date().toISOString() },
          { op: "set", path: "/consentVersion", value: consentVersion },
        ]);
        return { ...resource, quotaDay: today, quotaUsed: 0 };
      }
      return resource;
    }
  } catch (error) {
    if (!isNotFound(error)) throw error;
  }

  const created: ContributorDocument = {
    id: contributorId,
    split: assignSplit(contributorId),
    createdAt: new Date().toISOString(),
    lastSeenAt: new Date().toISOString(),
    consentVersion,
    capturesRegistered: 0,
    quotaDay: today,
    quotaUsed: 0,
    account: null,
    linkedContributorIds: [],
  };
  try {
    await container.items.create(created);
  } catch (error) {
    if ((error as { code?: number }).code !== 409) throw error;
    const { resource } = await container.item(contributorId, contributorId).read<ContributorDocument>();
    if (resource) return resource;
  }
  return created;
}

async function bumpQuota(contributorId: string): Promise<void> {
  try {
    await contributorsContainer()
      .item(contributorId, contributorId)
      .patch([
        { op: "incr", path: "/quotaUsed", value: 1 },
        { op: "incr", path: "/capturesRegistered", value: 1 },
        { op: "set", path: "/lastSeenAt", value: new Date().toISOString() },
      ]);
  } catch (error) {
    // El contador es un freno antiabuso, no contabilidad: si falla, no se tumba
    // un aporte legítimo que ya está guardado.
    if (!isNotFound(error)) throw error;
  }
}

app.http("registerCapture", {
  methods: ["POST"],
  authLevel: "anonymous",
  route: "captures",
  handler: registerCapture,
});

app.http("commitCapture", {
  methods: ["POST"],
  authLevel: "anonymous",
  route: "captures/{id}/commit",
  handler: commitCapture,
});
