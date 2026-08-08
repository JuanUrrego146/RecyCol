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
 *
 * La cola se lee de la tabla-índice `pendingreview` en vez de barrer todas las
 * capturas: Table Storage no tiene índices secundarios, y sin ese índice cada
 * carga de la pantalla recorrería el dataset entero.
 */

import { app, HttpRequest, HttpResponseInit, InvocationContext } from "@azure/functions";
import { isAdministrator } from "../auth";
import { readSasUrl } from "../blob";
import type { CaptureDocument } from "../model";
import { recordApproved, recordRejected } from "../stats";
import { ensureTables, listPending, readCapture, removePending, replaceCapture } from "../store";

const PAGE_SIZE = 20;

export async function pendingReviews(request: HttpRequest): Promise<HttpResponseInit> {
  if (!isAdministrator(request)) {
    return { status: 403, jsonBody: { message: "Solo administración" } };
  }

  await ensureTables();
  const cursor = request.query.get("cursor") ?? undefined;
  const page = await listPending(PAGE_SIZE, cursor);

  const captures = await Promise.all(
    page.items.map((entry) => readCapture(entry.contributorId, entry.captureId)),
  );

  return {
    status: 200,
    jsonBody: {
      items: captures
        .filter((capture): capture is CaptureDocument => capture !== null)
        .map((capture) => ({ ...capture, imageUrl: readSasUrl(capture.blobPath) })),
      continuationToken: page.continuationToken,
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

  const capture = await readCapture(body.contributorId, captureId);
  if (!capture) return { status: 404, jsonBody: { message: "Captura no encontrada" } };

  if (capture.status !== "PENDING") {
    // Ya resuelta: se responde el estado actual sin volver a mover contadores.
    return { status: 200, jsonBody: { status: capture.status } };
  }

  const decided: CaptureDocument = {
    ...capture,
    status: body.decision,
    reviewedAt: new Date().toISOString(),
    reviewNote: body.note ?? null,
  };
  await replaceCapture(decided);
  await removePending(capture);

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
  route: "api/review/pending",
  handler: pendingReviews,
});

app.http("decideReview", {
  methods: ["POST"],
  authLevel: "anonymous",
  route: "api/review/{id}",
  handler: decideReview,
});
