package com.botabien.android.inference.engine

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pruebas del respaldo automático (RF-014, criterio de hecho de S15):
 * si el delegado no está disponible o falla en caliente, se cae a CPU
 * sin propagar el error.
 */
class ResilientInferenceEngineTest {

    private class FakeEngine(
        override val accelerationMode: AccelerationMode,
        private val result: FloatArray = floatArrayOf(1f),
        private val failOnRun: Boolean = false,
    ) : InferenceEngine {
        var closed = false

        override fun run(input: ByteBuffer): FloatArray {
            check(!failOnRun) { "fallo simulado del delegado en caliente" }
            return result
        }

        override fun close() {
            closed = true
        }
    }

    private val anyInput: ByteBuffer = ByteBuffer.allocate(4)

    @Test
    fun `usa la primera via disponible cuando construye sin error`() {
        val engine = ResilientInferenceEngine { mode -> FakeEngine(mode) }

        assertEquals(AccelerationMode.NNAPI, engine.accelerationMode)
    }

    @Test
    fun `cae a CPU cuando ningun delegado esta disponible`() {
        val attempted = mutableListOf<AccelerationMode>()
        val engine = ResilientInferenceEngine { mode ->
            attempted += mode
            if (mode != AccelerationMode.CPU) error("delegado $mode no disponible")
            FakeEngine(mode, floatArrayOf(0.2f, 0.8f))
        }

        val scores = engine.run(anyInput)

        assertEquals(AccelerationMode.CPU, engine.accelerationMode)
        assertEquals(listOf(AccelerationMode.NNAPI, AccelerationMode.GPU, AccelerationMode.CPU), attempted)
        assertEquals(0.8f, scores[1])
    }

    @Test
    fun `un fallo del delegado en caliente degrada a CPU y reintenta sin propagar error`() {
        val delegateEngine = FakeEngine(AccelerationMode.GPU, failOnRun = true)
        val cpuResult = floatArrayOf(0.9f)
        val engine = ResilientInferenceEngine(
            preferredOrder = listOf(AccelerationMode.GPU, AccelerationMode.CPU),
        ) { mode ->
            if (mode == AccelerationMode.GPU) delegateEngine else FakeEngine(mode, cpuResult)
        }

        val scores = engine.run(anyInput)

        assertEquals(0.9f, scores[0])
        assertTrue(delegateEngine.closed, "el motor del delegado fallido debe liberarse")
        assertEquals(AccelerationMode.CPU, engine.accelerationMode)
    }

    @Test
    fun `tras degradar a CPU las inferencias siguientes ya no pasan por el delegado`() {
        var gpuBuilds = 0
        val engine = ResilientInferenceEngine(
            preferredOrder = listOf(AccelerationMode.GPU, AccelerationMode.CPU),
        ) { mode ->
            if (mode == AccelerationMode.GPU) {
                gpuBuilds++
                FakeEngine(mode, failOnRun = true)
            } else {
                FakeEngine(mode)
            }
        }

        engine.run(anyInput)
        engine.run(anyInput)

        assertEquals(1, gpuBuilds, "el delegado fallido no debe reconstruirse")
    }

    @Test
    fun `si la CPU tambien falla se lanza InferenceException`() {
        val engine = ResilientInferenceEngine(
            preferredOrder = listOf(AccelerationMode.CPU),
        ) { mode -> FakeEngine(mode, failOnRun = true) }

        assertFailsWith<InferenceException> { engine.run(anyInput) }
    }

    @Test
    fun `el orden de preferencia debe incluir CPU`() {
        assertFailsWith<IllegalArgumentException> {
            ResilientInferenceEngine(
                preferredOrder = listOf(AccelerationMode.NNAPI, AccelerationMode.GPU),
            ) { mode -> FakeEngine(mode) }
        }
    }
}
