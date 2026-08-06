package com.botabien.domain.model

/**
 * Métricas de calidad de un frame, calculadas con heurísticas deterministas
 * (varianza del Laplaciano, luminancia media, diferencia entre frames).
 *
 * @property sharpness nitidez normalizada en `[0.0, 1.0]`; valores bajos indican desenfoque.
 * @property luminance luminancia media normalizada en `[0.0, 1.0]`.
 * @property lensSoiling `true` si se detecta suciedad persistente del lente.
 * @property objectCentered `true` si el objeto está razonablemente encuadrado.
 */
data class FrameQuality(
    val sharpness: Float,
    val luminance: Float,
    val lensSoiling: Boolean,
    val objectCentered: Boolean,
)
