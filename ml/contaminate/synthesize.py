"""Síntesis de contaminación por segmentación y composición (S24 · RF-021).

No existe dataset público de reciclables contaminados, así que se fabrica:

1. U²-Net segmenta el objeto limpio (máscara de saliencia).
2. Sobre la superficie del objeto se componen manchas de líquido, grasa y
   residuo alimenticio generadas proceduralmente (ruido fractal + paletas de
   café/aceite/salsa) con mezcla multiplicativa y brillo especular leve.
3. El resultado conserva el fondo intacto: la contaminación solo cae dentro
   de la máscara, como en el enfoque de EcoBin.

Determinista por imagen (misma ruta y semilla ⇒ misma mancha). Genera pares
limpio/contaminado para entrenar el clasificador binario de S26.

Uso (contenedor de ML; requiere ``data/models/u2net.onnx``)::

    python contaminate/synthesize.py --materials PLASTIC GLASS METAL BEVERAGE_CARTON CARDBOARD PAPER --per-class 800
    python contaminate/synthesize.py --preview 8
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ML_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ML_DIR))

U2NET_PATH = ML_DIR / "data" / "models" / "u2net.onnx"
OUT_DIR = ML_DIR / "data" / "derived" / "contamination"
GLOBAL_SEED = "botabien-s24-v1"

# Paletas RGB de contaminantes típicos (bordes oscuros → centros claros).
PALETTES = {
    "coffee": [(58, 34, 18), (92, 58, 28), (128, 86, 44)],
    "grease": [(120, 104, 40), (160, 140, 70), (190, 172, 110)],
    "sauce": [(110, 30, 16), (150, 48, 24), (180, 70, 36)],
    "organic": [(70, 62, 26), (98, 88, 40), (56, 70, 30)],
}


def rng_for(key: str) -> random.Random:
    digest = hashlib.md5(f"{key}|{GLOBAL_SEED}".encode("utf-8")).hexdigest()
    return random.Random(int(digest, 16))


# ---------------------------------------------------------------------------
# Segmentación con U²-Net
# ---------------------------------------------------------------------------

class Segmenter:
    def __init__(self) -> None:
        import onnxruntime as ort

        if not U2NET_PATH.is_file():
            raise SystemExit(
                f"Falta {U2NET_PATH.relative_to(ML_DIR)}: descárgalo según ml/DATASETS.md (S24)."
            )
        self.session = ort.InferenceSession(str(U2NET_PATH), providers=["CPUExecutionProvider"])
        self.input_name = self.session.get_inputs()[0].name

    def mask(self, image: Image.Image) -> np.ndarray:
        """Máscara de saliencia [0,1] al tamaño original de la imagen."""
        small = image.convert("RGB").resize((320, 320), Image.BILINEAR)
        array = np.asarray(small, dtype=np.float32) / 255.0
        array = (array - [0.485, 0.456, 0.406]) / [0.229, 0.224, 0.225]
        tensor = array.transpose(2, 0, 1)[np.newaxis].astype(np.float32)
        result = self.session.run(None, {self.input_name: tensor})[0][0, 0]
        result = (result - result.min()) / max(result.max() - result.min(), 1e-6)
        mask = Image.fromarray((result * 255).astype(np.uint8)).resize(image.size, Image.BILINEAR)
        return np.asarray(mask, dtype=np.float32) / 255.0


# ---------------------------------------------------------------------------
# Generación procedural de manchas
# ---------------------------------------------------------------------------

def fractal_noise(size: tuple[int, int], rng: random.Random, octaves: int = 4) -> np.ndarray:
    """Ruido fractal [0,1] sumando ruido blanco reescalado por octavas."""
    width, height = size
    seed_rng = np.random.default_rng(rng.getrandbits(32))
    field = np.zeros((height, width), dtype=np.float32)
    amplitude, total = 1.0, 0.0
    for octave in range(octaves):
        step = 2 ** (octaves - octave + 1)
        coarse = seed_rng.random((max(height // step, 2), max(width // step, 2))).astype(np.float32)
        layer = np.asarray(
            Image.fromarray((coarse * 255).astype(np.uint8)).resize((width, height), Image.BICUBIC),
            dtype=np.float32) / 255.0
        field += amplitude * layer
        total += amplitude
        amplitude *= 0.55
    return field / total


def stain_mask(size: tuple[int, int], object_mask: np.ndarray, rng: random.Random) -> np.ndarray:
    """Mancha orgánica dentro del objeto: blob elíptico modulado por ruido."""
    width, height = size
    ys, xs = np.nonzero(object_mask > 0.5)
    if len(xs) == 0:
        return np.zeros((height, width), dtype=np.float32)
    anchor = rng.randrange(len(xs))
    cx, cy = int(xs[anchor]), int(ys[anchor])
    base = Image.new("L", size, 0)
    draw = ImageDraw.Draw(base)
    spread_x = max(int(width * rng.uniform(0.10, 0.30)), 8)
    spread_y = max(int(height * rng.uniform(0.10, 0.30)), 8)
    draw.ellipse([cx - spread_x, cy - spread_y, cx + spread_x, cy + spread_y], fill=255)
    for _ in range(rng.randrange(2, 6)):     # salpicaduras satélite
        dx, dy = rng.randrange(-spread_x, spread_x + 1), rng.randrange(-spread_y, spread_y + 1)
        radius = rng.randrange(3, max(min(spread_x, spread_y) // 2, 4))
        draw.ellipse([cx + dx - radius, cy + dy - radius, cx + dx + radius, cy + dy + radius], fill=255)
    blob = np.asarray(base.filter(ImageFilter.GaussianBlur(radius=min(spread_x, spread_y) * 0.35)),
                      dtype=np.float32) / 255.0
    noise = fractal_noise(size, rng)
    stain = np.clip(blob * (0.45 + 0.9 * noise), 0.0, 1.0)
    stain[stain < 0.18] = 0.0                # borde irregular, no degradado suave
    return stain * object_mask


def apply_stain(image: Image.Image, stain: np.ndarray, palette: list[tuple[int, int, int]],
                rng: random.Random) -> Image.Image:
    array = np.asarray(image.convert("RGB"), dtype=np.float32)
    height, width = array.shape[:2]
    color_field = np.zeros_like(array)
    bands = np.clip((stain * (len(palette) - 1e-3)).astype(np.int32), 0, len(palette) - 1)
    for index, color in enumerate(palette):
        color_field[bands == index] = color
    alpha = np.clip(stain * rng.uniform(0.65, 0.9), 0.0, 1.0)[..., np.newaxis]
    # Mezcla multiplicativa: el líquido oscurece y tiñe sin borrar la textura.
    stained = array * (1.0 - alpha) + (array / 255.0) * color_field * alpha
    # Brillo especular leve en el núcleo de la mancha (líquido fresco).
    core = np.clip(stain - 0.75, 0.0, 1.0)[..., np.newaxis] * 90.0
    return Image.fromarray(np.clip(stained + core, 0, 255).astype(np.uint8))


def contaminate(image: Image.Image, mask: np.ndarray, key: str) -> Image.Image:
    rng = rng_for(key)
    result = image.convert("RGB")
    for _ in range(rng.randrange(1, 4)):     # 1–3 manchas por objeto
        palette = PALETTES[rng.choice(sorted(PALETTES))]
        stain = stain_mask(result.size, mask, rng)
        result = apply_stain(result, stain, palette, rng)
    return result


# ---------------------------------------------------------------------------
# Generación del conjunto
# ---------------------------------------------------------------------------

def load_train_rows(materials: set[str]) -> list[dict]:
    manifest = ML_DIR / "data" / "manifests" / "train.csv"
    if not manifest.is_file():
        raise SystemExit("Falta data/manifests/train.csv: ejecuta antes ingest/pipeline.py (S22).")
    with manifest.open(encoding="utf-8") as handle:
        rows = [r for r in csv.DictReader(handle) if r["material"] in materials]
    rows.sort(key=lambda r: hashlib.md5(f"{r['path']}|s24".encode()).hexdigest())
    return rows


def generate(materials: list[str], per_class: int) -> None:
    from tqdm import tqdm

    segmenter = Segmenter()
    rows = load_train_rows(set(materials))
    quota = {m: per_class for m in materials}
    manifest_rows = []
    for row in tqdm(rows, desc="s24: contaminando", unit="img"):
        if quota.get(row["material"], 0) <= 0:
            continue
        source = ML_DIR / "data" / row["path"]
        with Image.open(source) as img:
            image = img.convert("RGB")
        mask = segmenter.mask(image)
        if float((mask > 0.5).mean()) < 0.05:   # objeto no segmentable: se salta
            continue
        dirty = contaminate(image, mask, row["path"])
        out = OUT_DIR / "dirty" / row["material"] / f"{hashlib.md5(row['path'].encode()).hexdigest()}.jpg"
        out.parent.mkdir(parents=True, exist_ok=True)
        dirty.save(out, "JPEG", quality=92)
        manifest_rows.append({"material": row["material"], "clean": row["path"],
                              "dirty": str(out.relative_to(ML_DIR / "data"))})
        quota[row["material"]] -= 1
        if all(v <= 0 for v in quota.values()):
            break

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with (OUT_DIR / "pairs.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=["material", "clean", "dirty"])
        writer.writeheader()
        writer.writerows(manifest_rows)
    print(f"{len(manifest_rows)} pares limpio/contaminado en {OUT_DIR.relative_to(ML_DIR)}")
    pending = {m: q for m, q in quota.items() if q > 0}
    if pending:
        print(f"AVISO: cuota no alcanzada (faltan imágenes limpias): {pending}")


def preview(count: int) -> None:
    segmenter = Segmenter()
    rows = load_train_rows({"PLASTIC", "GLASS", "METAL", "BEVERAGE_CARTON", "CARDBOARD", "PAPER"})
    tile = 256
    sheet = Image.new("RGB", (tile * 3, tile * count), "white")
    made = 0
    for row in rows:
        if made >= count:
            break
        with Image.open(ML_DIR / "data" / row["path"]) as img:
            image = img.convert("RGB").resize((tile, tile), Image.LANCZOS)
        mask = segmenter.mask(image)
        if float((mask > 0.5).mean()) < 0.05:
            continue
        sheet.paste(image, (0, made * tile))
        mask_img = Image.fromarray((mask * 255).astype(np.uint8)).convert("RGB")
        sheet.paste(mask_img, (tile, made * tile))
        sheet.paste(contaminate(image, mask, f"{row['path']}#prev"), (tile * 2, made * tile))
        made += 1
    out = ML_DIR / "data" / "derived" / "contamination" / "preview.jpg"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out, "JPEG", quality=90)
    print(f"Vista previa (original | máscara | contaminado): {out.relative_to(ML_DIR)}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--materials", nargs="+", default=["PLASTIC", "GLASS", "METAL",
                                                          "BEVERAGE_CARTON", "CARDBOARD", "PAPER"])
    parser.add_argument("--per-class", type=int, default=800)
    parser.add_argument("--preview", type=int, metavar="N")
    args = parser.parse_args()
    if args.preview:
        preview(args.preview)
    else:
        generate(args.materials, args.per_class)
    return 0


if __name__ == "__main__":
    sys.exit(main())
