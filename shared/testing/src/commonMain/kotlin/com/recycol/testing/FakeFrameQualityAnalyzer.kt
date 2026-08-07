package com.recycol.testing

import com.recycol.domain.model.FrameQuality
import com.recycol.domain.model.ImageFrame
import com.recycol.domain.port.FrameQualityAnalyzer

/**
 * Fake determinista de `FrameQualityAnalyzer` (implementación real: agente CAM).
 *
 * Devuelve siempre la calidad fijada en el constructor; por defecto un frame
 * bueno (nítido, bien iluminado, lente limpio y objeto centrado), de modo que
 * el flujo feliz de los consumidores no se atasque en indicaciones de captura.
 */
class FakeFrameQualityAnalyzer(
    private val quality: FrameQuality = FrameQuality(
        sharpness = 0.90f,
        luminance = 0.60f,
        lensSoiling = false,
        objectCentered = true,
    ),
) : FrameQualityAnalyzer {

    override fun analyze(frame: ImageFrame): FrameQuality = quality
}
