/**
 * Cuarentena — §10, punto 5: «ninguna imagen entra al pool sin pasar por
 * revisión».
 *
 * Es lo que hace asumible que el enlace sea abierto. Una captura aportada queda
 * en `PENDING` y no la ve el pipeline de ML hasta que alguien con el rol de
 * administrador la aprueba.
 *
 * Rechazar **no borra la imagen**: la marca. Borrar de verdad es irreversible y
 * un rechazo puede ser un error de criterio; el blob se limpia aparte, cuando
 * alguien lo decide a conciencia.
 */

import { app, HttpRequest, HttpResponseInit, InvocationContext } from "@azure/functions";
import { isAdministrator } from "../auth";
import { readSasUrl } from "../blob";
import { capturesContainer, isNotFound } from "../cosmos";
import type { CaptureDocument } from "../model";
import { recordApproved, recordRejected } from "../stats";

const PAGE_SIZE = 20;

export async function pendingReviews(request: HttpRequest): Promise<HttpResponseInit> {
  if (!isAdministrator(request)) {
    return { status: 403, jsonBody: { message: "Solo administración" } };
  }

  const cursor = request.query.get("cursor") ?? undefined;
  const iterator = capturesContainer().items.query<CaptureDocument>(
    {
      query:
        "SELECT * FROM c WHERE c.status = 'PENDING' AND c.imageUploaded = true ORDER BY c.registeredAt ASC",
    },
    { maxItemCount: PAGE_SIZE, continuationToken: cursor },
  );

  const page = await iterator.fetchNext();
  return {
    status: 200,
    jsonBody: {
      items: page.resources.map((capture) => ({
        ...capture,
        imageUrl: readSasUrl(capture.blobPath),
      })),
      continuationToken: page.continuationToken ?? null,
    },
  };
}

export async function decideReview(
  request: HttpRequest,
  context: InvocationContext,
): Promise<HttpResponseInit> {
  if (!isAdministrator(request)) {
    return { status: 403, jsonBody: { message: "Solo administración" } };
  }

  const captureId = request.params.id;
  if (!captureId) return { status: 400, jsonBody: { message: "Falta el identificador" } };

  const body = (await request.json().catch(() => null)) as {
    contributorId?: string;
    decision?: string;
    note?: string | null;
  } | null;

  if (!body?.contributorId || (body.decision !== "APPROVED" && body.decision !== "REJECTED")) {
    return { status: 400, jsonBody: { message: "Decisión inválida" } };
  }

  let capture: CaptureDocument | undefined;
  try {
    const { resource } = await capturesContainer()
      .item(captureId, body.contributorId)
      .read<CaptureDocument>();
    capture = resource;
  } catch (error) {
    if (!isNotFound(error)) throw error;
  }
  if (!capture) return { status: 404, jsonBody: { message: "Captura no encontrada" } };

  if (capture.status !== "PENDING") {
    // Ya resuelta: se responde el estado actual sin volver a mover contadores.
    return { status: 200, jsonBody: { status: capture.status } };
  }

  await capturesContainer()
    .item(captureId, body.contributorId)
    .patch([
      { op: "set", path: "/status", value: body.decision },
      { op: "set", path: "/reviewedAt", value: new Date().toISOString() },
      { op: "set", path: "/reviewNote", value: body.note ?? null },
    ]);

  const contaminated = capture.contamination !== null && capture.contamination !== "CLEAN";
  if (body.decision === "APPROVED") {
    await recordApproved(capture.material, contaminated);
  } else {
    await recordRejected(capture.material, contaminated);
  }

  context.log(`Captura ${captureId} → ${body.decision}`);
  return { status: 200, jsonBody: { status: body.decision } };
}

app.http("pendingReviews", {
  methods: ["GET"],
  authLevel: "anonymous",
  route: "review/pending",
  handler: pendingReviews,
});

app.http("decideReview", {
  methods: ["POST"],
  authLevel: "anonymous",
  route: "review/{id}",
  handler: decideReview,
});
