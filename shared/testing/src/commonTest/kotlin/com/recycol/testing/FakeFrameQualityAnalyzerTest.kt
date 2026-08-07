package com.recycol.testing

import com.recycol.domain.model.FrameQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Documenta el contrato del fake de `FrameQualityAnalyzer`: por defecto un
 * frame bueno (flujo feliz); cualquier condición adversa se fija por constructor.
 */
class FakeFrameQualityAnalyzerTest {

    @Test
    fun porDefectoElFrameEsNitidoIluminadoYCentrado() {
        val analyzer = FakeFrameQualityAnalyzer()

        val quality = analyzer.analyze(StubImageFrame())

        assertTrue(quality.sharpness >= 0.9f)
        assertTrue(quality.luminance > 0.0f)
        assertFalse(quality.lensSoiling)
        assertTrue(quality.objectCentered)
    }

    @Test
    fun devuelveLaCalidadConfiguradaParaSimularCondicionesAdversas() {
        val blurry = FrameQuality(
            sharpness = 0.10f,
            luminance = 0.05f,
            lensSoiling = true,
            objectCentered = false,
        )
        val analyzer = FakeFrameQualityAnalyzer(quality = blurry)

        assertEquals(blurry, analyzer.analyze(StubImageFrame()))
    }
}
