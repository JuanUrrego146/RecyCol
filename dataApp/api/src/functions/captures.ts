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
 *
 * ## Dos topes diarios, no uno
 *
 * El de **aportante** protege el equilibrio del dataset frente a una sola persona
 * muy entusiasta, y se cuenta al confirmar. El de **dirección de origen** protege
 * la cuenta de almacenamiento frente a un guion, se cuenta al registrar y es el
 * primero que se mira, antes de escribir nada. Hacen falta los dos:
 * `contributorId` viaja en el cuerpo de la petición, así que un identificador
 * nuevo por llamada no roza el primero. El porqué completo está en
 * `ratelimit.ts`.
 */

import { app, HttpRequest, HttpResponseInit, InvocationContext } from "@azure/functions";
import { readPrincipal } from "../auth";
import { blobExists, ensureContainer, uploadSasUrl } from "../blob";
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
import { DAILY_IP_CAPTURE_LIMIT, clientIp, ipQuotaKey } from "../ratelimit";
import { recordCollected } from "../stats";
import {
  addPending,
  bumpContributorCount,
  consumeIpQuota,
  createCapture,
  ensureTables,
  findCaptureById,
  hasDuplicate,
  readCapture,
  readContributor,
  replaceCapture,
  updateContributor,
  upsertContributor,
} from "../store";

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
      return { status: 403, jsonBody: { message: "El aporte no corresponde a la sesión activa." } };
    }
  } else if (isAccountId(record.contributorId)) {
    return {
      status: 401,
      jsonBody: { message: "Este aporte dice venir de una cuenta, pero no hay sesión iniciada." },
    };
  }

  await ensureTables();

  // Idempotencia: si ya está registrada, se devuelve una firma nueva y se sale.
  // Volver a escribir el documento permitiría cambiar la etiqueta de una captura
  // ya revisada mandando el mismo id.
  const existing = await readCapture(record.contributorId, record.id);
  if (existing) {
    return {
      status: 200,
      jsonBody: {
        captureId: existing.id,
        uploadUrl: uploadSasUrl(existing.blobPath),
        blobPath: existing.blobPath,
      },
    };
  }

  // Tope por dirección de origen, **antes de escribir nada**.
  //
  // El tope de más abajo cuenta contra un identificador que manda el propio
  // cliente, así que un UUID nuevo por llamada se lo salta entero; este no,
  // porque la dirección la pone la plataforma. Va aquí arriba y no junto al otro
  // por una razón concreta: `touchContributor` **crea** el documento del aportante
  // y suma al recuento de personas. Dejarlo delante significaría que una ráfaga
  // frenada sigue llenando la tabla de aportantes fantasma, uno por petición.
  //
  // Se descuenta al registrar y no al confirmar: lo que hay que frenar es la
  // ráfaga de registros —cada uno emite una firma de escritura contra el
  // almacenamiento—, aunque la imagen no llegue nunca. Los reintentos de una
  // captura ya registrada salen antes, por la comprobación de idempotencia, así
  // que la cola sin cobertura no gasta cuota repitiendo. Ver `ratelimit.ts`.
  const ip = clientIp(request);
  if (ip) {
    const day = todayStamp();
    const verdict = await consumeIpQuota(day, ipQuotaKey(day, ip), DAILY_IP_CAPTURE_LIMIT);
    if (verdict === "LIMIT") {
      context.log(`Cuota diaria por dirección agotada (${DAILY_IP_CAPTURE_LIMIT})`);
      return {
        status: 429,
        jsonBody: {
          message:
            "Desde esta red ya se aportó el máximo de hoy. Si estáis varios en la misma conexión, probad mañana o desde otra red.",
        },
      };
    }
    if (verdict === "BUSY") {
      return {
        status: 503,
        headers: { "retry-after": "5" },
        jsonBody: {
          message: "Hay mucho tráfico ahora mismo. Tu foto está guardada y se reintenta sola.",
        },
      };
    }
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
  if (await hasDuplicate(record.contributorId, record.phash)) {
    return { status: 409, jsonBody: { message: "Esta foto ya se había aportado." } };
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

  await createCapture(document);
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

  const capture = await findCaptureById(captureId);
  if (!capture) return { status: 404, jsonBody: { message: "Captura no encontrada" } };

  if (capture.imageUploaded) {
    // Reintento después de una respuesta perdida: no se cuenta dos veces.
    return { status: 200, jsonBody: { status: capture.status } };
  }

  if (!(await blobExists(capture.blobPath))) {
    return { status: 409, jsonBody: { message: "La imagen todavía no llegó al almacenamiento" } };
  }

  const confirmed: CaptureDocument = {
    ...capture,
    imageUploaded: true,
    uploadedAt: new Date().toISOString(),
  };
  await replaceCapture(confirmed);
  await addPending(confirmed);

  await recordCollected(
    capture.material,
    capture.contamination !== null && capture.contamination !== "CLEAN",
  );
  await bumpQuota(capture.contributorId);

  context.log(`Captura confirmada ${capture.id}`);
  return { status: 200, jsonBody: { status: capture.status } };
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
  const today = todayStamp();
  const existing = await readContributor(contributorId);

  if (existing) {
    if (existing.quotaDay !== today) {
      const reset = await updateContributor(contributorId, (current) => ({
        ...current,
        quotaDay: today,
        quotaUsed: 0,
        lastSeenAt: new Date().toISOString(),
        consentVersion,
      }));
      return reset ?? existing;
    }
    return existing;
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
  await upsertContributor(created);
  await bumpContributorCount();
  return created;
}

async function bumpQuota(contributorId: string): Promise<void> {
  try {
    await updateContributor(contributorId, (current) => ({
      ...current,
      quotaUsed: current.quotaUsed + 1,
      capturesRegistered: current.capturesRegistered + 1,
      lastSeenAt: new Date().toISOString(),
    }));
  } catch {
    // El contador es un freno antiabuso, no contabilidad: si falla, no se tumba
    // un aporte legítimo que ya está guardado.
  }
}

app.http("registerCapture", {
  methods: ["POST"],
  authLevel: "anonymous",
  route: "api/captures",
  handler: registerCapture,
});

app.http("commitCapture", {
  methods: ["POST"],
  authLevel: "anonymous",
  route: "api/captures/{id}/commit",
  handler: commitCapture,
});
