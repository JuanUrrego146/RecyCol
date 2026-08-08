package com.recycol.domain.usecase

import com.recycol.domain.model.ClassificationOutcome
import com.recycol.domain.model.FrameQuality

/**
 * Resultado de una pasada del caso de uso junto con la calidad que se midió para
 * producirlo.
 *
 * Existe para que el seguimiento temporal pueda evaluar el cambio de escena sin
 * volver a analizar el frame: la luminancia ya se calculó una vez en
 * `FrameQualityAnalyzer.analyze` y tirarla obligaría a medirla dos veces por
 * fotograma. [ClassifyWasteUseCase.execute] se conserva intacto para los
 * consumidores que solo quieren la decisión.
 */
data class FrameEvaluation(
    val outcome: ClassificationOutcome,
    val quality: FrameQuality,
)
