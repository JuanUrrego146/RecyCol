package com.botabien.testing

import com.botabien.domain.model.ClassificationResult
import com.botabien.domain.model.ContaminationResult
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.ImageFrame
import com.botabien.domain.model.WasteMaterial

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
) : com.botabien.domain.port.WasteClassifier {

    override suspend fun classify(frame: ImageFrame): ClassificationResult = classification

    override suspend fun inspectContamination(frame: ImageFrame): ContaminationResult = contamination
}
