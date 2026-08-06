package com.botabien.android.inference

import com.botabien.android.inference.engine.AccelerationMode
import com.botabien.android.inference.engine.InferenceEngine
import com.botabien.android.inference.engine.InferenceException
import com.botabien.android.inference.model.ModelCatalog
import com.botabien.android.inference.model.ModelOutputOrder
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.ImageFrame
import com.botabien.domain.model.WasteMaterial
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
    fun `la confianza queda acotada al rango 0 a 1 aunque la decuantizacion se pase`() = runTest {
        val scores = materialScores(WasteMaterial.METAL, 1.2f)
        val subject = classifier(ScriptedEngine(scores))

        val result = subject.classify(frame)

        assertEquals(WasteMaterial.METAL, result.material)
        assertEquals(1.0f, result.confidence)
    }
}
