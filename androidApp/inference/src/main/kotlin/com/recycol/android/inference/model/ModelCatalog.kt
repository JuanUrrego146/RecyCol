package com.recycol.android.inference.model

import com.recycol.domain.model.DeviceTier

/**
 * Catálogo de variantes de modelo por gama de dispositivo.
 *
 * Materializa la fila «Modelo» de la matriz de gamas de
 * `context-for-vibe-coding.md`: MobileNetV3-Small para gama baja,
 * MobileNetV3-Large 0.75 para media y EfficientNet-Lite2 para alta,
 * todas cuantizadas INT8. La etapa de contaminación usa una única
 * variante ligera en las tres gamas.
 */
object ModelCatalog {

    /** Etapa 1, gama baja: MobileNetV3-Small INT8, entrada 224×224. */
    val MATERIAL_LOW = ModelSpec(
        assetFileName = "material_low.tflite",
        inputSize = 224,
        quantizedInput = true,
        outputClasses = ModelOutputOrder.MATERIALS.size,
    )

    /** Etapa 1, gama media: MobileNetV3-Large 0.75 INT8, entrada 224×224. */
    val MATERIAL_MID = ModelSpec(
        assetFileName = "material_mid.tflite",
        inputSize = 224,
        quantizedInput = true,
        outputClasses = ModelOutputOrder.MATERIALS.size,
    )

    /** Etapa 1, gama alta: EfficientNet-Lite2 INT8, entrada 260×260. */
    val MATERIAL_HIGH = ModelSpec(
        assetFileName = "material_high.tflite",
        inputSize = 260,
        quantizedInput = true,
        outputClasses = ModelOutputOrder.MATERIALS.size,
    )

    /** Etapa 2: clasificador binario de contaminación INT8, entrada 224×224. */
    val CONTAMINATION = ModelSpec(
        assetFileName = "contamination.tflite",
        inputSize = 224,
        quantizedInput = true,
        outputClasses = ModelOutputOrder.CONTAMINATION.size,
    )

    /**
     * Detector de objeto para el recorte del área de interés en gama media y
     * alta (RF-010). Genérico y agnóstico a la clase; puede ser un
     * EfficientDet-Lite0 o SSD-MobileNet de referencia mientras ML no
     * publique uno propio. Opcional: si falta, todas las gamas usan el marco
     * guía fijo y nada deja de funcionar.
     */
    val DETECTOR = DetectorModelSpec(
        assetFileName = "detector.tflite",
        inputSize = 320,
        quantizedInput = true,
        maxDetections = 10,
    )

    /** Variante de la etapa 1 que corresponde a la gama resuelta. */
    fun materialSpecFor(tier: DeviceTier): ModelSpec = when (tier) {
        DeviceTier.LOW -> MATERIAL_LOW
        DeviceTier.MID -> MATERIAL_MID
        DeviceTier.HIGH -> MATERIAL_HIGH
    }
}
