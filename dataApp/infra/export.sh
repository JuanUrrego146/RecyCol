#!/usr/bin/env bash
#
# Descarga el dataset completo —imágenes y etiquetas— para entrenar.
#
#   bash dataApp/infra/export.sh [carpeta-destino]
#
# Por defecto deja todo en `ml/data/recycol_aporta/`, junto al resto de fuentes
# del pipeline.
#
# Requisitos: Azure CLI con `az login` hecho. **Nada más.** No hace falta azcopy
# —que no está instalado en la máquina de Juan— ni tener sesión abierta en la
# aplicación web: esto habla directamente con el almacenamiento, del que ya eres
# dueño.
#
# Qué produce:
#
#   <destino>/
#   ├── images/MATERIAL/<aportante>/<captura>.jpg   las fotos, tal cual se subieron
#   ├── manifest.csv                                una fila por foto
#   ├── manifest.jsonl                              lo mismo, línea a línea
#   └── RESUMEN.txt                                 recuentos y avisos
#
# **Solo se exporta lo APROBADO.** Lo que está en cuarentena no sale: la revisión
# no sería revisión si el exportador la saltara (CONTEXTO.md §10, punto 5).

set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DESTINO="${1:-${RAIZ}/ml/data/recycol_aporta}"
# Absoluta desde ya: más abajo el volcado se ejecuta con otro directorio de
# trabajo, y una ruta relativa acabaría escribiendo el manifiesto en otro sitio.
mkdir -p "${DESTINO}"
DESTINO="$(cd "${DESTINO}" && pwd)"

RESOURCE_GROUP="${RESOURCE_GROUP:-rg-recycol-aporta}"
STORAGE_ACCOUNT="${STORAGE_ACCOUNT:-strecycolaporta94b924}"
CONTAINER_NAME="${CONTAINER_NAME:-captures}"
# Cambiar a PENDING o REJECTED solo para inspeccionar; nunca para entrenar.
ESTADO="${ESTADO:-APPROVED}"

command -v az >/dev/null 2>&1 || {
  echo "Falta el CLI de Azure. En Windows está en:"
  echo '  export PATH="/c/Program Files/Microsoft SDKs/Azure/CLI2/wbin:$PATH"'
  exit 1
}

echo "Cuenta   : ${STORAGE_ACCOUNT}"
echo "Destino  : ${DESTINO}"
echo "Estado   : ${ESTADO}"
echo

CONEXION="$(az storage account show-connection-string \
  --name "${STORAGE_ACCOUNT}" --resource-group "${RESOURCE_GROUP}" \
  --query connectionString -o tsv)"

mkdir -p "${DESTINO}/images"

echo "→ Descargando imágenes"
# `download-batch` del propio CLI: recursivo y sin dependencias externas.
az storage blob download-batch \
  --source "${CONTAINER_NAME}" \
  --destination "${DESTINO}/images" \
  --connection-string "${CONEXION}" \
  --no-progress \
  --output none

echo "→ Volcando etiquetas y metadatos"

# El volcado va por Node porque el registro completo vive como JSON dentro de la
# entidad —Table Storage no admite objetos anidados— y hay que reconstruirlo.
# Se reutiliza el SDK que ya trae la API; si falta, se instala.
API_DIR="${RAIZ}/dataApp/api"
if [[ ! -d "${API_DIR}/node_modules/@azure/data-tables" ]]; then
  echo "  (instalando dependencias de la API, solo la primera vez)"
  npm ci --prefix "${API_DIR}" --no-audit --no-fund >/dev/null
fi

# Se ejecuta CON EL DIRECTORIO DE TRABAJO en dataApp/api.
#
# No sirve `NODE_PATH`: solo lo honra el resolutor de CommonJS, y esto es un
# módulo ESM. Con NODE_PATH el script funcionaba desde dataApp/api y moría con
# ERR_MODULE_NOT_FOUND desde cualquier otro sitio — incluida la raíz del
# repositorio, que es desde donde se invoca. Por eso `DESTINO` se convirtió en
# ruta absoluta más arriba.
( cd "${API_DIR}" && CONEXION="${CONEXION}" DESTINO="${DESTINO}" ESTADO="${ESTADO}" \
  node --input-type=module -e "$(cat <<'JS'
import { TableClient } from "@azure/data-tables";
import { writeFileSync, existsSync } from "node:fs";
import { join } from "node:path";

const conexion = process.env.CONEXION;
const destino = process.env.DESTINO;
const estado = process.env.ESTADO;

const capturas = TableClient.fromConnectionString(conexion, "captures");
const aportantes = TableClient.fromConnectionString(conexion, "contributors");

// Los identificadores anónimos que resultaron ser la misma persona al entrar en
// su cuenta. Sin esto, alguien que aportó antes de identificarse contaría como
// dos aportantes distintos y la partición quedaría mal hecha.
const canonico = new Map();
for await (const entidad of aportantes.listEntities()) {
  const doc = JSON.parse(entidad.payload);
  for (const enlazado of doc.linkedContributorIds ?? []) canonico.set(enlazado, doc.id);
}

const COLUMNAS = [
  "relative_path", "material", "contamination",
  "contributor_id", "canonical_contributor_id", "object_id", "split",
  "light", "angle", "physical_state", "background",
  "sharpness", "luminance", "quality_accepted", "phash",
  "width", "height", "device_platform", "device_memory_gb",
  "mode", "requested_material", "corrected",
  "label_latency_ms", "fast_label",
  "consent_version", "captured_at", "reviewed_at",
];

const filas = [];
const sinImagen = [];
for await (const entidad of capturas.listEntities()) {
  const c = JSON.parse(entidad.payload);
  if (c.status !== estado || !c.imageUploaded) continue;

  if (!existsSync(join(destino, "images", ...c.blobPath.split("/")))) {
    sinImagen.push(c.blobPath);
  }

  filas.push({
    relative_path: c.blobPath,
    material: c.material,
    contamination: c.contamination,
    contributor_id: c.contributorId,
    canonical_contributor_id: canonico.get(c.contributorId) ?? c.contributorId,
    object_id: c.objectId,
    split: c.split,
    light: c.light,
    angle: c.angle,
    physical_state: c.physicalState,
    background: c.background,
    sharpness: c.quality.sharpness,
    luminance: c.quality.luminance,
    quality_accepted: c.quality.accepted,
    phash: c.phash,
    width: c.image.width,
    height: c.image.height,
    device_platform: c.device.platform,
    device_memory_gb: c.device.memoryGb,
    mode: c.mode,
    requested_material: c.requestedMaterial,
    corrected: c.requestedMaterial !== null && c.requestedMaterial !== c.material,
    label_latency_ms: c.labelLatencyMs,
    fast_label: c.labelLatencyMs < 1000,
    consent_version: c.consentVersion,
    captured_at: c.capturedAt,
    reviewed_at: c.reviewedAt,
  });
}

filas.sort((a, b) => a.relative_path.localeCompare(b.relative_path));

// Tercera copia del escapado de dataApp/api/src/csv.ts —este guion es
// autocontenido y no puede importar de allí—. La comilla simple delante de =, +,
// - y @ es lo que impide que una hoja de cálculo EJECUTE el contenido de una
// celda al abrir el manifiesto. Si cambia una copia, cambian las dos.
const celda = (v) => {
  if (v === null || v === undefined) return "";
  const bruto = String(v);
  const t = /^[=+\-@\t\r]/.test(bruto) ? "'" + bruto : bruto;
  return /[",\n]/.test(t) ? '"' + t.replace(/"/g, '""') + '"' : t;
};
writeFileSync(
  join(destino, "manifest.csv"),
  [COLUMNAS.join(","), ...filas.map((f) => COLUMNAS.map((c) => celda(f[c])).join(","))].join("\n") + "\n",
);
writeFileSync(
  join(destino, "manifest.jsonl"),
  filas.map((f) => JSON.stringify(f)).join("\n") + (filas.length ? "\n" : ""),
);

// Resumen: los recuentos que de verdad hay que mirar antes de entrenar.
const cuenta = (fn) => {
  const m = new Map();
  for (const f of filas) m.set(fn(f), (m.get(fn(f)) ?? 0) + 1);
  return [...m.entries()].sort((a, b) => b[1] - a[1]);
};
const personas = new Set(filas.map((f) => f.canonical_contributor_id));
const objetos = new Set(filas.map((f) => f.object_id));
const control = filas.filter((f) => f.split === "CONTROL");
const personasControl = new Set(control.map((f) => f.canonical_contributor_id));
const personasTrain = new Set(
  filas.filter((f) => f.split === "TRAIN").map((f) => f.canonical_contributor_id),
);
const solapadas = [...personasControl].filter((p) => personasTrain.has(p));

const lineas = [];
lineas.push(`Exportado: ${filas.length} fotos en estado ${estado}`);
lineas.push(`Personas distintas: ${personas.size} · objetos físicos distintos: ${objetos.size}`);
lineas.push("");
lineas.push("Por material:");
for (const [m, n] of cuenta((f) => f.material)) lineas.push(`  ${m.padEnd(18)} ${n}`);
lineas.push("");
lineas.push("Por partición:");
for (const [s, n] of cuenta((f) => f.split)) lineas.push(`  ${s.padEnd(18)} ${n}`);
lineas.push("");
lineas.push("Contaminación (solo fibra la lleva obligatoria):");
for (const [c, n] of cuenta((f) => f.contamination ?? "(sin declarar)")) {
  lineas.push(`  ${String(c).padEnd(18)} ${n}`);
}
lineas.push("");
const rapidas = filas.filter((f) => f.fast_label).length;
const corregidas = filas.filter((f) => f.corrected).length;
const rechazablesPorCalidad = filas.filter((f) => !f.quality_accepted).length;
lineas.push(`Etiquetadas en menos de 1 s: ${rapidas}  (confianza menor: puede que no miraran)`);
lineas.push(`Correcciones sobre lo que pedía la misión: ${corregidas}  (valen más que las confirmaciones)`);
lineas.push(
  `Fotos que el filtro de la app habría rechazado: ${rechazablesPorCalidad}  (SEGÚN EL CLIENTE)`,
);
lineas.push("");

if (solapadas.length > 0) {
  lineas.push("*** AVISO GRAVE ***");
  lineas.push(`${solapadas.length} persona(s) aparecen a la vez en TRAIN y en CONTROL.`);
  lineas.push("El control propio deja de medir generalización. No entrenes así:");
  lineas.push("pasa a TRAIN todo lo de esas personas antes de seguir.");
  lineas.push("");
}
if (sinImagen.length > 0) {
  lineas.push(`*** ${sinImagen.length} fila(s) sin imagen en disco ***`);
  for (const p of sinImagen.slice(0, 10)) lineas.push(`  ${p}`);
  if (sinImagen.length > 10) lineas.push(`  … y ${sinImagen.length - 10} más`);
  lineas.push("");
}

lineas.push("ANTES DE ENTRENAR, lo que no admite atajos:");
lineas.push("  1. Particiona por `canonical_contributor_id`, NUNCA por fila. Dentro de");
lineas.push("     una persona, `object_id` agrupa las tomas de la misma pieza.");
lineas.push("  2. Lo marcado CONTROL jamás entrena. Es control propio congelado.");
lineas.push("  3. RealWaste sigue intocable. Esto es otra fuente, con su entrada en");
lineas.push("     ml/DATA_LICENSES.md y ml/taxonomy/label_mapping.yaml antes de usarse.");
lineas.push("  4. Deduplica con pHash contra el pool y contra RealWaste. El `phash` de");
lineas.push("     este manifiesto lo calcula el navegador: sirve de prefiltro, no decide.");
lineas.push("  5. Entrena con y sin estos datos y compara contra el control de siempre.");
lineas.push("  6. sharpness, luminance y quality_accepted las DECLARA EL CLIENTE. Nadie");
lineas.push("     las ha verificado contra la imagen. Recalcúlalas sobre images/ con");
lineas.push("     ml/quality/frame_quality_gate.py antes de filtrar nada por calidad.");

const resumen = lineas.join("\n") + "\n";
writeFileSync(join(destino, "RESUMEN.txt"), resumen);
console.log(resumen);
JS
)" )

echo "✓ Listo en ${DESTINO}"
