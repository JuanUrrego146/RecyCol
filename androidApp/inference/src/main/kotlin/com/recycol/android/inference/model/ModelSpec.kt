package com.recycol.android.inference.model

/**
 * Especificación de un modelo empaquetado: dónde está y cómo se le habla.
 *
 * Es la mitad estática del contrato con el pipeline de `ml/` (agente ML):
 * el archivo exportado en S27 debe cumplir exactamente lo que declara su spec.
 * Cambiar un campo de una spec publicada requiere issue de coordinación.
 *
 * @property assetFileName nombre del archivo dentro de `assets/models/`.
 * @property inputSize lado en píxeles de la entrada cuadrada (HWC, RGB).
 * @property quantizedInput `true` si la entrada es UINT8 `[0, 255]`;
 *   `false` si es FLOAT32 normalizado con [inputMean] y [inputStd].
 * @property outputClasses número de clases del tensor de salida.
 * @property outputsProbabilities `true` si la última capa ya es softmax;
 *   `false` si el modelo emite logits y hay que aplicar softmax aquí.
 * @property inputMean media a restar en el caso FLOAT32 (sobre `[0, 255]`).
 * @property inputStd desviación con la que se divide en el caso FLOAT32.
 */
data class ModelSpec(
    val assetFileName: String,
    override val inputSize: Int,
    override val quantizedInput: Boolean,
    val outputClasses: Int,
    val outputsProbabilities: Boolean = true,
    override val inputMean: Float = 0f,
    override val inputStd: Float = 255f,
) : InputTensorSpec
