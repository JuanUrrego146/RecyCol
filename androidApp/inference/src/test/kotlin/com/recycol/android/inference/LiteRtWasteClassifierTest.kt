package com.recycol.android.inference

import com.recycol.android.inference.engine.AccelerationMode
import com.recycol.android.inference.engine.InferenceEngine
import com.recycol.android.inference.engine.InferenceException
import com.recycol.android.inference.frame.PixelAccessFrame
import com.recycol.android.inference.model.ModelCatalog
import com.recycol.android.inference.model.ModelOutputOrder
import com.recycol.android.inference.roi.CropRegion
import com.recycol.android.inference.roi.LatencyMeter
import com.recycol.android.inference.roi.RoiStrategy
import com.recycol.domain.model.ContaminationState
import com.recycol.domain.model.ImageFrame
import com.recycol.domain.model.WasteMaterial
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class LiteRtWasteClassifierTest {

    private class ScriptedEngine(private val scores: FloatArray) : InferenceEngine {
        override val accelerationMode = AccelerationMode.CPU
        override fun run(input: ByteBuffer): FloatArray = scores
        override fun close() = Unit
    }

    private class RecordingRoi : RoiStrategy {
        var calls = 0
        override suspend fun findRegion(frame: PixelAccessFrame): CropRegion {
            calls++
            return CropRegion.centeredSquare(frame.width, frame.height)
        }
    }

    private val frame = FakePixelFrame.solid(width = 32, height = 32, argb = 0xFF808080.toInt())

    private fun materialScores(winner: WasteMaterial, confidence: Float): FloatArray {
        val rest = (1f - confidence) / (ModelOutputOrder.MATERIALS.size - 1)
        return FloatArray(ModelOutputOrder.MATERIALS.size) { index ->
            if (ModelOutputOrder.MATERIALS[index] == winner) confidence else rest
        }
    }

    private fun classifier(
        material: InferenceEngine,
        contamination: InferenceEngine? = null,
    ) = LiteRtWasteClassifier(
        materialEngine = material,
        materialSpec = ModelCatalog.MATERIAL_LOW,
        contaminationEngine = contamination,
        contaminationSpec = ModelCatalog.CONTAMINATION,
    )

    @Test
    fun `el material ganador se mapea por indice segun el orden del enumerado`() = runTest {
        val subject = classifier(ScriptedEngine(materialScores(WasteMaterial.GLASS, 0.85f)))

        val result = subject.classify(frame)

        assertEquals(WasteMaterial.GLASS, result.material)
        assertEquals(0.85f, result.confidence, absoluteTolerance = 0.0001f)
    }

    @Test
    fun `una salida con numero de clases distinto a la taxonomia falla explicitamente`() = runTest {
        val subject = classifier(ScriptedEngine(floatArrayOf(0.5f, 0.5f)))

        assertFailsWith<InferenceException> { subject.classify(frame) }
    }

    @Test
    fun `un frame sin acceso a pixeles es un error de integracion`() = runTest {
        val subject = classifier(ScriptedEngine(materialScores(WasteMaterial.PAPER, 0.9f)))
        val opaqueFrame = object : ImageFrame {
            override val width = 10
            override val height = 10
            override val timestampMillis = 0L
        }

        assertFailsWith<IllegalArgumentException> { subject.classify(opaqueFrame) }
    }

    @Test
    fun `sin modelo de contaminacion la inspeccion devuelve UNKNOWN con confianza cero`() = runTest {
        val subject = classifier(ScriptedEngine(materialScores(WasteMaterial.PLASTIC, 0.9f)))

        val result = subject.inspectContamination(frame)

        assertEquals(ContaminationState.UNKNOWN, result.state)
        assertEquals(0f, result.confidence)
    }

    @Test
    fun `la inspeccion mapea el binario contaminado con su confianza`() = runTest {
        val subject = classifier(
            material = ScriptedEngine(materialScores(WasteMaterial.PLASTIC, 0.9f)),
            contamination = ScriptedEngine(floatArrayOf(0.15f, 0.85f)),
        )

        val result = subject.inspectContamination(frame)

        assertEquals(ContaminationState.CONTAMINATED, result.state)
        assertEquals(0.85f, result.confidence, absoluteTolerance = 0.0001f)
    }

    @Test
    fun `la inspeccion recorta la toma dirigida con su propia estrategia (RF-021)`() = runTest {
        val stageOneRoi = RecordingRoi()
        val stageTwoRoi = RecordingRoi()
        val subject = LiteRtWasteClassifier(
            materialEngine = ScriptedEngine(materialScores(WasteMaterial.PLASTIC, 0.9f)),
            materialSpec = ModelCatalog.MATERIAL_LOW,
            contaminationEngine = ScriptedEngine(floatArrayOf(0.8f, 0.2f)),
            contaminationSpec = ModelCatalog.CONTAMINATION,
            roiStrategy = stageOneRoi,
            contaminationRoi = stageTwoRoi,
        )

        val result = subject.inspectContamination(frame)

        assertEquals(ContaminationState.CLEAN, result.state)
        assertEquals(1, stageTwoRoi.calls, "la etapa 2 recorta con su estrategia")
        assertEquals(0, stageOneRoi.calls, "la etapa 1 no interviene en la inspección")
    }

    @Test
    fun `la latencia extremo a extremo incluye la ROI y se reporta al oyente (#103)`() = runTest {
        var now = 0L
        val reported = mutableListOf<Long>()
        val slowRoi = object : RoiStrategy {
            override suspend fun findRegion(frame: PixelAccessFrame): CropRegion {
                now += 40 // la ROI cuesta 40 ms del presupuesto extremo a extremo
                return CropRegion.centeredSquare(frame.width, frame.height)
            }
        }
        val subject = LiteRtWasteClassifier(
            materialEngine = ScriptedEngine(materialScores(WasteMaterial.PLASTIC, 0.9f)),
            materialSpec = ModelCatalog.MATERIAL_LOW,
            contaminationEngine = ScriptedEngine(floatArrayOf(0.8f, 0.2f)),
            contaminationSpec = ModelCatalog.CONTAMINATION,
            roiStrategy = slowRoi,
            onClassifyLatencyMillis = { reported += it },
            classifyLatency = LatencyMeter(clock = { now }),
        )

        subject.classify(frame)
        subject.classify(frame)
        subject.inspectContamination(frame)

        assertEquals(2, reported.size, "clasificar reporta; la inspección no")
        assertEquals(40L, reported[0], "la señal reportada incluye el coste de la ROI")
    }

    @Test
    fun `cada etapa registra su latencia por separado`() = runTest {
        val subject = classifier(
            material = ScriptedEngine(materialScores(WasteMaterial.PLASTIC, 0.9f)),
            contamination = ScriptedEngine(floatArrayOf(0.3f, 0.7f)),
        )

        subject.classify(frame)
        subject.classify(frame)
        subject.inspectContamination(frame)

        assertEquals(2L, subject.materialLatency.sampleCount)
        assertEquals(1L, subject.contaminationLatency.sampleCount)
    }

    @Test
    fun `la confianza queda acotada al rango 0 a 1 aunque la decuantizacion se pase`() = runTest {
        val scores = materialScores(WasteMaterial.METAL, 1.2f)
        val subject = classifier(ScriptedEngine(scores))

        val result = subject.classify(frame)

        assertEquals(WasteMaterial.METAL, result.material)
        assertEquals(1.0f, result.confidence)
    }
}
