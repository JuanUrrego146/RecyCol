"""Réplica en NumPy del filtro de calidad de frames de la app (coordinación #21, CAM→ML).

En la app real los frames pasan por ``HeuristicFrameQualityAnalyzer`` (S11,
``androidApp/camera/``) antes de llegar al clasificador: un frame más borroso,
más oscuro o más quemado que los umbrales calibrados nunca se clasifica — la
app pide otra toma (RF-015/RF-017). Este módulo reproduce esas métricas y
umbrales para que el pipeline de S23 pueda:

1. **Validar la augmentación**: cada muestra degradada debe seguir siendo un
   frame que la app aceptaría; degradar más allá del filtro entrena sobre una
   distribución que producción nunca ve.
2. **Caracterizar la degradación con el criterio de producción**: al medir la
   brecha de dominio (evaluación sobre el conjunto de control real), separar
   «el modelo falla en frames que la app aceptaría» (brecha real que S23 debe
   cerrar) de «el modelo falla en frames que el filtro habría rechazado»
   (fuera del dominio operativo).

Paridad con Kotlin (``FrameQualityThresholds.kt`` es la fuente de verdad; si
S39/QA recalibra, sincronizar a mano y anotar la versión aquí):

- Laplaciano de 4 vecinos sobre rejilla submuestreada (paso 2) del plano de
  luminancia; varianza normalizada saturando en ``SHARPNESS_SATURATION``.
- Luminancia media submuestreada en ``[0, 1]``.
- La app trabaja sobre el plano Y de YUV_420_888; para imágenes RGB del
  pipeline usar :func:`luma_from_rgb` (BT.601, la misma ponderación que
  produce ese plano Y).

Uso como módulo::

    from quality.frame_quality_gate import FrameQualityGate, luma_from_rgb

    gate = FrameQualityGate()
    ok, metrics = gate.accepts(luma_from_rgb(rgb_uint8))

Uso como autochequeo (mismos patrones sintéticos que las pruebas JVM de S11)::

    python quality/frame_quality_gate.py

Sale con código 0 si el autochequeo pasa; 1 si falla.
"""

from __future__ import annotations

import sys
from dataclasses import dataclass

import numpy as np

# Umbrales espejo de FrameQualityThresholds.kt (S11, main@cb8405b).
SHARPNESS_SATURATION = 900.0
BLURRY_BELOW = 0.18
UNDEREXPOSED_BELOW = 0.16
OVEREXPOSED_ABOVE = 0.92
SAMPLE_STEP = 2

# Ponderación BT.601: la misma con la que el sensor produce el plano Y que ve
# la app; usarla mantiene la paridad entre este filtro y el de producción.
_BT601 = np.array([0.299, 0.587, 0.114], dtype=np.float64)


def luma_from_rgb(rgb: np.ndarray) -> np.ndarray:
    """Convierte una imagen RGB ``HxWx3 uint8`` al plano de luminancia ``HxW uint8``."""
    if rgb.dtype != np.uint8 or rgb.ndim != 3 or rgb.shape[2] != 3:
        raise ValueError("Se espera una imagen HxWx3 en uint8")
    return np.clip(np.rint(rgb.astype(np.float64) @ _BT601), 0.0, 255.0).astype(np.uint8)


@dataclass
class FrameMetrics:
    """Métricas de un frame, con el mismo significado que ``FrameQuality`` en la app."""

    sharpness: float
    luminance: float

    @property
    def blurry(self) -> bool:
        return self.sharpness < BLURRY_BELOW

    @property
    def underexposed(self) -> bool:
        return self.luminance < UNDEREXPOSED_BELOW

    @property
    def overexposed(self) -> bool:
        return self.luminance > OVEREXPOSED_ABOVE


class FrameQualityGate:
    """Filtro de calidad equivalente al de la app Android (S11)."""

    def metrics(self, luma: np.ndarray) -> FrameMetrics:
        if luma.dtype != np.uint8 or luma.ndim != 2:
            raise ValueError("Se espera el plano de luminancia HxW en uint8")
        return FrameMetrics(
            sharpness=self._sharpness(luma),
            luminance=self._mean_luminance(luma),
        )

    def accepts(self, luma: np.ndarray) -> tuple[bool, FrameMetrics]:
        """``(True, métricas)`` si la app clasificaría este frame; ``(False, métricas)`` si pediría otra toma."""
        m = self.metrics(luma)
        return (not m.blurry and not m.underexposed and not m.overexposed), m

    @staticmethod
    def _sharpness(luma: np.ndarray) -> float:
        s = SAMPLE_STEP
        # Misma rejilla que Kotlin: centros en [s, dim-s) con paso s y vecinos
        # a distancia s.
        p = luma.astype(np.float64)
        center = p[s:-s:s, s:-s:s]
        left = p[s:-s:s, 0:-2 * s:s]
        right = p[s:-s:s, 2 * s::s]
        up = p[0:-2 * s:s, s:-s:s]
        down = p[2 * s::s, s:-s:s]
        h = min(center.shape[0], left.shape[0], right.shape[0], up.shape[0], down.shape[0])
        w = min(center.shape[1], left.shape[1], right.shape[1], up.shape[1], down.shape[1])
        response = (
            4.0 * center[:h, :w]
            - left[:h, :w] - right[:h, :w] - up[:h, :w] - down[:h, :w]
        )
        if response.size == 0:
            return 0.0
        variance = float(response.var())
        return float(min(1.0, max(0.0, variance / SHARPNESS_SATURATION)))

    @staticmethod
    def _mean_luminance(luma: np.ndarray) -> float:
        s = SAMPLE_STEP
        return float(luma[::s, ::s].astype(np.float64).mean() / 255.0)


def _self_check() -> list[str]:
    """Autochequeo con los patrones sintéticos de las pruebas JVM (``SyntheticFrames.kt``)."""
    errors: list[str] = []
    height, width = 240, 320
    yy, xx = np.mgrid[0:height, 0:width]
    checker = np.where(((xx // 4) + (yy // 4)) % 2 == 0, 30, 220).astype(np.uint8)

    kernel = np.ones(5) / 5.0

    def box_blur(img: np.ndarray, passes: int = 3) -> np.ndarray:
        out = img.astype(np.float64)
        for _ in range(passes):
            out = np.apply_along_axis(lambda r: np.convolve(r, kernel, mode="same"), 1, out)
            out = np.apply_along_axis(lambda c: np.convolve(c, kernel, mode="same"), 0, out)
        return out.astype(np.uint8)

    gate = FrameQualityGate()
    cases = [
        ("nítido aceptado", checker, True, None),
        ("borroso rechazado", box_blur(checker), False, "blurry"),
        ("oscuro rechazado", np.full((height, width), 18, np.uint8), False, "underexposed"),
        ("quemado rechazado", np.full((height, width), 248, np.uint8), False, "overexposed"),
    ]
    for name, luma, expected_ok, expected_flag in cases:
        ok, metrics = gate.accepts(luma)
        if ok != expected_ok:
            errors.append(f"{name}: accepts={ok}, métricas={metrics}")
        if expected_flag is not None and not getattr(metrics, expected_flag):
            errors.append(f"{name}: no marca {expected_flag} ({metrics})")

    gray_rgb = np.full((height, width, 3), 128, np.uint8)
    if not np.array_equal(luma_from_rgb(gray_rgb), np.full((height, width), 128, np.uint8)):
        errors.append("luma_from_rgb: un gris neutro debe mapear al mismo nivel de luma")

    return errors


if __name__ == "__main__":
    failures = _self_check()
    for failure in failures:
        print(f"ERROR: {failure}", file=sys.stderr)
    if failures:
        sys.exit(1)
    print("frame_quality_gate: autoverificación OK")
    sys.exit(0)
