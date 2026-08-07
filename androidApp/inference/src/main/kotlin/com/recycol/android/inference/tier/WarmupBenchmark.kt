package com.recycol.android.inference.tier

import com.recycol.android.inference.engine.InferenceEngine
import com.recycol.android.inference.model.InputTensorSpec
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Micro-benchmark de calentamiento (RF-029): mide la latencia real de
 * inferencia en este dispositivo concreto, que es la señal de gama que no
 * miente. Se ejecuta al primer arranque (después queda cacheada la gama).
 *
 * Corre hasta [maxRuns] inferencias sobre un tensor sintético y devuelve la
 * mediana, descartando la primera pasada (paga la inicialización del
 * intérprete y los delegados). Respeta un presupuesto de tiempo duro para no
 * comprometer el criterio de los 2 segundos de arranque: si el presupuesto se
 * agota, decide con las muestras que haya.
 */
class WarmupBenchmark(
    private val spec: InputTensorSpec,
    private val engine: InferenceEngine,
    private val maxRuns: Int = DEFAULT_RUNS,
    private val budgetMillis: Long = DEFAULT_BUDGET_MILLIS,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {

    /**
     * Mediana de latencia en milisegundos, o nula si no se pudo medir
     * (modelo ausente o motor caído): en ese caso la gama se resuelve solo
     * con las capacidades declaradas.
     */
    fun medianLatencyMillis(): Long? = try {
        val input = syntheticInput()
        val samples = mutableListOf<Long>()
        val deadline = clock() + budgetMillis

        // La primera pasada calienta intérprete y delegado: se mide aparte
        // y no cuenta para la mediana.
        engine.run(input)

        while (samples.size < maxRuns && clock() < deadline) {
            input.rewind()
            val start = clock()
            engine.run(input)
            samples += clock() - start
        }
        samples.sorted().getOrNull(samples.size / 2)
    } catch (_: Exception) {
        null
    }

    /** Tensor de entrada gris medio: el contenido no afecta la latencia. */
    private fun syntheticInput(): ByteBuffer {
        val channels = 3
        val bytesPerChannel = if (spec.quantizedInput) 1 else 4
        val buffer = ByteBuffer
            .allocateDirect(spec.inputSize * spec.inputSize * channels * bytesPerChannel)
            .order(ByteOrder.nativeOrder())
        while (buffer.hasRemaining()) {
            if (spec.quantizedInput) buffer.put(GRAY_LEVEL) else buffer.putFloat(GRAY_FLOAT)
        }
        buffer.rewind()
        return buffer
    }

    private companion object {
        const val DEFAULT_RUNS = 5
        const val DEFAULT_BUDGET_MILLIS = 1_200L
        const val NANOS_PER_MILLI = 1_000_000L
        const val GRAY_LEVEL = 0x80.toByte()
        const val GRAY_FLOAT = 0.5f
    }
}
