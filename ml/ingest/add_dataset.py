"""Ingesta incremental de un dataset al pool existente (S22, operativa).

Añade un dataset de carpetas (p. ej. ``garbage_dataset_v2`` recién
desbloqueado) a los manifiestos vigentes sin re-escanear las demás fuentes,
cuyos originales pueden ya no estar en disco (se borran por espacio una vez
materializado ``pool512``). La deduplicación de las filas nuevas se hace
contra los dHash del pool materializado — una imagen y su versión ≤512 px
producen dHashes casi idénticos, de sobra dentro del umbral.

La referencia canónica sigue siendo ``pipeline.py`` desde cero con todos los
originales re-descargados; este script es el camino operativo para ampliar el
pool sin re-descargar gigabytes. Determinista: mismas entradas ⇒ mismo
resultado.

    python ingest/add_dataset.py --dataset garbage_dataset_v2
"""

from __future__ import annotations

import argparse
import csv
import sys
from collections import defaultdict
from pathlib import Path

import yaml
from PIL import Image
from tqdm import tqdm

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from ingest.pipeline import (  # noqa: E402
    DEDUP_MAX_DISTANCE, MANIFESTS, MAPPING_PATH, ML_DIR, RAW, SOURCES,
    VAL_PERCENT, Row, dhash, hamming, rows_from_folders, stable_bucket,
)
from ingest.resize_pool import resize_one  # noqa: E402


def load_manifest(split: str) -> list[Row]:
    with (MANIFESTS / f"{split}.csv").open(encoding="utf-8") as handle:
        return [Row(r["dataset"], r["material"], r["path"], split)
                for r in csv.DictReader(handle)]


def write_manifest(split: str, rows: list[Row]) -> None:
    with (MANIFESTS / f"{split}.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["dataset", "material", "path"])
        for row in sorted(rows, key=lambda r: (r.dataset, r.path)):
            writer.writerow([row.dataset, row.material, row.path])


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", required=True)
    args = parser.parse_args()

    mapping = yaml.safe_load(MAPPING_PATH.read_text(encoding="utf-8"))
    cfg = mapping["datasets"][args.dataset]
    if not cfg.get("enabled", True):
        print(f"{args.dataset} está deshabilitado por licencia.", file=sys.stderr)
        return 1
    source = SOURCES[args.dataset]
    root = RAW / source["dir"]
    if not root.exists():
        print(f"Falta {root}", file=sys.stderr)
        return 1

    existing = {split: load_manifest(split) for split in ("train", "val", "control")}
    if any(r.dataset == args.dataset for rows in existing.values() for r in rows):
        print(f"{args.dataset} ya está en los manifiestos; nada que hacer.")
        return 0

    pairs = rows_from_folders(args.dataset, root, cfg.get("labels") or {},
                              cfg.get("collapse_all_to"), cfg.get("discards") or {})
    print(f"{args.dataset}: {len(pairs)} imágenes candidatas")

    hashes_by_material: dict[str, list[int]] = defaultdict(list)
    control_hashes: list[int] = []
    for split, rows in existing.items():
        for row in tqdm(rows, desc=f"hash pool existente ({split})", unit="img"):
            with Image.open(ML_DIR / "data" / row.path) as img:
                value = dhash(img)
            if split == "control":
                control_hashes.append(value)
            else:
                hashes_by_material[row.material].append(value)

    added, dropped = [], 0
    for material, rel_path in tqdm(sorted(pairs, key=lambda p: p[1]),
                                   desc="dedup filas nuevas", unit="img"):
        with Image.open(ML_DIR / "data" / rel_path) as img:
            value = dhash(img)
        if any(hamming(value, seen) <= DEDUP_MAX_DISTANCE
               for seen in hashes_by_material[material]) or \
           any(hamming(value, seen) <= DEDUP_MAX_DISTANCE for seen in control_hashes):
            dropped += 1
            continue
        hashes_by_material[material].append(value)
        split = "val" if stable_bucket(f"{args.dataset}/{rel_path}") < VAL_PERCENT else "train"
        added.append(Row(args.dataset, material, rel_path, split))

    print(f"{len(added)} filas nuevas ({dropped} duplicados descartados)")
    import hashlib
    materialized = []
    for row in tqdm(added, desc="materializando pool512", unit="img"):
        digest = hashlib.md5(row.path.encode("utf-8")).hexdigest()
        rel = f"derived/pool512/{row.dataset}/{digest}.jpg"
        resize_one(ML_DIR / "data" / row.path, ML_DIR / "data" / rel)
        materialized.append(Row(row.dataset, row.material, rel, row.split))

    for split in ("train", "val"):
        rows = existing[split] + [r for r in materialized if r.split == split]
        write_manifest(split, rows)
        print(f"{split}: {len(rows)} filas")
    return 0


if __name__ == "__main__":
    sys.exit(main())
