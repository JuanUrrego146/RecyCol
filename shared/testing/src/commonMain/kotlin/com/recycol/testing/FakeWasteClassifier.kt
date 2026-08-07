package com.recycol.testing

import com.recycol.domain.model.ClassificationResult
import com.recycol.domain.model.ContaminationResult
import com.recycol.domain.model.ContaminationState
import com.recycol.domain.model.ImageFrame
import com.recycol.domain.model.WasteMaterial

/**
 * Fake determinista de `WasteClassifier` (implementación real: agente EDGE).
 *
 * Devuelve siempre los resultados fijados en el constructor, sin aleatoriedad
 * ni estado interno: mismas entradas, mismas salidas, en cualquier orden de
 * ejecución de las pruebas.
 */
class FakeWasteClassifier(
    private val classification: ClassificationResult =
        ClassificationResult(WasteMaterial.PLASTIC, confidence = 0.92f),
    private val contamination: ContaminationResult =
        ContaminationResult(ContaminationState.CLEAN, confidence = 0.90f),
) : com.recycol.domain.port.WasteClassifier {

    override suspend fun classify(frame: ImageFrame): ClassificationResult = classification

    override suspend fun inspectContamination(frame: ImageFrame): ContaminationResult = contamination
}
