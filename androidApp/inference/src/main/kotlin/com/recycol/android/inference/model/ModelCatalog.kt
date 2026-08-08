package com.recycol.android.inference.model

import com.recycol.domain.model.DeviceTier

/**
 * Catálogo de variantes de modelo por gama de dispositivo.
 *
 * ## Firma de entrada real (M4, no la del contrato S15 original)
 *
 * Los `.tflite` que exportó ML en S27 declaran `[1, 3, lado, lado]` INT8
 * (NCHW), no el `[1, lado, lado, 3]` UINT8 (NHWC) del contrato. Medido con
 * `ai_edge_litert.interpreter` sobre los artefactos reales — no asumido —, la
 * escala/punto cero de entrada es idéntica en los cuatro modelos porque
 * comparten pipeline de exportación: `scale=0.018649335950613022,
 * zeroPoint=-14`, imagen normalizada con media/desviación de ImageNet antes
 * de cuantizar (`ml/eval/evaluate_tflite.py`, que es la referencia exacta
 * usada para medir las cifras de `ml/REPORTE_METRICAS.md`).
 *
 * Se adapta el preprocesado del runtime en vez de reexportar: es la opción
 * más barata y más verificable (`FramePreprocessor`, `InputTensorSpec`), y no
 * hay reexportar sin volver a correr el pipeline completo de ML. El
 * `ModelOutputOrder` y la cuantización de **salida** sí cumplen el contrato
 * (softmax/logits sobre la taxonomía; `LiteRtEngines` ya decuantiza leyendo
 * la escala/punto cero reales del tensor, no hacía falta tocarlo).
 *
 * ## Reparto por gama (invertido respecto al contrato original)
 *
 * El contrato original asignaba un modelo distinto y más grande a cada gama
 * superior, bajo el supuesto de que más grande es más preciso. Contra
 * control (`ml/REPORTE_METRICAS.md`, criterio RNF-008 = acierto de **ruta**,
 * no de material) el orden se invierte: `mid` (MobileNetV3-Large, INT8) es
 * el que mejor generaliza — 74,4 % de ruta —, por delante de `high`
 * (EfficientNet-B2, 67,7 %) y de `low` (MobileNetV3-Small, 61,1 %, además
 * degradado por la cuantización: -9,5 pp de ruta solo por el INT8).
 * `high` queda **dominado en los dos ejes** por `mid` (peor y más pesado), así
 * que gama alta y media comparten el mismo modelo; gama baja se queda con su
 * propio modelo porque no hay banco de latencia en hardware de gama baja real
 * que confirme que aguantaría el de gama media (`ml/REPORTE_METRICAS.md`,
 * sección de riesgos abiertos — decisión de producto, no de arquitectura).
 */
object ModelCatalog {

    /** Etapa 1, gama baja: MobileNetV3-Small INT8, entrada 224×224 (CHW). */
    val MATERIAL_LOW = ModelSpec(
        assetFileName = "material_low.tflite",
        inputSize = 224,
        quantizedInput = false,
        outputsProbabilities = false,
        outputClasses = ModelOutputOrder.MATERIALS.size,
        inputLayout = InputLayout.CHW,
        normalizedInt8Quantization = NormalizedInt8Quantization(scale = 0.018649335950613022f, zeroPoint = -14),
    )

    /**
     * Etapa 1, gamas media **y alta**: MobileNetV3-Large INT8, entrada
     * 224×224 (CHW). Ganador contra control (74,4 % de ruta); ver el
     * reparto por gama en la doc de la clase.
     */
    val MATERIAL_MID = ModelSpec(
        assetFileName = "material_mid.tflite",
        inputSize = 224,
        quantizedInput = false,
        outputsProbabilities = false,
        outputClasses = ModelOutputOrder.MATERIALS.size,
        inputLayout = InputLayout.CHW,
        normalizedInt8Quantization = NormalizedInt8Quantization(scale = 0.018649335950613022f, zeroPoint = -14),
    )

    /**
     * Etapa 1: EfficientNet-B2 INT8, entrada 260×260 (CHW). **Retirado del
     * reparto por gama** (dominado por [MATERIAL_MID] en ruta y en tamaño;
     * ver doc de la clase); se conserva la spec por si cambia la evidencia,
     * pero el artefacto **no se empaqueta**: son 9,3 MB que ningún dispositivo
     * llegaría a cargar. Volver a activarlo exige copiar el `.tflite` a
     * `assets/models/` además de cambiar [materialSpecFor].
     */
    val MATERIAL_HIGH = ModelSpec(
        assetFileName = "material_high.tflite",
        inputSize = 260,
        quantizedInput = false,
        outputsProbabilities = false,
        outputClasses = ModelOutputOrder.MATERIALS.size,
        inputLayout = InputLayout.CHW,
        normalizedInt8Quantization = NormalizedInt8Quantization(scale = 0.018649335950613022f, zeroPoint = -14),
    )

    /** Etapa 2: clasificador binario de contaminación INT8, entrada 224×224 (CHW). */
    val CONTAMINATION = ModelSpec(
        assetFileName = "contamination.tflite",
        inputSize = 224,
        quantizedInput = false,
        outputsProbabilities = false,
        outputClasses = ModelOutputOrder.CONTAMINATION.size,
        inputLayout = InputLayout.CHW,
        normalizedInt8Quantization = NormalizedInt8Quantization(scale = 0.018649335950613022f, zeroPoint = -14),
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

    /**
     * Variante de la etapa 1 que corresponde a la gama resuelta. Alta y
     * media comparten modelo a propósito (ver doc de la clase).
     */
    fun materialSpecFor(tier: DeviceTier): ModelSpec = when (tier) {
        DeviceTier.LOW -> MATERIAL_LOW
        DeviceTier.MID -> MATERIAL_MID
        DeviceTier.HIGH -> MATERIAL_MID
    }
}
