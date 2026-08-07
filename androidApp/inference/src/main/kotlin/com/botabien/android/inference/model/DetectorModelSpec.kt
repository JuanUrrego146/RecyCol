package com.botabien.android.inference.model

/**
 * Especificación del detector de objeto (RF-010).
 *
 * Es un detector genérico con post-procesado TFLite estándar (cuatro tensores
 * de salida: cajas, clases, puntuaciones y conteo). Se usa de forma agnóstica
 * a la clase: solo interesa dónde está el objeto dominante, el material lo
 * decide la etapa 1. Puede ser un modelo preentrenado de referencia
 * (EfficientDet-Lite0 / SSD-MobileNet) mientras ML no publique uno propio.
 *
 * @property assetFileName nombre del archivo dentro de `assets/models/`.
 * @property inputSize lado de la entrada cuadrada (HWC, RGB).
 * @property quantizedInput `true` si la entrada es UINT8 `[0, 255]`.
 * @property maxDetections detecciones máximas del post-procesado del modelo.
 * @property scoreThreshold puntuación mínima para aceptar una caja.
 * @property marginFraction margen que se añade alrededor de la caja al recortar.
 */
data class DetectorModelSpec(
    val assetFileName: String,
    override val inputSize: Int,
    override val quantizedInput: Boolean,
    val maxDetections: Int,
    val scoreThreshold: Float = 0.35f,
    val marginFraction: Float = 0.15f,
    override val inputMean: Float = 0f,
    override val inputStd: Float = 255f,
) : InputTensorSpec
