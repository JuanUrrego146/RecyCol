package com.recycol.android.inference.roi

import com.recycol.android.inference.FakePixelFrame
import com.recycol.android.inference.engine.AccelerationMode
import com.recycol.android.inference.engine.InferenceEngine
import com.recycol.android.inference.model.DetectorModelSpec
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DetectorRoiTest {

    /** Motor falso con el layout estándar de detección: cajas, clases, puntuaciones, conteo. */
    private class FakeDetectionEngine(
        private val boxes: FloatArray,
        private val scores: FloatArray,
        private val failOnRun: Boolean = false,
    ) : InferenceEngine {
        override val accelerationMode = AccelerationMode.CPU

        override fun run(input: ByteBuffer): FloatArray = error("el detector usa runMultiOutput")

        override fun runMultiOutput(input: ByteBuffer): List<FloatArray> {
            check(!failOnRun) { "fallo simulado del detector" }
            return listOf(
                boxes,
                FloatArray(scores.size),
                scores,
                floatArrayOf(scores.size.toFloat()),
            )
        }

        override fun close() = Unit
    }

    private val spec = DetectorModelSpec(
        assetFileName = "detector.tflite",
        inputSize = 320,
        quantizedInput = true,
        maxDetections = 10,
        scoreThreshold = 0.35f,
        marginFraction = 0.15f,
    )

    // Frame cuadrado 200x200: el área de búsqueda es el frame completo.
    private val frame = FakePixelFrame.solid(width = 200, height = 200, argb = 0xFF888888.toInt())

    @Test
    fun `elige la caja con mejor puntuacion y la expande con margen`() = runTest {
        // Caja B: centro (0.5, 0.5), 0.4x0.4 → 80 px; expandida: 80 * 1.3 = 104.
        val engine = FakeDetectionEngine(
            boxes = floatArrayOf(
                0.0f, 0.0f, 0.2f, 0.2f, // caja A, peor puntuación
                0.3f, 0.3f, 0.7f, 0.7f, // caja B, mejor puntuación
            ),
            scores = floatArrayOf(0.50f, 0.90f),
        )
        val detector = DetectorRoi(engine, spec)

        val region = detector.findRegion(frame)

        assertEquals(104, region.size)
        assertEquals(100, region.left + region.size / 2, "centro X")
        assertEquals(100, region.top + region.size / 2, "centro Y")
    }

    @Test
    fun `sin cajas sobre el umbral degrada al marco guia`() = runTest {
        val engine = FakeDetectionEngine(
            boxes = floatArrayOf(0.1f, 0.1f, 0.9f, 0.9f),
            scores = floatArrayOf(0.10f),
        )
        val detector = DetectorRoi(engine, spec)

        val region = detector.findRegion(frame)

        assertEquals(
            CropRegion.centeredFraction(200, 200, GuideFrameRoi.GUIDE_FRACTION),
            region,
        )
    }

    @Test
    fun `un fallo del detector degrada al marco guia sin propagar error`() = runTest {
        val engine = FakeDetectionEngine(
            boxes = FloatArray(4),
            scores = FloatArray(1),
            failOnRun = true,
        )
        val detector = DetectorRoi(engine, spec)

        val region = detector.findRegion(frame)

        assertEquals(
            CropRegion.centeredFraction(200, 200, GuideFrameRoi.GUIDE_FRACTION),
            region,
        )
    }

    @Test
    fun `una caja diminuta se acota al lado minimo del area de busqueda`() = runTest {
        val engine = FakeDetectionEngine(
            boxes = floatArrayOf(0.49f, 0.49f, 0.51f, 0.51f),
            scores = floatArrayOf(0.95f),
        )
        val detector = DetectorRoi(engine, spec)

        val region = detector.findRegion(frame)

        // 25 % del área de búsqueda (200 px) = 50 px.
        assertEquals(50, region.size)
    }

    @Test
    fun `una caja pegada al borde produce una region que cabe en el frame`() = runTest {
        val engine = FakeDetectionEngine(
            boxes = floatArrayOf(0.0f, 0.0f, 0.5f, 0.5f),
            scores = floatArrayOf(0.80f),
        )
        val detector = DetectorRoi(engine, spec)

        val region = detector.findRegion(frame)

        assertTrue(region.left >= 0 && region.top >= 0)
        assertTrue(region.left + region.size <= 200)
        assertTrue(region.top + region.size <= 200)
    }

    @Test
    fun `cada deteccion registra su latencia`() = runTest {
        val engine = FakeDetectionEngine(
            boxes = floatArrayOf(0.3f, 0.3f, 0.7f, 0.7f),
            scores = floatArrayOf(0.90f),
        )
        val detector = DetectorRoi(engine, spec)

        detector.findRegion(frame)
        detector.findRegion(frame)

        assertEquals(2L, detector.latency.sampleCount)
        assertNotNull(detector.latency.lastMillis)
        assertNotNull(detector.latency.averageMillis)
    }
}
