"""Materializa el pool de entrenamiento redimensionado (S22, rendimiento).

Las fuentes originales mezclan resoluciones (Mendeley llega a varios MB por
foto) y el entrenamiento lee cada imagen en cada época. Este paso copia una
única vez todas las imágenes de los manifiestos a ``data/derived/pool512/``
con lado máximo 512 px (JPEG q90) y reescribe los manifiestos para apuntar
ahí. Reduce el I/O por época un orden de magnitud sin tocar los originales
(el dedup y las particiones ya están decididos: esto solo cambia la ruta).

Idempotente y determinista: nombre de destino = md5 de la ruta original.

    python ingest/resize_pool.py
"""

from __future__ import annotations

import csv
import hashlib
import sys
from pathlib import Path

from PIL import Image
from tqdm import tqdm

ML_DIR = Path(__file__).resolve().parent.parent
MANIFESTS = ML_DIR / "data" / "manifests"
POOL = ML_DIR / "data" / "derived" / "pool512"
MAX_SIDE = 512


def resize_one(source: Path, target: Path) -> None:
    if target.exists():
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    with Image.open(source) as img:
        image = img.convert("RGB")
        scale = MAX_SIDE / max(image.size)
        if scale < 1.0:
            image = image.resize((max(int(image.width * scale), 1),
                                  max(int(image.height * scale), 1)), Image.LANCZOS)
        image.save(target, "JPEG", quality=90)


def main() -> int:
    for split in ("train", "val", "control"):
        manifest = MANIFESTS / f"{split}.csv"
        if not manifest.is_file():
            print(f"Falta {manifest}: ejecuta antes ingest/pipeline.py", file=sys.stderr)
            return 1
        with manifest.open(encoding="utf-8") as handle:
            rows = list(csv.DictReader(handle))
        changed = 0
        for row in tqdm(rows, desc=f"pool512 {split}", unit="img"):
            if row["path"].startswith("derived/pool512/"):
                continue
            digest = hashlib.md5(row["path"].encode("utf-8")).hexdigest()
            rel = f"derived/pool512/{row['dataset']}/{digest}.jpg"
            resize_one(ML_DIR / "data" / row["path"], ML_DIR / "data" / rel)
            row["path"] = rel
            changed += 1
        with manifest.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(handle, fieldnames=["dataset", "material", "path"])
            writer.writeheader()
            writer.writerows(rows)
        print(f"{split}: {changed} imágenes materializadas en pool512")
    return 0


if __name__ == "__main__":
    sys.exit(main())
