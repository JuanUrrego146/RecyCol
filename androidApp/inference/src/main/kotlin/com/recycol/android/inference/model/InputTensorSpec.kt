package com.recycol.android.inference.model

/**
 * Lo que el preprocesador necesita saber de la entrada de un modelo,
 * sea clasificador ([ModelSpec]) o detector ([DetectorModelSpec]).
 */
interface InputTensorSpec {

    /** Lado en píxeles de la entrada cuadrada (HWC, RGB). */
    val inputSize: Int

    /** `true` si la entrada es UINT8 `[0, 255]`; `false` si es FLOAT32 normalizado. */
    val quantizedInput: Boolean

    /** Media a restar en el caso FLOAT32 (sobre `[0, 255]`). */
    val inputMean: Float

    /** Desviación con la que se divide en el caso FLOAT32. */
    val inputStd: Float
}
