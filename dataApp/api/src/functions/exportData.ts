/**
 * Exportación hacia el pipeline de ML.
 *
 * Produce el manifiesto de lo **aprobado**, nunca de lo pendiente: la cuarentena
 * no sería cuarentena si el exportador la saltara.
 *
 * Las dos columnas que hay que mirar antes que la etiqueta son `contributor_id` y
 * `object_id`. §10, punto 2: «partir por aportante, no por imagen […] la unidad
 * de partición es el usuario, y después el objeto físico». Un `split` por imagen
 * sobre estos datos infla la métrica igual que se sospecha del train/val actual.
 * `split` viene ya resuelto desde el registro del aportante y **no debe
 * recalcularse aguas abajo**.
 *
 * Rutas:
 *   GET /api/export/manifest?format=jsonl|csv&cursor=…  → metadatos
 *   GET /api/export/sas?minutes=60                      → SAS de lectura para azcopy
 *
 * Ambas exigen rol de administrador.
 */

import { app, HttpRequest, HttpResponseInit } from "@azure/functions";
import { isAdministrator } from "../auth";
import { containerReadSasUrl } from "../blob";
import type { CaptureDocument } from "../model";
import { ensureTables, listApproved } from "../store";

const PAGE_SIZE = 2000;
const MAX_SAS_MINUTES = 240;

interface ManifestRow {
  relative_path: string;
  material: string;
  contamination: string | null;
  contributor_id: string;
  object_id: string;
  split: string;
  light: string;
  angle: string;
  physical_state: string | null;
  background: string | null;
  sharpness: number;
  luminance: number;
  quality_accepted: boolean;
  phash: string;
  width: number;
  height: number;
  device_platform: string;
  device_memory_gb: number | null;
  mode: string;
  requested_material: string | null;
  /** `true` si la persona corrigió lo que pedía la misión. §10: la corrección vale más que la confirmación. */
  corrected: boolean;
  label_latency_ms: number;
  /** `true` si la etiqueta llegó en menos de un segundo: señal de no haber mirado. */
  fast_label: boolean;
  consent_version: string;
  captured_at: string;
  reviewed_at: string | null;
}

const COLUMNS: readonly (keyof ManifestRow)[] = [
  "relative_path",
  "material",
  "contamination",
  "contributor_id",
  "object_id",
  "split",
  "light",
  "angle",
  "physical_state",
  "background",
  "sharpness",
  "luminance",
  "quality_accepted",
  "phash",
  "width",
  "height",
  "device_platform",
  "device_memory_gb",
  "mode",
  "requested_material",
  "corrected",
  "label_latency_ms",
  "fast_label",
  "consent_version",
  "captured_at",
  "reviewed_at",
];

function toRow(capture: CaptureDocument): ManifestRow {
  return {
    relative_path: capture.blobPath,
    material: capture.material,
    contamination: capture.contamination,
    contributor_id: capture.contributorId,
    object_id: capture.objectId,
    split: capture.split,
    light: capture.light,
    angle: capture.angle,
    physical_state: capture.physicalState,
    background: capture.background,
    sharpness: capture.quality.sharpness,
    luminance: capture.quality.luminance,
    quality_accepted: capture.quality.accepted,
    phash: capture.phash,
    width: capture.image.width,
    height: capture.image.height,
    device_platform: capture.device.platform,
    device_memory_gb: capture.device.memoryGb,
    mode: capture.mode,
    requested_material: capture.requestedMaterial,
    corrected:
      capture.requestedMaterial !== null && capture.requestedMaterial !== capture.material,
    label_latency_ms: capture.labelLatencyMs,
    fast_label: capture.labelLatencyMs < 1000,
    consent_version: capture.consentVersion,
    captured_at: capture.capturedAt,
    reviewed_at: capture.reviewedAt,
  };
}

export async function exportManifest(request: HttpRequest): Promise<HttpResponseInit> {
  if (!isAdministrator(request)) {
    return { status: 403, jsonBody: { message: "Solo administración" } };
  }

  const format = request.query.get("format") === "csv" ? "csv" : "jsonl";
  const cursor = request.query.get("cursor") ?? undefined;

  await ensureTables();
  const page = await listApproved(PAGE_SIZE, cursor);
  const rows = page.items.map(toRow);
  const body = format === "csv" ? toCsv(rows, cursor === undefined) : toJsonl(rows);

  return {
    status: 200,
    headers: {
      "content-type": format === "csv" ? "text/csv; charset=utf-8" : "application/x-ndjson",
      // La página siguiente viaja en una cabecera para no ensuciar un cuerpo que
      // se consume línea a línea desde el pipeline.
      "x-continuation-token": page.continuationToken ?? "",
      "x-row-count": String(rows.length),
    },
    body,
  };
}

function toJsonl(rows: readonly ManifestRow[]): string {
  return rows.map((row) => JSON.stringify(row)).join("\n") + (rows.length > 0 ? "\n" : "");
}

function toCsv(rows: readonly ManifestRow[], withHeader: boolean): string {
  const lines = withHeader ? [COLUMNS.join(",")] : [];
  for (const row of rows) {
    lines.push(COLUMNS.map((column) => csvCell(row[column])).join(","));
  }
  return lines.join("\n") + (lines.length > 0 ? "\n" : "");
}

function csvCell(value: string | number | boolean | null): string {
  if (value === null) return "";
  const text = String(value);
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

/**
 * SAS de lectura sobre el contenedor, para sincronizar las imágenes con
 * `azcopy`. De vida corta y solo lectura: no permite escribir ni borrar nada.
 */
export async function exportSas(request: HttpRequest): Promise<HttpResponseInit> {
  if (!isAdministrator(request)) {
    return { status: 403, jsonBody: { message: "Solo administración" } };
  }
  const requested = Number(request.query.get("minutes") ?? "60");
  const minutes = Number.isFinite(requested)
    ? Math.min(MAX_SAS_MINUTES, Math.max(5, Math.trunc(requested)))
    : 60;
  const url = containerReadSasUrl(minutes);
  return {
    status: 200,
    jsonBody: {
      url,
      minutes,
      hint: `azcopy copy "${url}" "./ml/data/recycol_aporta" --recursive`,
    },
  };
}

app.http("exportManifest", {
  methods: ["GET"],
  authLevel: "anonymous",
  route: "api/export/manifest",
  handler: exportManifest,
});

app.http("exportSas", {
  methods: ["GET"],
  authLevel: "anonymous",
  route: "api/export/sas",
  handler: exportSas,
});
