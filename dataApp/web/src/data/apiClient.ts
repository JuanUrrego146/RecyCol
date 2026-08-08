/**
 * Cliente de la API.
 *
 * El envío de una captura son tres pasos y hay una razón para cada uno:
 *
 *   1. `POST /api/captures` registra los metadatos y devuelve una **SAS de
 *      escritura de un solo uso**.
 *   2. `PUT` de la imagen **directamente a Blob Storage** con esa SAS. La foto
 *      no atraviesa la función: se ahorra el límite de tamaño de petición, se
 *      ahorra ancho de banda de cómputo y sube más rápido.
 *   3. `POST /api/captures/{id}/commit` confirma que la imagen llegó. Sin este
 *      paso, un registro cuya subida se cortó contaría como aporte en las
 *      misiones y nadie tendría la foto.
 *
 * El paso 1 es idempotente por `id`: reintentar tras un corte no duplica nada y
 * devuelve una SAS nueva, que es lo que hace segura la cola offline.
 */

import type { CaptureRecord, ReviewStatus, StoredCapture } from "../domain/capture";
import type { Material } from "../domain/materials";
import type { MaterialTally } from "../domain/missions";

const API_BASE: string = import.meta.env.VITE_API_BASE ?? "/api";

export interface RegisteredCapture {
  readonly captureId: string;
  readonly uploadUrl: string;
  readonly blobPath: string;
}

export interface StatsResponse {
  readonly tally: Partial<Record<Material, MaterialTally>>;
  readonly contributors: number;
  readonly updatedAt: string;
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: { "content-type": "application/json", ...init?.headers },
  });
  if (!response.ok) {
    throw new ApiError(await describeFailure(response), response.status);
  }
  return (await response.json()) as T;
}

async function describeFailure(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { message?: string };
    if (body.message) return body.message;
  } catch {
    // Cuerpo no JSON: se cae al mensaje genérico.
  }
  return `La API respondió ${response.status}`;
}

export function registerCapture(record: CaptureRecord): Promise<RegisteredCapture> {
  return request<RegisteredCapture>("/captures", {
    method: "POST",
    body: JSON.stringify(record),
  });
}

export async function uploadImage(uploadUrl: string, image: Blob): Promise<void> {
  const response = await fetch(uploadUrl, {
    method: "PUT",
    headers: {
      "x-ms-blob-type": "BlockBlob",
      "content-type": image.type || "image/jpeg",
    },
    body: image,
  });
  if (!response.ok) {
    throw new ApiError(`El almacenamiento respondió ${response.status}`, response.status);
  }
}

export function commitCapture(captureId: string): Promise<{ status: ReviewStatus }> {
  return request<{ status: ReviewStatus }>(`/captures/${encodeURIComponent(captureId)}/commit`, {
    method: "POST",
  });
}

export function fetchStats(): Promise<StatsResponse> {
  return request<StatsResponse>("/stats");
}

/** Envía una captura completa: registro, imagen y confirmación. */
export async function sendCapture(record: CaptureRecord, image: Blob): Promise<void> {
  const registered = await registerCapture(record);
  await uploadImage(registered.uploadUrl, image);
  await commitCapture(registered.captureId);
}

// --- Moderación. Requiere sesión de administrador de Static Web Apps. ---

export interface ReviewQueueResponse {
  readonly items: readonly (StoredCapture & { readonly imageUrl: string })[];
  readonly continuationToken: string | null;
}

export function fetchReviewQueue(continuationToken?: string): Promise<ReviewQueueResponse> {
  const query = continuationToken ? `?cursor=${encodeURIComponent(continuationToken)}` : "";
  return request<ReviewQueueResponse>(`/review/pending${query}`);
}

export function submitReview(
  captureId: string,
  contributorId: string,
  decision: Exclude<ReviewStatus, "PENDING">,
  note?: string,
): Promise<{ status: ReviewStatus }> {
  return request<{ status: ReviewStatus }>(`/review/${encodeURIComponent(captureId)}`, {
    method: "POST",
    body: JSON.stringify({ contributorId, decision, note: note ?? null }),
  });
}
