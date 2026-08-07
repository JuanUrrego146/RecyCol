package com.recycol.android.inference.roi

import com.recycol.android.inference.engine.InferenceEngine
import com.recycol.android.inference.frame.PixelAccessFrame
import com.recycol.android.inference.image.FramePreprocessor
import com.recycol.android.inference.model.DetectorModelSpec
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Estrategia de ROI con detector de objeto para gama media y alta (RF-010).
 *
 * Corre el detector sobre el recorte central del frame, toma la caja con
 * mejor puntuación de forma agnóstica a la clase, la expande con margen y la
 * cuadra. Cualquier cosa que salga mal —sin cajas confiables, modelo caído,
 * salida malformada— degrada al [fallback] (marco guía): el detector es una
 * mejora, nunca un requisito, y la clasificación sigue funcionando.
 *
 * El post-procesado esperado es el estándar de detección de TFLite: cuatro
 * tensores `[cajas, clases, puntuaciones, conteo]`, cajas normalizadas
 * `[ymin, xmin, ymax, xmax]` relativas a la entrada del detector.
 *
 * El coste de cada detección se registra en [latency] (tarea de S16); el
 * banco formal por gama lo consume en S20.
 */
class DetectorRoi(
    private val engine: InferenceEngine,
    private val spec: DetectorModelSpec,
    private val preprocessor: FramePreprocessor = FramePreprocessor(),
    private val fallback: RoiStrategy = GuideFrameRoi(),
    val latency: LatencyMeter = LatencyMeter(),
) : RoiStrategy, AutoCloseable {

    override suspend fun findRegion(frame: PixelAccessFrame): CropRegion = try {
        latency.measure { detect(frame) } ?: fallback.findRegion(frame)
    } catch (_: Exception) {
        // El detector nunca tumba la clasificación (criterio de S16).
        fallback.findRegion(frame)
    }

    /** Libera el motor del detector (lo invoca el recambio de gama, #102). */
    override fun close() {
        engine.close()
    }

    private fun detect(frame: PixelAccessFrame): CropRegion? {
        val searchArea = CropRegion.centeredSquare(frame.width, frame.height)
        val input = preprocessor.preprocess(frame, spec, searchArea)
        val outputs = engine.runMultiOutput(input)
        if (outputs.size < DETECTION_OUTPUTS) return null

        val boxes = outputs[BOXES_INDEX]
        val scores = outputs[SCORES_INDEX]
        val declaredCount = outputs[COUNT_INDEX].firstOrNull()?.toInt() ?: spec.maxDetections
        val count = minOf(declaredCount, scores.size, boxes.size / BOX_FIELDS)

        var best = -1
        for (index in 0 until count) {
            if (scores[index] < spec.scoreThreshold) continue
            if (best == -1 || scores[index] > scores[best]) best = index
        }
        if (best == -1) return null

        return regionFromBox(boxes, best, searchArea, frame)
    }

    /** Traduce la caja normalizada (relativa al área de búsqueda) a píxeles del frame. */
    private fun regionFromBox(
        boxes: FloatArray,
        index: Int,
        searchArea: CropRegion,
        frame: PixelAccessFrame,
    ): CropRegion {
        val yMin = boxes[index * BOX_FIELDS].coerceIn(0f, 1f)
        val xMin = boxes[index * BOX_FIELDS + 1].coerceIn(0f, 1f)
        val yMax = boxes[index * BOX_FIELDS + 2].coerceIn(0f, 1f)
        val xMax = boxes[index * BOX_FIELDS + 3].coerceIn(0f, 1f)
        if (xMax <= xMin || yMax <= yMin) return fallbackRegion(frame)

        val widthPx = (xMax - xMin) * searchArea.size
        val heightPx = (yMax - yMin) * searchArea.size
        val expandedSide = (max(widthPx, heightPx) * (1f + 2f * spec.marginFraction))
            .roundToInt()
            .coerceAtLeast((searchArea.size * MIN_SIDE_FRACTION).roundToInt())

        val centerX = searchArea.left + ((xMin + xMax) / 2f * searchArea.size).roundToInt()
        val centerY = searchArea.top + ((yMin + yMax) / 2f * searchArea.size).roundToInt()
        return CropRegion.centeredAt(centerX, centerY, expandedSide, frame.width, frame.height)
    }

    private fun fallbackRegion(frame: PixelAccessFrame): CropRegion =
        CropRegion.centeredFraction(frame.width, frame.height, GuideFrameRoi.GUIDE_FRACTION)

    private companion object {
        const val DETECTION_OUTPUTS = 4
        const val BOXES_INDEX = 0
        const val SCORES_INDEX = 2
        const val COUNT_INDEX = 3
        const val BOX_FIELDS = 4

        /** Lado mínimo del recorte como fracción del área de búsqueda: una caja
         *  degenerada no puede dejar al clasificador sin contexto. */
        const val MIN_SIDE_FRACTION = 0.25f
    }
}
