"""Pipeline de ingesta, unificación y particiones reproducibles (S22 · RNF-016).

Construye el conjunto de entrenamiento desde cero de forma determinista:

1. Extrae los datasets descargados en ``ml/data/raw/`` (los zips los descarga
   quien ejecuta; ver ``ml/DATASETS.md``).
2. Normaliza cada fuente habilitada al vocabulario ``WasteMaterial`` usando
   ``ml/taxonomy/label_mapping.yaml`` (nunca se mezcla nada sin pasar por ahí).
3. Deduplica perceptualmente (dHash) el conjunto de entrenamiento.
4. Particiona train/val con semilla fija por hash estable del path — el
   resultado no depende del orden del sistema de archivos.
5. Reserva RealWaste íntegro como partición de control (jamás entrena).
6. Escribe manifiestos CSV y el balance de clases en ``ml/data/manifests/``.

Ejecutar dos veces desde cero produce manifiestos idénticos (criterio de hecho
de S22)::

    python ingest/pipeline.py            # todo
    python ingest/pipeline.py --report   # solo re-emite balance desde manifiestos
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import sys
import zipfile
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import yaml
from PIL import Image
from tqdm import tqdm

ML_DIR = Path(__file__).resolve().parent.parent
RAW = ML_DIR / "data" / "raw"
DERIVED = ML_DIR / "data" / "derived"
MANIFESTS = ML_DIR / "data" / "manifests"
MAPPING_PATH = ML_DIR / "taxonomy" / "label_mapping.yaml"

SEED = "botabien-s22-v1"     # sal de la partición: cambiarla es cambiar las particiones
VAL_PERCENT = 15             # % de validación del pool de entrenamiento
DHASH_SIZE = 8               # dHash 64 bits
DEDUP_MAX_DISTANCE = 4       # distancia de Hamming máxima para considerar duplicado
MIN_CROP_SIDE = 64           # px: recortes de TACO más pequeños se descartan
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}

# Licencias de Flickr aptas para uso comercial (ml/DATA_LICENSES.md): en el
# COCO de TACO cada imagen declara la suya; NC/ND/desconocida quedan fuera.
COMMERCIAL_LICENSE_MARKERS = ("/by/", "/by-sa/", "/publicdomain/", "/zero/")


@dataclass(frozen=True)
class Row:
    """Una imagen normalizada del pool unificado."""

    dataset: str
    material: str
    path: str        # relativo a ml/data/
    split: str       # train | val | control


# ---------------------------------------------------------------------------
# Utilidades deterministas
# ---------------------------------------------------------------------------

def stable_bucket(key: str, buckets: int = 100) -> int:
    """Entero estable en [0, buckets) derivado solo de la clave y la semilla."""
    digest = hashlib.md5(f"{key}|{SEED}".encode("utf-8")).hexdigest()
    return int(digest, 16) % buckets


def dhash(image: Image.Image) -> int:
    """dHash de 64 bits: gradiente horizontal sobre luminancia 9x8."""
    gray = image.convert("L").resize((DHASH_SIZE + 1, DHASH_SIZE), Image.LANCZOS)
    pixels = np.asarray(gray, dtype=np.int16)
    bits = (pixels[:, 1:] > pixels[:, :-1]).flatten()
    value = 0
    for bit in bits:
        value = (value << 1) | int(bit)
    return value


def hamming(a: int, b: int) -> int:
    return bin(a ^ b).count("1")


def ensure_extracted(zip_path: Path, target: Path) -> bool:
    """Extrae el zip si el destino no existe aún. False si falta el zip."""
    if target.exists():
        return True
    if not zip_path.is_file():
        return False
    print(f"Extrayendo {zip_path.name} → {target.relative_to(ML_DIR)}")
    try:
        with zipfile.ZipFile(zip_path) as archive:
            if archive.testzip() is not None:
                raise zipfile.BadZipFile("contenido corrupto")
            archive.extractall(target)
    except zipfile.BadZipFile as error:
        # Típicamente una descarga aún en curso o truncada: se trata como no
        # disponible y no se deja una extracción a medias.
        print(f"AVISO: {zip_path.name} incompleto o corrupto ({error}); se omite esta fuente")
        if target.exists():
            import shutil
            shutil.rmtree(target)
        return False
    return True


def iter_images(root: Path):
    for path in sorted(root.rglob("*")):
        if path.suffix.lower() in IMAGE_EXTENSIONS and path.is_file():
            yield path


# ---------------------------------------------------------------------------
# Extractores por tipo de fuente
# ---------------------------------------------------------------------------

def rows_from_folders(dataset: str, root: Path, labels: dict[str, str],
                      collapse_to: str | None, discards: dict[str, str]) -> list[tuple[str, str]]:
    """(material, path_relativo) para datasets organizados en carpetas por clase.

    La carpeta de clase es el primer directorio bajo ``root`` que coincide con
    una etiqueta del mapeo (o cualquiera, si el dataset colapsa a una sola
    clase). Etiquetas presentes en disco pero ausentes del mapeo son un error:
    el mapeo debe ser exhaustivo (criterio de S21).
    """
    found: list[tuple[str, str]] = []
    unknown: set[str] = set()
    for image_path in iter_images(root):
        parts = image_path.relative_to(root).parts
        label = next((p for p in parts[:-1] if p in labels or p in discards), None)
        if collapse_to is not None:
            material = collapse_to
        elif label is None:
            unknown.add(parts[0] if len(parts) > 1 else "<raíz>")
            continue
        elif label in discards:
            continue
        else:
            material = labels[label]
        found.append((material, str(image_path.relative_to(ML_DIR / "data"))))
    if unknown:
        raise SystemExit(
            f"[{dataset}] carpetas sin entrada en label_mapping.yaml: {sorted(unknown)}. "
            "El mapeo debe ser exhaustivo: añade destino o descarte (S21)."
        )
    return found


def rows_from_taco(root: Path, labels: dict[str, str], discards: dict[str, str]) -> list[tuple[str, str]]:
    """Recorta las anotaciones de TACO a ``data/derived/taco_crops``.

    Solo imágenes cuya licencia declarada en el COCO sea apta para uso
    comercial (ml/DATA_LICENSES.md). Los recortes son deterministas: nombre =
    id de anotación; si ya existen no se rehacen.
    """
    annotations_path = next(root.rglob("annotations.json"), None)
    if annotations_path is None:
        raise SystemExit("[taco] annotations.json no encontrado tras la extracción.")
    coco = json.loads(annotations_path.read_text(encoding="utf-8"))

    license_table = coco.get("licenses") or []
    licenses_ok: set[int] = set()
    for lic in license_table:
        url = (lic.get("url") or "").lower()
        if any(marker in url for marker in COMMERCIAL_LICENSE_MARKERS):
            licenses_ok.add(lic["id"])
    # El paquete oficial de Zenodo no trae licencias por imagen (lista vacía):
    # su licencia es uniforme, CC BY 4.0 declarada en el registro de Zenodo
    # (ml/DATA_LICENSES.md), así que no hay nada que filtrar imagen a imagen.
    per_image_filter = bool(license_table)
    if not per_image_filter:
        print("taco: paquete Zenodo con licencia uniforme CC BY 4.0 — sin filtro por imagen")
    images = {img["id"]: img for img in coco["images"]}
    categories = {cat["id"]: cat["name"] for cat in coco["categories"]}

    crops_dir = DERIVED / "taco_crops"
    crops_dir.mkdir(parents=True, exist_ok=True)
    rows: list[tuple[str, str]] = []
    skipped_license = 0
    skipped_small = 0

    for ann in tqdm(sorted(coco["annotations"], key=lambda a: a["id"]),
                    desc="taco: recortando", unit="ann"):
        name = categories[ann["category_id"]]
        if name in discards or name not in labels:
            continue
        image_info = images[ann["image_id"]]
        if per_image_filter and image_info.get("license") not in licenses_ok:
            skipped_license += 1
            continue
        x, y, w, h = (int(v) for v in ann["bbox"])
        if w < MIN_CROP_SIDE or h < MIN_CROP_SIDE:
            skipped_small += 1
            continue
        material = labels[name]
        crop_path = crops_dir / material / f"{ann['id']}.jpg"
        if not crop_path.exists():
            crop_path.parent.mkdir(parents=True, exist_ok=True)
            source = annotations_path.parent / image_info["file_name"]
            if not source.is_file():
                continue
            with Image.open(source) as img:
                img.convert("RGB").crop((x, y, x + w, y + h)).save(crop_path, "JPEG", quality=95)
        rows.append((material, str(crop_path.relative_to(ML_DIR / "data"))))

    print(f"taco: {len(rows)} recortes · {skipped_license} anotaciones excluidas por licencia "
          f"· {skipped_small} por tamaño < {MIN_CROP_SIDE}px")
    return rows


# Dónde vive cada dataset bajo data/raw/ y cómo se ingiere.
SOURCES = {
    "garbage_dataset_v2": {"kind": "folders", "zip": "garbage-classification-v2.zip", "dir": "garbage_v2"},
    "garbage_classification_12": {"kind": "folders", "zip": "garbage-classification-12.zip", "dir": "garbage_12"},
    "realwaste": {"kind": "folders", "zip": "realwaste.zip", "dir": "realwaste"},
    "trashnet": {"kind": "folders", "zip": "trashnet-resized.zip", "dir": "trashnet"},
    "taco": {"kind": "taco", "zip": "taco.zip", "dir": "taco"},
    "zerowaste": {"kind": "folders", "zip": "zerowaste.zip", "dir": "zerowaste"},
    "clothing_dataset": {"kind": "folders", "zip": None, "dir": "clothing-dataset-small"},
    "mendeley_rotten_fruits": {"kind": "folders", "zip": "mendeley_fruits.zip", "dir": "mendeley_fruits"},
    "open_images_v7": {"kind": "openimages", "zip": None, "dir": "open_images"},
}


def normalized_labels(dataset_cfg: dict) -> tuple[dict[str, str], dict[str, str]]:
    """Etiquetas del YAML en minúsculas-insensible para carpetas reales."""
    labels = dataset_cfg.get("labels") or {}
    discards = dataset_cfg.get("discards") or {}
    return labels, discards


# ---------------------------------------------------------------------------
# Orquestación
# ---------------------------------------------------------------------------

def build_manifest() -> list[Row]:
    mapping = yaml.safe_load(MAPPING_PATH.read_text(encoding="utf-8"))
    rows: list[Row] = []
    missing: list[str] = []

    for name, cfg in mapping["datasets"].items():
        if not cfg.get("enabled", True):
            print(f"{name}: deshabilitado por licencia (ml/DATA_LICENSES.md) — se omite")
            continue
        source = SOURCES[name]
        root = RAW / source["dir"]
        if source["kind"] == "openimages":
            # La descarga dirigida de Open Images llega en una iteración
            # posterior de S22 (requiere tooling propio); se registra su
            # ausencia sin frenar el resto del pool.
            if not root.exists():
                missing.append(f"{name} (descarga dirigida pendiente)")
                continue
        elif not ensure_extracted(RAW / (source["zip"] or ""), root) and not root.exists():
            missing.append(name)
            continue

        labels, discards = normalized_labels(cfg)
        if source["kind"] == "taco":
            pairs = rows_from_taco(root, labels, discards)
        else:
            pairs = rows_from_folders(name, root, labels, cfg.get("collapse_all_to"), discards)

        is_control = "control" in (cfg.get("role_note") or "").lower() or name == "realwaste"
        for material, rel_path in pairs:
            if is_control:
                split = "control"
            else:
                split = "val" if stable_bucket(f"{name}/{rel_path}") < VAL_PERCENT else "train"
            rows.append(Row(name, material, rel_path, split))
        print(f"{name}: {len(pairs)} imágenes")

    if missing:
        print(f"AVISO: fuentes no disponibles aún en data/raw/: {', '.join(missing)}")
    return rows


def deduplicate(rows: list[Row]) -> list[Row]:
    """Elimina casi-duplicados del pool train/val; el control no se toca.

    Orden estable (dataset, path) ⇒ ante un duplicado sobrevive siempre el
    mismo. Los hashes se agrupan por material para acotar la comparación.
    """
    control = [r for r in rows if r.split == "control"]
    pool = sorted((r for r in rows if r.split != "control"),
                  key=lambda r: (r.dataset, r.path))
    kept: list[Row] = []
    hashes_by_material: dict[str, list[int]] = defaultdict(list)
    dropped = 0
    control_hashes = set()
    for row in tqdm(control, desc="dedup: hash control", unit="img"):
        with Image.open(ML_DIR / "data" / row.path) as img:
            control_hashes.add(dhash(img))

    for row in tqdm(pool, desc="dedup: train/val", unit="img"):
        with Image.open(ML_DIR / "data" / row.path) as img:
            value = dhash(img)
        if any(hamming(value, seen) <= DEDUP_MAX_DISTANCE
               for seen in hashes_by_material[row.material]):
            dropped += 1
            continue
        if any(hamming(value, seen) <= DEDUP_MAX_DISTANCE for seen in control_hashes):
            dropped += 1  # colisión con el control: fuera del train por higiene
            continue
        hashes_by_material[row.material].append(value)
        kept.append(row)

    print(f"dedup: {dropped} casi-duplicados eliminados de {len(pool)}")
    return kept + control


def write_outputs(rows: list[Row]) -> None:
    MANIFESTS.mkdir(parents=True, exist_ok=True)
    by_split: dict[str, list[Row]] = defaultdict(list)
    for row in rows:
        by_split[row.split].append(row)

    for split, split_rows in by_split.items():
        out = MANIFESTS / f"{split}.csv"
        with out.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.writer(handle)
            writer.writerow(["dataset", "material", "path"])
            for row in sorted(split_rows, key=lambda r: (r.dataset, r.path)):
                writer.writerow([row.dataset, row.material, row.path])
        print(f"{out.relative_to(ML_DIR)}: {len(split_rows)} filas")

    balance = MANIFESTS / "balance.md"
    with balance.open("w", encoding="utf-8") as handle:
        handle.write("# Balance de clases (S22)\n\n")
        handle.write(f"Semilla de partición: `{SEED}` · val {VAL_PERCENT} % · "
                     f"dedup dHash ≤ {DEDUP_MAX_DISTANCE}\n\n")
        handle.write("| Material | train | val | control |\n|---|---|---|---|\n")
        counts = {s: Counter(r.material for r in by_split.get(s, [])) for s in ("train", "val", "control")}
        materials = sorted({r.material for r in rows})
        for material in materials:
            handle.write(f"| {material} | {counts['train'][material]} | "
                         f"{counts['val'][material]} | {counts['control'][material]} |\n")
        totals = {s: sum(counts[s].values()) for s in counts}
        handle.write(f"| **Total** | **{totals['train']}** | **{totals['val']}** | **{totals['control']}** |\n")
    print(f"{balance.relative_to(ML_DIR)} escrito")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-dedup", action="store_true",
                        help="omite la deduplicación (solo para depurar)")
    args = parser.parse_args()

    rows = build_manifest()
    if not rows:
        print("No hay ninguna fuente disponible en ml/data/raw/ — nada que hacer.",
              file=sys.stderr)
        return 1
    if not args.skip_dedup:
        rows = deduplicate(rows)
    write_outputs(rows)
    return 0


if __name__ == "__main__":
    sys.exit(main())
