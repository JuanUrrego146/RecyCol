package com.botabien.android.inference.tier

import com.botabien.android.inference.engine.AccelerationMode
import com.botabien.android.inference.engine.InferenceEngine
import com.botabien.android.inference.model.ModelCatalog
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WarmupBenchmarkTest {

    /** Motor cuyo tiempo de inferencia lo dicta la lista de latencias. */
    private class TimedFakeEngine(
        private val advanceClock: (Long) -> Unit,
        private val latencies: LongArray,
        private val failAlways: Boolean = false,
    ) : InferenceEngine {
        var runs = 0
        override val accelerationMode = AccelerationMode.CPU

        override fun run(input: ByteBuffer): FloatArray {
            check(!failAlways) { "modelo ausente o motor caído" }
            advanceClock(latencies[runs.coerceAtMost(latencies.size - 1)])
            runs++
            return floatArrayOf(1f)
        }

        override fun close() = Unit
    }

    @Test
    fun `devuelve la mediana descartando la pasada de calentamiento`() {
        var now = 0L
        // Primera pasada (calentamiento, 500 ms) no cuenta; mediana de [10, 20, 30, 40, 50] = 30.
        val engine = TimedFakeEngine({ now += it }, longArrayOf(500, 10, 20, 30, 40, 50))
        val benchmark = WarmupBenchmark(
            spec = ModelCatalog.MATERIAL_LOW,
            engine = engine,
            maxRuns = 5,
            budgetMillis = 10_000,
            clock = { now },
        )

        assertEquals(30L, benchmark.medianLatencyMillis())
        assertEquals(6, engine.runs)
    }

    @Test
    fun `respeta el presupuesto de tiempo y decide con las muestras que haya`() {
        var now = 0L
        val engine = TimedFakeEngine({ now += it }, longArrayOf(100, 400, 400, 400, 400, 400))
        val benchmark = WarmupBenchmark(
            spec = ModelCatalog.MATERIAL_LOW,
            engine = engine,
            maxRuns = 10,
            budgetMillis = 1_000,
            clock = { now },
        )

        val median = benchmark.medianLatencyMillis()

        assertEquals(400L, median)
        assertTrue(engine.runs < 11, "el presupuesto corta antes de las 10 pasadas (corrió ${engine.runs})")
    }

    @Test
    fun `sin modelo o con el motor caido devuelve nulo`() {
        var now = 0L
        val engine = TimedFakeEngine({ now += it }, longArrayOf(0), failAlways = true)
        val benchmark = WarmupBenchmark(
            spec = ModelCatalog.MATERIAL_LOW,
            engine = engine,
            clock = { now },
        )

        assertNull(benchmark.medianLatencyMillis())
    }
}
