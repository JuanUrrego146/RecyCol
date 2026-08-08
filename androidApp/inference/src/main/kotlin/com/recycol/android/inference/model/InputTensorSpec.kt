package com.recycol.android.inference.model

/**
 * Orden de los ejes del tensor de entrada.
 *
 * `HWC` es lo que exige el contrato S15 (`[1, lado, lado, 3]`). `CHW` existe
 * porque los artefactos que exportó ML para M4 declaran `[1, 3, lado, lado]`
 * (litert-torch conserva el layout nativo de PyTorch) — no cumplen el
 * contrato de entrada tal cual, y adaptar el preprocesado aquí es más barato
 * y más verificable que reexportar. Ver `ModelCatalog` y el README del módulo.
 */
enum class InputLayout { HWC, CHW }

/**
 * Lo que el preprocesador necesita saber de la entrada de un modelo,
 * sea clasificador ([ModelSpec]) o detector ([DetectorModelSpec]).
 */
interface InputTensorSpec {

    /** Lado en píxeles de la entrada cuadrada (RGB, sin alfa). */
    val inputSize: Int

    /** Orden de los ejes del tensor. Por defecto `HWC` (el del contrato S15). */
    val inputLayout: InputLayout get() = InputLayout.HWC

    /** `true` si la entrada es UINT8 `[0, 255]` sin normalizar. */
    val quantizedInput: Boolean

    /** Media a restar en el caso FLOAT32 (sobre `[0, 255]`). */
    val inputMean: Float

    /** Desviación con la que se divide en el caso FLOAT32. */
    val inputStd: Float

    /**
     * Cuantización de entrada normalizada (INT8), o `null` si no aplica.
     *
     * Distinta de [quantizedInput]: esa es UINT8 crudo `[0, 255]` sin ninguna
     * normalización, tal como llega de la cámara. Esta es la firma real de
     * los `.tflite` de M4 — imagen normalizada con media/desviación de
     * ImageNet y luego cuantizada a INT8 con la escala y el punto cero que
     * litert-torch fijó en el propio artefacto (medidos con
     * `ai_edge_litert.interpreter`, no supuestos).
     */
    val normalizedInt8Quantization: NormalizedInt8Quantization? get() = null
}

/**
 * Parámetros de la cuantización INT8 medidos sobre el artefacto real.
 *
 * @property scale escala de la cuantización afín del tensor de entrada.
 * @property zeroPoint punto cero de la cuantización afín.
 * @property channelMean media ImageNet por canal RGB, sobre `[0, 1]`.
 * @property channelStd desviación ImageNet por canal RGB, sobre `[0, 1]`.
 */
data class NormalizedInt8Quantization(
    val scale: Float,
    val zeroPoint: Int,
    val channelMean: FloatArray = IMAGENET_MEAN,
    val channelStd: FloatArray = IMAGENET_STD,
) {
    companion object {
        /** Constantes estándar de ImageNet, en orden R, G, B. */
        val IMAGENET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        val IMAGENET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }

    override fun equals(other: Any?): Boolean =
        other is NormalizedInt8Quantization && scale == other.scale && zeroPoint == other.zeroPoint

    override fun hashCode(): Int = 31 * scale.hashCode() + zeroPoint
}
