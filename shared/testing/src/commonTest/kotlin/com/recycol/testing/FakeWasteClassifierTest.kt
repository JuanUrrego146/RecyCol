package com.recycol.testing

import com.recycol.domain.model.ClassificationResult
import com.recycol.domain.model.ContaminationState
import com.recycol.domain.model.WasteMaterial
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Documenta el contrato del fake de `WasteClassifier`: respuestas fijadas por
 * constructor, idénticas en cada invocación, sin importar el frame recibido.
 */
class FakeWasteClassifierTest {

    @Test
    fun porDefectoClasificaPlasticoLimpioConAltaConfianza() = runTest {
        val classifier = FakeWasteClassifier()

        val classification = classifier.classify(StubImageFrame())
        val contamination = classifier.inspectContamination(StubImageFrame())

        assertEquals(WasteMaterial.PLASTIC, classification.material)
        assertEquals(0.92f, classification.confidence)
        assertEquals(ContaminationState.CLEAN, contamination.state)
    }

    @Test
    fun devuelveSiempreElResultadoConfiguradoIndependienteDelFrame() = runTest {
        val expected = ClassificationResult(WasteMaterial.BEVERAGE_CARTON, confidence = 0.71f)
        val classifier = FakeWasteClassifier(classification = expected)

        val first = classifier.classify(StubImageFrame(width = 100, height = 100))
        val second = classifier.classify(StubImageFrame(width = 4000, height = 3000, timestampMillis = 99L))

        assertEquals(expected, first)
        assertEquals(expected, second)
    }
}
