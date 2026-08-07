package com.recycol.android.inference.tier

import com.recycol.domain.model.DeviceTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalysisCadenceTest {

    @Test
    fun `la cadencia sigue la matriz de gamas`() {
        assertNull(
            AnalysisCadence.targetIntervalMillisFor(DeviceTier.LOW),
            "gama baja clasifica bajo demanda, sin análisis continuo",
        )
        assertEquals(200L, AnalysisCadence.targetIntervalMillisFor(DeviceTier.MID))
        assertEquals(100L, AnalysisCadence.targetIntervalMillisFor(DeviceTier.HIGH))
    }

    @Test
    fun `la compuerta deja pasar un frame por intervalo`() {
        var now = 0L
        val throttle = FrameThrottle(intervalMillis = 200, clock = { now })

        assertTrue(throttle.shouldAnalyze(), "el primer frame siempre pasa")
        now = 100
        assertFalse(throttle.shouldAnalyze(), "dentro del intervalo se descarta")
        now = 200
        assertTrue(throttle.shouldAnalyze(), "cumplido el intervalo pasa el siguiente")
        now = 250
        assertFalse(throttle.shouldAnalyze())
        now = 450
        assertTrue(throttle.shouldAnalyze())
    }
}
