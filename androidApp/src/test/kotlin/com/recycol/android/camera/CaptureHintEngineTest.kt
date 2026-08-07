package com.recycol.android.camera

import com.recycol.domain.model.CaptureHint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Criterio de hecho de S13: como máximo una indicación cada intervalo mínimo,
 * nunca dos simultáneas, y ninguna cuando todas las métricas son aceptables.
 *
 * El motor consume las indicaciones del dominio tal como las emite
 * `ClassifyWasteUseCase` en `ClassificationOutcome.hints`.
 */
class CaptureHintEngineTest {

    private val interval = CaptureHintEngine.DEFAULT_MIN_INTERVAL_MILLIS

    @Test
    fun `sin indicaciones activas no se muestra nada`() {
        val engine = CaptureHintEngine()

        assertNull(engine.evaluate(emptyList(), classificationConfidence = null, nowMillis = 0))
    }

    @Test
    fun `una unica causa activa se muestra de inmediato`() {
        val engine = CaptureHintEngine()

        val hint = engine.evaluate(
            listOf(CaptureHint.MOVE_CLOSER),
            classificationConfidence = null,
            nowMillis = 0,
        )

        assertEquals(CaptureHint.MOVE_CLOSER, hint)
    }

    @Test
    fun `la indicacion vigente se mantiene sin contar como nueva`() {
        val engine = CaptureHintEngine()

        engine.evaluate(listOf(CaptureHint.MORE_LIGHT), null, nowMillis = 0)
        val sustained = engine.evaluate(listOf(CaptureHint.MORE_LIGHT), null, nowMillis = 100)

        assertEquals(CaptureHint.MORE_LIGHT, sustained)
    }

    @Test
    fun `la indicacion se retira en cuanto la causa desaparece`() {
        val engine = CaptureHintEngine()

        engine.evaluate(listOf(CaptureHint.MORE_LIGHT), null, nowMillis = 0)
        val afterFix = engine.evaluate(emptyList(), null, nowMillis = 100)

        assertNull(afterFix)
    }

    @Test
    fun `una causa nueva dentro del intervalo espera su turno`() {
        val engine = CaptureHintEngine()

        engine.evaluate(listOf(CaptureHint.MORE_LIGHT), null, nowMillis = 0)
        engine.evaluate(emptyList(), null, nowMillis = 200)
        val tooSoon = engine.evaluate(listOf(CaptureHint.MOVE_CLOSER), null, nowMillis = 400)
        val afterInterval =
            engine.evaluate(listOf(CaptureHint.MOVE_CLOSER), null, nowMillis = interval + 1)

        assertNull(tooSoon, "Una indicación nueva antes del intervalo mínimo viola RF-018")
        assertEquals(CaptureHint.MOVE_CLOSER, afterInterval)
    }

    @Test
    fun `ante varias causas solo se muestra la dominante`() {
        val engine = CaptureHintEngine()

        val hint = engine.evaluate(
            listOf(
                CaptureHint.CENTER_OBJECT,
                CaptureHint.MOVE_CLOSER,
                CaptureHint.MORE_LIGHT,
                CaptureHint.CLEAN_LENS,
            ),
            classificationConfidence = null,
            nowMillis = 0,
        )

        assertEquals(CaptureHint.CLEAN_LENS, hint, "El lente sucio es la causa raíz dominante")
    }

    @Test
    fun `escalar a una causa mas prioritaria tambien respeta el intervalo`() {
        val engine = CaptureHintEngine()

        engine.evaluate(listOf(CaptureHint.CENTER_OBJECT), null, nowMillis = 0)
        val stillOld = engine.evaluate(
            listOf(CaptureHint.CENTER_OBJECT, CaptureHint.CLEAN_LENS),
            classificationConfidence = null,
            nowMillis = 500,
        )
        val escalated = engine.evaluate(
            listOf(CaptureHint.CENTER_OBJECT, CaptureHint.CLEAN_LENS),
            classificationConfidence = null,
            nowMillis = interval + 500,
        )

        assertEquals(
            CaptureHint.CENTER_OBJECT,
            stillOld,
            "Dentro del intervalo se mantiene la vigente",
        )
        assertEquals(CaptureHint.CLEAN_LENS, escalated)
    }

    @Test
    fun `con confianza suficiente no se molesta al usuario`() {
        val engine = CaptureHintEngine()

        val hint = engine.evaluate(
            listOf(CaptureHint.MOVE_CLOSER),
            classificationConfidence = CaptureHintEngine.DEFAULT_SUFFICIENT_CONFIDENCE,
            nowMillis = 0,
        )

        assertNull(hint, "Con el clasificador seguro, las indicaciones se suprimen")
    }

    @Test
    fun `con confianza baja las indicaciones siguen activas`() {
        val engine = CaptureHintEngine()

        val hint = engine.evaluate(listOf(CaptureHint.MOVE_CLOSER), 0.3f, nowMillis = 0)

        assertEquals(CaptureHint.MOVE_CLOSER, hint)
    }

    @Test
    fun `POINT_INSIDE no pasa por la politica de indicaciones`() {
        val engine = CaptureHintEngine()

        val hint = engine.evaluate(
            listOf(CaptureHint.POINT_INSIDE),
            classificationConfidence = null,
            nowMillis = 0,
        )

        assertNull(hint, "La inspección interior la gestiona DirectedCaptureController (S14)")
    }

    @Test
    fun `nunca hay dos indicaciones en el mismo intervalo aunque las causas cambien`() {
        val engine = CaptureHintEngine()
        var shown = 0
        var previous: CaptureHint? = null

        val sequence = listOf(
            listOf(CaptureHint.MORE_LIGHT),
            emptyList(),
            listOf(CaptureHint.MOVE_CLOSER),
            emptyList(),
            listOf(CaptureHint.CENTER_OBJECT),
            listOf(CaptureHint.CLEAN_LENS),
            emptyList(),
            listOf(CaptureHint.MORE_LIGHT),
        )
        sequence.forEachIndexed { index, hints ->
            val hint = engine.evaluate(hints, classificationConfidence = null, nowMillis = index * 300L)
            if (hint != null && hint != previous) shown++
            previous = hint
        }

        assertEquals(1, shown, "En menos de un intervalo solo puede aparecer una indicación nueva")
    }
}
