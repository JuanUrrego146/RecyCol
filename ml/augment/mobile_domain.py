"""Augmentación orientada al dominio móvil real (S23 · RNF-008).

Simula las condiciones adversas de una cámara de teléfono en mano: desenfoque
gaussiano y de movimiento, variación de brillo, contraste y temperatura de
color, ruido del sensor, artefactos de compresión JPEG, oclusión parcial y
perspectiva. Las intensidades están acotadas para degradar la captura sin
volver irreconocible el objeto (criterio de hecho de la issue #21).

Determinista por diseño: la secuencia de transformaciones de cada imagen se
deriva de su ruta y de una semilla global, nunca del reloj ni del orden de
ejecución.

Uso como biblioteca::

    from augment.mobile_domain import augment_image, rng_for
    augmented = augment_image(pil_image, rng_for("trashnet/glass/glass1.jpg"))

Hoja de contacto para revisión visual (S23 exige verificar a ojo)::

    python augment/mobile_domain.py --preview 12
"""

from __future__ import annotations

import argparse
import hashlib
import io
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

ML_DIR = Path(__file__).resolve().parent.parent
GLOBAL_SEED = "botabien-s23-v1"

# ---------------------------------------------------------------------------
# Transformaciones individuales
# ---------------------------------------------------------------------------


def gaussian_blur(image: Image.Image, rng: random.Random) -> Image.Image:
    return image.filter(ImageFilter.GaussianBlur(radius=rng.uniform(0.6, 2.2)))


def motion_blur(image: Image.Image, rng: random.Random) -> Image.Image:
    """Desenfoque de movimiento: media a lo largo de una línea con ángulo."""
    length = rng.randrange(5, 13, 2)
    angle = rng.uniform(0.0, 180.0)
    kernel = np.zeros((length, length), dtype=np.float32)
    kernel[length // 2, :] = 1.0
    rotated = Image.fromarray((kernel * 255).astype(np.uint8)).rotate(
        angle, resample=Image.BILINEAR, expand=False
    )
    kernel = np.asarray(rotated, dtype=np.float32)
    kernel /= max(kernel.sum(), 1.0)

    array = np.asarray(image, dtype=np.float32)
    pad = length // 2
    padded = np.pad(array, ((pad, pad), (pad, pad), (0, 0)), mode="edge")
    out = np.zeros_like(array)
    # Convolución directa: los kernels son pequeños (≤13) y las imágenes de
    # entrenamiento van reescaladas, así que esto es suficientemente rápido
    # sin más dependencias.
    for dy in range(length):
        for dx in range(length):
            weight = kernel[dy, dx]
            if weight == 0.0:
                continue
            out += weight * padded[dy : dy + array.shape[0], dx : dx + array.shape[1], :]
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


def brightness_contrast(image: Image.Image, rng: random.Random) -> Image.Image:
    image = ImageEnhance.Brightness(image).enhance(rng.uniform(0.55, 1.35))
    return ImageEnhance.Contrast(image).enhance(rng.uniform(0.65, 1.25))


def color_temperature(image: Image.Image, rng: random.Random) -> Image.Image:
    """Deriva cálida o fría típica del balance de blancos automático."""
    shift = rng.uniform(-0.18, 0.18)
    array = np.asarray(image, dtype=np.float32)
    array[:, :, 0] *= 1.0 + shift          # rojo
    array[:, :, 2] *= 1.0 - shift          # azul
    return Image.fromarray(np.clip(array, 0, 255).astype(np.uint8))


def sensor_noise(image: Image.Image, rng: random.Random) -> Image.Image:
    sigma = rng.uniform(3.0, 11.0)
    noise_rng = np.random.default_rng(rng.getrandbits(32))
    array = np.asarray(image, dtype=np.float32)
    array += noise_rng.normal(0.0, sigma, array.shape)
    return Image.fromarray(np.clip(array, 0, 255).astype(np.uint8))


def jpeg_artifacts(image: Image.Image, rng: random.Random) -> Image.Image:
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", quality=rng.randrange(30, 65))
    buffer.seek(0)
    return Image.open(buffer).convert("RGB")


def partial_occlusion(image: Image.Image, rng: random.Random) -> Image.Image:
    """Oclusión desde un borde (mano, otro objeto): tapa ≤ 20 % del área."""
    image = image.copy()
    width, height = image.size
    draw = ImageDraw.Draw(image, "RGBA")
    edge = rng.choice(("left", "right", "top", "bottom"))
    span = rng.uniform(0.10, 0.28)
    shade = rng.randrange(15, 70)
    color = (shade, shade, shade, rng.randrange(190, 255))
    if edge in ("left", "right"):
        w = int(width * span)
        x0 = 0 if edge == "left" else width - w
        draw.ellipse([x0 - w, -height * 0.3, x0 + w, height * 1.3], fill=color)
    else:
        h = int(height * span)
        y0 = 0 if edge == "top" else height - h
        draw.ellipse([-width * 0.3, y0 - h, width * 1.3, y0 + h], fill=color)
    return image.convert("RGB")


def perspective(image: Image.Image, rng: random.Random) -> Image.Image:
    """Inclinación leve de cámara en mano: desplaza las esquinas ≤ 8 %."""
    width, height = image.size
    jitter = lambda v, span: v + rng.uniform(-span, span)  # noqa: E731
    sx, sy = width * 0.08, height * 0.08
    corners = [
        (jitter(0, sx), jitter(0, sy)),
        (jitter(width, sx), jitter(0, sy)),
        (jitter(width, sx), jitter(height, sy)),
        (jitter(0, sx), jitter(height, sy)),
    ]
    coefficients = _perspective_coefficients(
        [(0, 0), (width, 0), (width, height), (0, height)], corners
    )
    return image.transform(
        (width, height), Image.PERSPECTIVE, coefficients,
        resample=Image.BILINEAR, fillcolor=(96, 96, 96),
    )


def _perspective_coefficients(target, source):
    matrix = []
    for (tx, ty), (sx, sy) in zip(target, source):
        matrix.append([sx, sy, 1, 0, 0, 0, -tx * sx, -tx * sy])
        matrix.append([0, 0, 0, sx, sy, 1, -ty * sx, -ty * sy])
    a = np.asarray(matrix, dtype=np.float64)
    b = np.asarray(target, dtype=np.float64).flatten()
    return np.linalg.solve(a, b)


# Cada transformación con su probabilidad de aplicarse a una imagen dada.
TRANSFORMS = [
    (gaussian_blur, 0.35),
    (motion_blur, 0.25),
    (brightness_contrast, 0.75),
    (color_temperature, 0.55),
    (sensor_noise, 0.45),
    (jpeg_artifacts, 0.55),
    (partial_occlusion, 0.20),
    (perspective, 0.45),
]


def rng_for(image_key: str) -> random.Random:
    """RNG reproducible por imagen: mismo path y semilla ⇒ misma augmentación."""
    digest = hashlib.md5(f"{image_key}|{GLOBAL_SEED}".encode("utf-8")).hexdigest()
    return random.Random(int(digest, 16))


def augment_image(image: Image.Image, rng: random.Random) -> Image.Image:
    """Aplica la cadena de transformaciones muestreada para esta imagen."""
    image = image.convert("RGB")
    applied = 0
    for transform, probability in TRANSFORMS:
        if rng.random() < probability:
            image = transform(image, rng)
            applied += 1
    if applied == 0:
        # Ninguna augmentación sorteada: al menos degradación JPEG leve, que
        # es lo mínimo que introduce cualquier cámara de móvil.
        image = jpeg_artifacts(image, rng)
    return image


# ---------------------------------------------------------------------------
# Hoja de contacto para revisión visual
# ---------------------------------------------------------------------------

def build_preview(sample_count: int) -> Path:
    import csv

    manifest = ML_DIR / "data" / "manifests" / "train.csv"
    if not manifest.is_file():
        raise SystemExit("Falta data/manifests/train.csv: ejecuta antes ingest/pipeline.py (S22).")
    with manifest.open(encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    # Muestra determinista y repartida entre materiales distintos.
    rows.sort(key=lambda r: hashlib.md5(f"{r['path']}|preview".encode()).hexdigest())
    seen: dict[str, int] = {}
    sample = []
    for row in rows:
        if seen.get(row["material"], 0) >= max(1, sample_count // 8):
            continue
        seen[row["material"]] = seen.get(row["material"], 0) + 1
        sample.append(row)
        if len(sample) >= sample_count:
            break

    tile = 256
    variants = 3
    sheet = Image.new("RGB", (tile * (variants + 1), tile * len(sample)), "white")
    for row_index, row in enumerate(sample):
        with Image.open(ML_DIR / "data" / row["path"]) as img:
            base = img.convert("RGB").resize((tile, tile), Image.LANCZOS)
        sheet.paste(base, (0, row_index * tile))
        for variant in range(variants):
            rng = rng_for(f"{row['path']}#preview{variant}")
            sheet.paste(augment_image(base, rng), (tile * (variant + 1), row_index * tile))

    out = ML_DIR / "data" / "derived" / "aug_preview" / "contact_sheet.jpg"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out, "JPEG", quality=90)
    print(f"Hoja de contacto ({len(sample)} filas × original+{variants}): {out.relative_to(ML_DIR)}")
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview", type=int, metavar="N",
                        help="genera una hoja de contacto con N imágenes del train")
    args = parser.parse_args()
    if args.preview:
        build_preview(args.preview)
        return 0
    parser.print_help()
    return 0


if __name__ == "__main__":
    sys.exit(main())
