package com.botabien.android.inference.engine

import android.os.Build
import com.botabien.android.inference.model.ModelSpec
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate

/**
 * Fábrica de motores LiteRT reales, uno por vía de aceleración.
 *
 * La detección de disponibilidad es lanzar: si la vía no existe en este
 * dispositivo, [create] falla y [ResilientInferenceEngine] pasa a la
 * siguiente. La CPU nunca falla en construcción (XNNPACK por defecto).
 */
internal object LiteRtEngines {

    /** Número de hilos del intérprete en CPU: acotado para no saturar gama baja. */
    private const val MAX_CPU_THREADS = 4

    /**
     * Crea un motor para [mode] sobre el contenido del modelo.
     *
     * @throws IllegalStateException si la vía no está disponible en el dispositivo.
     */
    fun create(model: ByteBuffer, spec: ModelSpec, mode: AccelerationMode): InferenceEngine {
        val options = Interpreter.Options()
        var delegate: Closeable? = null
        when (mode) {
            AccelerationMode.NNAPI -> {
                check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    "NNAPI requiere API 27 y este dispositivo es API ${Build.VERSION.SDK_INT}."
                }
                // Sin la implementación de referencia nnapi-cpu: si no hay
                // acelerador real, preferimos nuestra propia vía CPU (XNNPACK).
                val nnapi = NnApiDelegate(NnApiDelegate.Options().setUseNnapiCpu(false))
                delegate = nnapi
                options.addDelegate(nnapi)
            }
            AccelerationMode.GPU -> {
                val gpu = CompatibilityList().use { compatibility ->
                    check(compatibility.isDelegateSupportedOnThisDevice) {
                        "El delegado GPU no está soportado en este dispositivo."
                    }
                    GpuDelegate(compatibility.bestOptionsForThisDevice)
                }
                delegate = gpu
                options.addDelegate(gpu)
            }
            AccelerationMode.CPU -> {
                val cores = Runtime.getRuntime().availableProcessors()
                options.setNumThreads(cores.coerceIn(1, MAX_CPU_THREADS))
            }
        }
        return try {
            LiteRtInferenceEngine(Interpreter(model, options), delegate, mode, spec)
        } catch (failure: Exception) {
            runCatching { delegate?.close() }
            throw failure
        }
    }
}

/**
 * Motor sobre un [Interpreter] de LiteRT.
 *
 * Lee el tipo del tensor de salida en ejecución y decuantiza cuando es entero,
 * de modo que los consumidores siempre ven puntuaciones en `Float`.
 */
private class LiteRtInferenceEngine(
    private val interpreter: Interpreter,
    private val delegate: Closeable?,
    override val accelerationMode: AccelerationMode,
    private val spec: ModelSpec,
) : InferenceEngine {

    override fun run(input: ByteBuffer): FloatArray {
        input.rewind()
        val outputTensor = interpreter.getOutputTensor(0)
        val classes = spec.outputClasses
        return when (val type = outputTensor.dataType()) {
            DataType.FLOAT32 -> {
                val output = Array(1) { FloatArray(classes) }
                interpreter.run(input, output)
                output[0]
            }
            DataType.UINT8, DataType.INT8 -> {
                val output = ByteBuffer.allocateDirect(classes).order(ByteOrder.nativeOrder())
                interpreter.run(input, output)
                output.rewind()
                dequantize(output, classes, type, outputTensor.quantizationParams())
            }
            else -> throw InferenceException(
                "Tipo de salida no soportado: $type en ${spec.assetFileName}."
            )
        }
    }

    override fun close() {
        interpreter.close()
        delegate?.close()
    }

    private fun dequantize(
        output: ByteBuffer,
        classes: Int,
        type: DataType,
        params: org.tensorflow.lite.Tensor.QuantizationParams,
    ): FloatArray = FloatArray(classes) { index ->
        val raw = output.get(index).toInt()
        val quantized = if (type == DataType.UINT8) raw and 0xFF else raw
        params.scale * (quantized - params.zeroPoint)
    }
}
