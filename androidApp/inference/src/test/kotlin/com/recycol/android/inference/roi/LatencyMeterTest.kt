package com.recycol.android.inference.roi

import kotlin.test.Test
import kotlin.test.assertEquals

class LatencyMeterTest {

    @Test
    fun `registra la ultima muestra y la media acumulada con un reloj inyectado`() {
        var now = 0L
        val meter = LatencyMeter(clock = { now })

        meter.measure { now += 10 }
        meter.measure { now += 30 }

        assertEquals(30L, meter.lastMillis)
        assertEquals(2L, meter.sampleCount)
        assertEquals(20.0, meter.averageMillis)
    }

    @Test
    fun `una operacion que lanza tambien registra su muestra`() {
        var now = 0L
        val meter = LatencyMeter(clock = { now })

        runCatching { meter.measure<Unit> { now += 5; error("falla") } }

        assertEquals(5L, meter.lastMillis)
        assertEquals(1L, meter.sampleCount)
    }
}
