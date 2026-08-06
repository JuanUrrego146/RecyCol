package com.botabien.android.inference

import com.botabien.android.inference.engine.InferenceEngine
import com.botabien.android.inference.engine.InferenceException
import com.botabien.android.inference.frame.requirePixelAccess
import com.botabien.android.inference.image.FramePreprocessor
import com.botabien.android.inference.model.ModelOutputOrder
import com.botabien.android.inference.model.ModelSpec
import com.botabien.domain.model.ClassificationResult
import com.botabien.domain.model.ContaminationResult
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.ImageFrame
import com.botabien.domain.port.WasteClassifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación del puerto [WasteClassifier] sobre LiteRT (RF-011, CUS-003).
 *
 * Pipeline de dos etapas, 100 % local (RNF-002): la etapa 1 clasifica el
 * material y la etapa 2 inspecciona contaminación sobre una toma dirigida.
 * Devuelve siempre un material, nunca una caneca (invariante 2); la caneca la
 * decide `RuleEngine` contra el perfil normativo activo.
 *
 * Mientras el agente ML no publique el modelo de contaminación (S26/S27), el
 * motor de la etapa 2 puede ser nulo: en ese caso la inspección devuelve
 * [ContaminationState.UNKNOWN] con confianza 0 y el motor de reglas aplica su
 * tratamiento conservador. La etapa se completa en S19.
 *
 * @param materialEngine motor de la etapa 1, con respaldo en CPU ya resuelto.
 * @param materialSpec spec del modelo de material activo (según gama).
 * @param contaminationEngine motor de la etapa 2, o nulo si aún no hay modelo.
 * @param contaminationSpec spec del modelo de contaminación.
 * @param dispatcher dispatcher donde corre la inferencia; nunca el hilo principal.
 */
class LiteRtWasteClassifier(
    private val materialEngine: InferenceEngine,
    private val materialSpec: ModelSpec,
    private val contaminationEngine: InferenceEngine?,
    private val contaminationSpec: ModelSpec,
    private val preprocessor: FramePreprocessor = FramePreprocessor(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : WasteClassifier {

    override suspend fun classify(frame: ImageFrame): ClassificationResult =
        withContext(dispatcher) {
            val input = preprocessor.preprocess(frame.requirePixelAccess(), materialSpec)
            val probabilities = Scores.toProbabilities(
                materialEngine.run(input),
                materialSpec.outputsProbabilities,
            )
            val materials = ModelOutputOrder.MATERIALS
            if (probabilities.size != materials.size) {
                throw InferenceException(
                    "El modelo ${materialSpec.assetFileName} devuelve ${probabilities.size} " +
                        "clases y la taxonomía declara ${materials.size}: " +
                        "modelo y WasteMaterial están desincronizados."
                )
            }
            val winner = Scores.argmax(probabilities)
            ClassificationResult(
                material = materials[winner],
                confidence = probabilities[winner].coerceIn(0f, 1f),
            )
        }

    override suspend fun inspectContamination(frame: ImageFrame): ContaminationResult =
        withContext(dispatcher) {
            val engine = contaminationEngine
                ?: return@withContext ContaminationResult(ContaminationState.UNKNOWN, 0f)

            val input = preprocessor.preprocess(frame.requirePixelAccess(), contaminationSpec)
            val probabilities = Scores.toProbabilities(
                engine.run(input),
                contaminationSpec.outputsProbabilities,
            )
            val states = ModelOutputOrder.CONTAMINATION
            if (probabilities.size != states.size) {
                throw InferenceException(
                    "El modelo ${contaminationSpec.assetFileName} devuelve " +
                        "${probabilities.size} clases y la etapa de contaminación espera ${states.size}."
                )
            }
            val winner = Scores.argmax(probabilities)
            ContaminationResult(
                state = states[winner],
                confidence = probabilities[winner].coerceIn(0f, 1f),
            )
        }
}
