package com.botabien.android.inference

import com.botabien.android.inference.engine.InferenceEngine
import com.botabien.android.inference.engine.InferenceException
import com.botabien.android.inference.frame.requirePixelAccess
import com.botabien.android.inference.image.FramePreprocessor
import com.botabien.android.inference.model.ModelOutputOrder
import com.botabien.android.inference.model.ModelSpec
import com.botabien.android.inference.roi.GuideFrameRoi
import com.botabien.android.inference.roi.LatencyMeter
import com.botabien.android.inference.roi.RoiStrategy
import com.botabien.domain.model.ClassificationResult
import com.botabien.domain.model.ContaminationResult
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.ImageFrame
import com.botabien.domain.port.WasteClassifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación del puerto [WasteClassifier] sobre LiteRT (RF-011, RF-021,
 * CUS-003, CUS-005).
 *
 * Pipeline de dos etapas, 100 % local (RNF-002): la etapa 1 clasifica el
 * material sobre el recorte del objeto (RF-010) y la etapa 2 inspecciona
 * contaminación sobre el recorte de la toma dirigida (RF-021). Devuelve
 * siempre un material, nunca una caneca (invariante 2); la caneca la decide
 * `RuleEngine` contra el perfil normativo activo.
 *
 * Mientras el agente ML no publique el modelo de contaminación (S26/S27), el
 * motor de la etapa 2 puede ser nulo: en ese caso la inspección devuelve
 * [ContaminationState.UNKNOWN] con confianza 0, que el flujo de clasificación
 * trata como no concluyente (respuesta conservadora, RF-023).
 *
 * La latencia de cada etapa queda registrada en [materialLatency] y
 * [contaminationLatency]; solo números, jamás frames (RNF-012). El banco de
 * S20/S41 y la degradación de gama consumen esas cifras.
 *
 * @param materialEngine motor de la etapa 1, con respaldo en CPU ya resuelto.
 * @param materialSpec spec del modelo de material activo (según gama).
 * @param contaminationEngine motor de la etapa 2, o nulo si aún no hay modelo.
 * @param contaminationSpec spec del modelo de contaminación.
 * @param roiStrategy aislamiento del objeto en la etapa 1 (RF-010):
 *   detector en gama media/alta, marco guía fijo en gama baja.
 * @param contaminationRoi recorte de la etapa 2: la toma interior ya viene
 *   dirigida por el usuario, así que el marco guía es el recorte correcto;
 *   el detector de objeto no aplica a una vista de interior.
 * @param dispatcher dispatcher donde corre la inferencia; nunca el hilo principal.
 */
class LiteRtWasteClassifier(
    private val materialEngine: InferenceEngine,
    private val materialSpec: ModelSpec,
    private val contaminationEngine: InferenceEngine?,
    private val contaminationSpec: ModelSpec,
    private val roiStrategy: RoiStrategy = GuideFrameRoi(),
    private val contaminationRoi: RoiStrategy = GuideFrameRoi(),
    private val preprocessor: FramePreprocessor = FramePreprocessor(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onMaterialLatencyMillis: ((Long) -> Unit)? = null,
) : WasteClassifier {

    /** Latencia de la etapa 1 (preprocesado + inferencia de material). */
    val materialLatency = LatencyMeter()

    /** Latencia de la etapa 2 (preprocesado + inferencia de contaminación). */
    val contaminationLatency = LatencyMeter()

    override suspend fun classify(frame: ImageFrame): ClassificationResult =
        withContext(dispatcher) {
            val pixelFrame = frame.requirePixelAccess()
            val region = roiStrategy.findRegion(pixelFrame)
            val probabilities = materialLatency.measure {
                val input = preprocessor.preprocess(pixelFrame, materialSpec, region)
                Scores.toProbabilities(
                    materialEngine.run(input),
                    materialSpec.outputsProbabilities,
                )
            }
            // Señal para la degradación de gama en uso (S17): la etapa de
            // material domina el presupuesto de latencia de la clasificación.
            materialLatency.lastMillis?.let { onMaterialLatencyMillis?.invoke(it) }
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

            val pixelFrame = frame.requirePixelAccess()
            val region = contaminationRoi.findRegion(pixelFrame)
            val probabilities = contaminationLatency.measure {
                val input = preprocessor.preprocess(pixelFrame, contaminationSpec, region)
                Scores.toProbabilities(
                    engine.run(input),
                    contaminationSpec.outputsProbabilities,
                )
            }
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
