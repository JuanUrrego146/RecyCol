package com.botabien.android.camera

import com.botabien.testing.StubImageFrame
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Calibración y verificación de las heurísticas de calidad (S11) sobre el
 * conjunto sintético de frames de prueba: el criterio de hecho exige detectar
 * desenfoque, baja luz y mal encuadre correctamente en este conjunto.
 */
class HeuristicFrameQualityAnalyzerTest {

    private val analyzer = HeuristicFrameQualityAnalyzer()

    // --- Nitidez -----------------------------------------------------------

    @Test
    fun `una textura nitida supera el umbral de desenfoque`() {
        val quality = analyzer.analyze(SyntheticFrames.frame(SyntheticFrames.checkerboard()))

        assertTrue(
            quality.sharpness > FrameQualityThresholds.BLURRY_BELOW,
            "Nitidez ${quality.sharpness} debería superar ${FrameQualityThresholds.BLURRY_BELOW}",
        )
    }

    @Test
    fun `la version desenfocada de la misma textura cae bajo el umbral`() {
        val borroso = SyntheticFrames.boxBlur(SyntheticFrames.checkerboard())

        val quality = analyzer.analyze(SyntheticFrames.frame(borroso))

        assertTrue(
            quality.sharpness < FrameQualityThresholds.BLURRY_BELOW,
            "Nitidez ${quality.sharpness} debería quedar bajo ${FrameQualityThresholds.BLURRY_BELOW}",
        )
    }

    @Test
    fun `el desenfoque reduce la nitidez tambien sobre ruido fino`() {
        val nitido = analyzer.analyze(SyntheticFrames.frame(SyntheticFrames.noise()))
        val borroso = analyzer.analyze(
            SyntheticFrames.frame(SyntheticFrames.boxBlur(SyntheticFrames.noise())),
        )

        assertTrue(nitido.sharpness > borroso.sharpness)
        assertTrue(borroso.sharpness < FrameQualityThresholds.BLURRY_BELOW)
    }

    // --- Luminancia --------------------------------------------------------

    @Test
    fun `un frame oscuro queda bajo el umbral de subexposicion`() {
        val quality = analyzer.analyze(SyntheticFrames.frame(SyntheticFrames.flat(18)))

        assertTrue(quality.luminance < FrameQualityThresholds.UNDEREXPOSED_BELOW)
    }

    @Test
    fun `un frame quemado supera el umbral de sobreexposicion`() {
        val quality = analyzer.analyze(SyntheticFrames.frame(SyntheticFrames.flat(248)))

        assertTrue(quality.luminance > FrameQualityThresholds.OVEREXPOSED_ABOVE)
    }

    @Test
    fun `una escena bien expuesta queda entre ambos umbrales`() {
        val quality = analyzer.analyze(SyntheticFrames.frame(SyntheticFrames.checkerboard()))

        assertTrue(quality.luminance > FrameQualityThresholds.UNDEREXPOSED_BELOW)
        assertTrue(quality.luminance < FrameQualityThresholds.OVEREXPOSED_ABOVE)
    }

    // --- Encuadre ----------------------------------------------------------

    @Test
    fun `un objeto centrado se reconoce dentro del area util`() {
        val luma = SyntheticFrames.texturedObject(centerXFraction = 0.5f, centerYFraction = 0.5f)

        val quality = analyzer.analyze(SyntheticFrames.frame(luma))

        assertTrue(quality.objectCentered)
    }

    @Test
    fun `un objeto en la esquina queda fuera del area util`() {
        val luma = SyntheticFrames.texturedObject(centerXFraction = 0.08f, centerYFraction = 0.08f)

        val quality = analyzer.analyze(SyntheticFrames.frame(luma))

        assertFalse(quality.objectCentered)
    }

    @Test
    fun `una escena plana sin objeto no cuenta como encuadrada`() {
        val quality = analyzer.analyze(SyntheticFrames.frame(SyntheticFrames.flat(128)))

        assertFalse(quality.objectCentered)
    }

    // --- Contrato ----------------------------------------------------------

    @Test
    fun `rechaza frames que no son LumaImageFrame`() {
        assertFailsWith<IllegalArgumentException> {
            analyzer.analyze(StubImageFrame())
        }
    }

    @Test
    fun `sin detector de suciedad el veredicto es siempre limpio`() {
        val quality = analyzer.analyze(SyntheticFrames.frame(SyntheticFrames.checkerboard()))

        assertFalse(quality.lensSoiling)
    }
}
