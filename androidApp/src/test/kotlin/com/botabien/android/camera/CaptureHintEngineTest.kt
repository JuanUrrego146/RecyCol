package com.botabien.android.camera

import com.botabien.domain.model.FrameQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Criterio de hecho de S13: como máximo una indicación cada intervalo mínimo,
 * nunca dos simultáneas, y ninguna cuando todas las métricas son aceptables.
 */
class CaptureHintEngineTest {

    private val interval = CaptureHintEngine.DEFAULT_MIN_INTERVAL_MILLIS

    private fun good() = FrameQuality(
        sharpness = 0.8f,
        luminance = 0.5f,
        lensSoiling = false,
        objectCentered = true,
    )

    private fun blurry() = good().copy(sharpness = 0.05f)
    private fun dark() = good().copy(luminance = 0.05f)
    private fun soiled() = good().copy(lensSoiling = true)
    private fun offCenter() = good().copy(objectCentered = false)

    @Test
    fun `sin degradaciones no hay indicacion`() {
        val engine = CaptureHintEngine()

        assertNull(engine.evaluate(good(), classificationConfidence = null, nowMillis = 0))
    }

    @Test
    fun `un frame borroso pide sostener firme`() {
        val engine = CaptureHintEngine()

        val hint = engine.evaluate(blurry(), classificationConfidence = null, nowMillis = 0)

        assertEquals(CaptureHintType.HOLD_STEADY, hint)
    }

    @Test
    fun `la indicacion vigente se mantiene sin contar como nueva`() {
        val engine = CaptureHintEngine()

        engine.evaluate(dark(), classificationConfidence = null, nowMillis = 0)
        val sustained = engine.evaluate(dark(), classificationConfidence = null, nowMillis = 100)

        assertEquals(CaptureHintType.MORE_LIGHT, sustained)
    }

    @Test
    fun `la indicacion se retira en cuanto la metrica se corrige`() {
        val engine = CaptureHintEngine()

        engine.evaluate(dark(), classificationConfidence = null, nowMillis = 0)
        val afterFix = engine.evaluate(good(), classificationConfidence = null, nowMillis = 100)

        assertNull(afterFix)
    }

    @Test
    fun `una causa nueva dentro del intervalo espera su turno`() {
        val engine = CaptureHintEngine()

        engine.evaluate(dark(), classificationConfidence = null, nowMillis = 0)
        engine.evaluate(good(), classificationConfidence = null, nowMillis = 200)
        val tooSoon = engine.evaluate(blurry(), classificationConfidence = null, nowMillis = 400)
        val afterInterval =
            engine.evaluate(blurry(), classificationConfidence = null, nowMillis = interval + 1)

        assertNull(tooSoon, "Una indicación nueva antes del intervalo mínimo viola RF-018")
        assertEquals(CaptureHintType.HOLD_STEADY, afterInterval)
    }

    @Test
    fun `ante varias degradaciones solo se muestra la causa dominante`() {
        val engine = CaptureHintEngine()
        val degraded = FrameQuality(
            sharpness = 0.05f,
            luminance = 0.05f,
            lensSoiling = true,
            objectCentered = false,
        )

        val hint = engine.evaluate(degraded, classificationConfidence = null, nowMillis = 0)

        assertEquals(CaptureHintType.CLEAN_LENS, hint, "El lente sucio es la causa raíz dominante")
    }

    @Test
    fun `escalar a una causa mas prioritaria tambien respeta el intervalo`() {
        val engine = CaptureHintEngine()

        engine.evaluate(offCenter(), classificationConfidence = null, nowMillis = 0)
        val stillOld = engine.evaluate(
            offCenter().copy(lensSoiling = true),
            classificationConfidence = null,
            nowMillis = 500,
        )
        val escalated = engine.evaluate(
            offCenter().copy(lensSoiling = true),
            classificationConfidence = null,
            nowMillis = interval + 500,
        )

        assertEquals(CaptureHintType.CENTER_OBJECT, stillOld, "Dentro del intervalo se mantiene la vigente")
        assertEquals(CaptureHintType.CLEAN_LENS, escalated)
    }

    @Test
    fun `con confianza suficiente no se molesta al usuario`() {
        val engine = CaptureHintEngine()

        val hint = engine.evaluate(
            blurry(),
            classificationConfidence = CaptureHintEngine.DEFAULT_SUFFICIENT_CONFIDENCE,
            nowMillis = 0,
        )

        assertNull(hint, "Con el clasificador seguro, las indicaciones se suprimen")
    }

    @Test
    fun `con confianza baja las indicaciones siguen activas`() {
        val engine = CaptureHintEngine()

        val hint = engine.evaluate(blurry(), classificationConfidence = 0.3f, nowMillis = 0)

        assertEquals(CaptureHintType.HOLD_STEADY, hint)
    }

    @Test
    fun `nunca hay dos indicaciones en el mismo intervalo aunque las causas cambien`() {
        val engine = CaptureHintEngine()
        var shown = 0
        var previous: CaptureHintType? = null

        val sequence = listOf(dark(), good(), blurry(), good(), offCenter(), soiled(), good(), dark())
        sequence.forEachIndexed { index, quality ->
            val hint = engine.evaluate(
                quality,
                classificationConfidence = null,
                nowMillis = index * 300L,
            )
            if (hint != null && hint != previous) shown++
            previous = hint
        }

        assertEquals(1, shown, "En menos de un intervalo solo puede aparecer una indicación nueva")
    }
}
