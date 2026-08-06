package com.botabien.android.inference

import com.botabien.domain.model.ClassificationResult
import com.botabien.domain.model.ContaminationResult
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.ImageFrame
import com.botabien.domain.model.WasteMaterial
import com.botabien.domain.port.WasteClassifier

/**
 * Clasificador provisional para cuando los modelos aún no están empaquetados.
 *
 * Los archivos `.tflite` no se versionan (se reconstruyen con el pipeline de
 * `ml/`, ver `.gitignore`), así que en un checkout limpio no hay modelos. Este
 * stub mantiene la app funcional y determinista mientras tanto: mismos valores
 * que `FakeWasteClassifier` de `shared/testing/`, sin depender de ese módulo
 * en producción. La inyección lo sustituye por [LiteRtWasteClassifier] en
 * cuanto los assets existen; no hay nada que cambiar en los consumidores.
 */
class StubWasteClassifier : WasteClassifier {

    override suspend fun classify(frame: ImageFrame): ClassificationResult =
        ClassificationResult(WasteMaterial.PLASTIC, confidence = 0.92f)

    override suspend fun inspectContamination(frame: ImageFrame): ContaminationResult =
        ContaminationResult(ContaminationState.CLEAN, confidence = 0.90f)
}
