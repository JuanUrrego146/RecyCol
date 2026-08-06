package com.botabien.domain.model

/**
 * Resultado de la segunda etapa del pipeline: inspección de contaminación.
 *
 * @property state estado de contaminación detectado.
 * @property confidence confianza de la inspección, en el rango `[0.0, 1.0]`.
 */
data class ContaminationResult(
    val state: ContaminationState,
    val confidence: Float,
)
