package com.botabien.android.inference.engine

import java.nio.ByteBuffer

/**
 * Motor con detección de disponibilidad y respaldo automático en CPU (RF-014).
 *
 * Recorre las vías de aceleración en orden de preferencia y se queda con la
 * primera cuyo motor se construye sin error. Si un delegado falla más tarde,
 * en caliente, reconstruye el motor en CPU y reintenta la inferencia una vez:
 * el fallo del delegado nunca llega al usuario como error (criterio de hecho
 * de S15). La construcción es perezosa: el motor real se crea en la primera
 * inferencia, no al inyectar.
 *
 * @param preferredOrder vías a intentar, en orden. Debe incluir [AccelerationMode.CPU],
 *   que es el único respaldo que no puede faltar.
 * @param buildEngine fábrica del motor concreto para una vía; lanza si la vía
 *   no está disponible en este dispositivo.
 */
class ResilientInferenceEngine(
    private val preferredOrder: List<AccelerationMode> = listOf(
        AccelerationMode.NNAPI,
        AccelerationMode.GPU,
        AccelerationMode.CPU,
    ),
    private val buildEngine: (AccelerationMode) -> InferenceEngine,
) : InferenceEngine {

    init {
        require(AccelerationMode.CPU in preferredOrder) {
            "El orden de preferencia debe incluir CPU: es el respaldo final."
        }
    }

    private var active: InferenceEngine? = null

    override val accelerationMode: AccelerationMode
        get() = ensureEngine().accelerationMode

    @Synchronized
    override fun run(input: ByteBuffer): FloatArray =
        executeWithFallback(input) { engine, buffer -> engine.run(buffer) }

    @Synchronized
    override fun runMultiOutput(input: ByteBuffer): List<FloatArray> =
        executeWithFallback(input) { engine, buffer -> engine.runMultiOutput(buffer) }

    @Synchronized
    override fun close() {
        active?.close()
        active = null
    }

    private fun <R> executeWithFallback(
        input: ByteBuffer,
        invoke: (InferenceEngine, ByteBuffer) -> R,
    ): R {
        val engine = ensureEngine()
        return try {
            invoke(engine, input)
        } catch (failure: Exception) {
            if (engine.accelerationMode == AccelerationMode.CPU) {
                throw InferenceException("La inferencia falló en CPU, sin más respaldos.", failure)
            }
            fallBackToCpu(engine, input, invoke)
        }
    }

    /** El delegado falló en caliente: degradar a CPU de forma permanente y reintentar. */
    private fun <R> fallBackToCpu(
        failed: InferenceEngine,
        input: ByteBuffer,
        invoke: (InferenceEngine, ByteBuffer) -> R,
    ): R {
        runCatching { failed.close() }
        val cpu = try {
            buildEngine(AccelerationMode.CPU)
        } catch (creationFailure: Exception) {
            active = null
            throw InferenceException(
                "El delegado ${failed.accelerationMode} falló y no se pudo construir el respaldo en CPU.",
                creationFailure,
            )
        }
        active = cpu
        input.rewind()
        return invoke(cpu, input)
    }

    @Synchronized
    private fun ensureEngine(): InferenceEngine {
        active?.let { return it }
        val failures = mutableListOf<String>()
        for (mode in preferredOrder) {
            try {
                val engine = buildEngine(mode)
                active = engine
                return engine
            } catch (unavailable: Exception) {
                failures += "$mode: ${unavailable.message}"
            }
        }
        throw InferenceException("Ninguna vía de inferencia disponible. Intentos: $failures")
    }
}
