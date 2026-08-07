"""Mancha de lente sintética para la augmentación de S23 (coordinación #21, CAM→ML).

Porta a NumPy el generador calibrado del agente CAM (``SyntheticFrames.withSmudge``,
pruebas JVM de S12): una mancha elíptica difusa con **atenuación radial**, como
la que deja un dedo o grasa en el lente. A diferencia de una oclusión opaca,
atenúa la luz de la escena en lugar de sustituirla — el modelo ve la textura
real debilitada, que es lo que produce un lente sucio de verdad.

Modelo físico: para cada píxel a distancia normalizada ``d`` del centro
(``d < 1`` dentro de la elipse), el factor de atenuación es
``1 - strength * (1 - d²)`` — opaco en el centro si ``strength = 1``, intacto
en el borde. La app detecta estas manchas en producción con
``PersistentSmudgeDetector`` (S12), pero necesita ≥ 8 frames de movimiento
para acumular evidencia: **la ventana previa a la detección es exactamente
donde el clasificador ve frames con lente sucio**, y es lo que esta
transformación enseña a tolerar. Nota: una mancha local apenas mueve la
nitidez global, así que estos frames PASAN el filtro de
``frame_quality_gate`` — el autochequeo lo verifica; no hay que excluirlos
de la augmentación por calidad.

Rangos calibrados para augmentación (validados contra el detector de S12):

- ``radius_fraction`` 0.08–0.25 del lado menor: de huella de dedo a manchón
  de pulgar. Por encima de 0.25 domina el frame y deja de ser realista.
- ``strength`` 0.4–1.0: de grasa leve a mancha opaca. Por debajo de 0.4 el
  efecto es imperceptible incluso para el detector de la app.
- Centro dentro del 80 % central: la app excluye el anillo exterior de
  celdas en su detección (los bordes entran y salen del encuadre) y las
  manchas de borde aportan poco.

Uso::

    from quality.lens_smudge import apply_smudge, sample_smudge_params

    dirty = apply_smudge(rgb, center_x_fraction=0.4, center_y_fraction=0.35)
    params = sample_smudge_params(np.random.default_rng(seed))
    dirty = apply_smudge(rgb, **params)

Autochequeo: ``python quality/lens_smudge.py`` (sale 0 si pasa; 1 si falla).
"""

from __future__ import annotations

import sys

import numpy as np

RADIUS_FRACTION_RANGE = (0.08, 0.25)
STRENGTH_RANGE = (0.4, 1.0)
CENTER_MARGIN_FRACTION = 0.1


def apply_smudge(
    image: np.ndarray,
    center_x_fraction: float,
    center_y_fraction: float,
    radius_fraction: float = 0.12,
    strength: float = 0.75,
) -> np.ndarray:
    """Superpone la mancha sobre una copia de ``image`` (``HxW`` o ``HxWx3`` uint8).

    La atenuación es acromática (la suciedad debilita la luz antes del filtro
    de color del sensor), así que en RGB se aplica el mismo factor a los tres
    canales.
    """
    if image.dtype != np.uint8 or image.ndim not in (2, 3):
        raise ValueError("Se espera una imagen HxW o HxWx3 en uint8")
    if not 0.0 < radius_fraction <= 0.5:
        raise ValueError("radius_fraction fuera de rango razonable (0, 0.5]")
    if not 0.0 <= strength <= 1.0:
        raise ValueError("strength debe estar en [0, 1]")

    height, width = image.shape[:2]
    center_x = width * center_x_fraction
    center_y = height * center_y_fraction
    radius = min(width, height) * radius_fraction

    yy, xx = np.mgrid[0:height, 0:width]
    distance_sq = ((xx - center_x) / radius) ** 2 + ((yy - center_y) / radius) ** 2
    attenuation = np.ones((height, width), dtype=np.float64)
    inside = distance_sq < 1.0
    attenuation[inside] = 1.0 - strength * (1.0 - distance_sq[inside])

    if image.ndim == 3:
        attenuation = attenuation[:, :, np.newaxis]
    return np.clip(np.rint(image.astype(np.float64) * attenuation), 0.0, 255.0).astype(np.uint8)


def sample_smudge_params(rng: np.random.Generator) -> dict[str, float]:
    """Parámetros aleatorios dentro de los rangos calibrados, para el pipeline de S23."""
    margin = CENTER_MARGIN_FRACTION
    return {
        "center_x_fraction": float(rng.uniform(margin, 1.0 - margin)),
        "center_y_fraction": float(rng.uniform(margin, 1.0 - margin)),
        "radius_fraction": float(rng.uniform(*RADIUS_FRACTION_RANGE)),
        "strength": float(rng.uniform(*STRENGTH_RANGE)),
    }


def _self_check() -> list[str]:
    errors: list[str] = []
    height, width = 240, 320

    yy, xx = np.mgrid[0:height, 0:width]
    checker = np.where(((xx // 4) + (yy // 4)) % 2 == 0, 30, 220).astype(np.uint8)

    dirty = apply_smudge(
        checker, center_x_fraction=0.5, center_y_fraction=0.5,
        radius_fraction=0.2, strength=1.0,
    )

    cy, cx = height // 2, width // 2
    if dirty[cy, cx] > 3:
        errors.append(f"el centro de una mancha opaca debe quedar ~negro (vale {dirty[cy, cx]})")
    if not np.array_equal(dirty[0:20, 0:20], checker[0:20, 0:20]):
        errors.append("fuera del radio la imagen debe quedar intacta")
    if dirty.shape != checker.shape or dirty.dtype != np.uint8:
        errors.append("la salida debe conservar forma y dtype")

    rgb = np.stack([checker, checker, checker], axis=2)
    dirty_rgb = apply_smudge(rgb, 0.5, 0.5, radius_fraction=0.2, strength=1.0)
    if dirty_rgb.shape != rgb.shape:
        errors.append("en RGB la salida debe conservar HxWx3")
    if not np.array_equal(dirty_rgb[:, :, 0], dirty_rgb[:, :, 1]):
        errors.append("la atenuación debe ser acromática (idéntica por canal)")

    # Propiedad clave para S23: un frame con mancha PASA el filtro de calidad
    # (la mancha es local; la app lo clasificaría), así que hay que entrenar
    # con él, no descartarlo.
    from frame_quality_gate import FrameQualityGate

    ok, metrics = FrameQualityGate().accepts(dirty)
    if not ok:
        errors.append(f"un frame con mancha local debe pasar el gate ({metrics})")

    rng = np.random.default_rng(7)
    for _ in range(100):
        params = sample_smudge_params(rng)
        if not (RADIUS_FRACTION_RANGE[0] <= params["radius_fraction"] <= RADIUS_FRACTION_RANGE[1]):
            errors.append("radius_fraction muestreado fuera de rango")
            break
        if not (STRENGTH_RANGE[0] <= params["strength"] <= STRENGTH_RANGE[1]):
            errors.append("strength muestreado fuera de rango")
            break

    return errors


if __name__ == "__main__":
    failures = _self_check()
    for failure in failures:
        print(f"ERROR: {failure}", file=sys.stderr)
    if failures:
        sys.exit(1)
    print("lens_smudge: autoverificación OK")
    sys.exit(0)
