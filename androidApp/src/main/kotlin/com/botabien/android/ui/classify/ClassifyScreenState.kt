package com.botabien.android.ui.classify

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.botabien.domain.model.CaptureHint
import com.botabien.domain.model.ClassificationOutcome
import com.botabien.domain.model.ImageFrame
import com.botabien.domain.model.WasteMaterial
import com.botabien.domain.usecase.ClassifyWasteUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Estado de la pantalla de clasificación (CUS-003, CUS-004). Orquesta el caso
 * de uso sobre el flujo de frames; toda la decisión —calidad, umbrales,
 * reglas, caneca— vive en `shared/domain` (invariante 4).
 *
 * Flujo de inspección interior (CUS-005): cuando el resultado trae la
 * directiva [CaptureHint.POINT_INSIDE], se da al usuario un periodo de gracia
 * para reorientar la cámara y el siguiente frame se envía a
 * [ClassifyWasteUseCase.resolveContamination] para la decisión definitiva.
 *
 * **Ese camino está desactivado mientras dure el plan B**
 * ([askUserAboutContamination]): la etapa de contaminación se entrenó con
 * suciedad sintética y marca como limpio el 98,75 % de la suciedad real, así que
 * resolverla automáticamente daba una respuesta casi siempre equivocada. Peor
 * aún, competía con la pregunta al usuario y la pisaba. Cuando la detección
 * automática funcione, basta con pasar `false` y este flujo revive intacto.
 *
 * Los frames se conflan: si el análisis va más lento que la cámara se procesa
 * siempre el más reciente y la vista en vivo jamás se bloquea (RF-009).
 */
@Stable
class ClassifyScreenState(
    private val classifyWaste: ClassifyWasteUseCase,
    private val scope: CoroutineScope,
    val hints: HintPresenter = HintPresenter(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val askUserAboutContamination: Boolean = true,
) {

    /** Último resultado con decisión o con decisión pendiente del usuario. */
    var outcome: ClassificationOutcome? by mutableStateOf(null)
        private set

    private var collectJob: Job? = null
    private var awaitingMaterial: WasteMaterial? = null
    private var awaitingSince = 0L

    /**
     * Material sobre el que el usuario ya se pronunció. Mientras el análisis
     * siga viendo lo mismo, su decisión manda; en cuanto aparece otro material,
     * se suelta y la cámara vuelve a decidir.
     */
    private var decidedByUserFor: WasteMaterial? = null

    /** Empieza a consumir el flujo de frames. Idempotente mientras corre. */
    fun start(frames: Flow<ImageFrame>) {
        if (collectJob != null) return
        collectJob = scope.launch {
            frames.conflate().collect { frame -> process(frame) }
        }
    }

    /** Deja de consumir frames; la última decisión permanece visible. */
    fun stop() {
        collectJob?.cancel()
        collectJob = null
    }

    /**
     * Aplica el resultado de una decisión del usuario —selección manual
     * (CUS-006) o respuesta a la pregunta de suciedad—: sustituye a la visible y
     * retira cualquier indicación pendiente.
     *
     * A partir de aquí **el análisis deja de sobrescribir la decisión** mientras
     * el objeto siga siendo el mismo. Sin eso, lo que el usuario acababa de
     * responder duraba lo que tardaba en llegar el siguiente fotograma —unos
     * doscientos milisegundos— y volvía a mandar la clasificación automática,
     * que además, al no conocer la contaminación, degradaba a la caneca
     * conservadora. El usuario contestaba «está limpio» y veía la caneca negra.
     */
    fun applyManualOutcome(manualOutcome: ClassificationOutcome) {
        outcome = manualOutcome
        decidedByUserFor = manualOutcome.classification?.material
        hints.offer(emptyList())
    }

    private suspend fun process(frame: ImageFrame) {
        val material = awaitingMaterial
        val result = if (material != null && clock() - awaitingSince >= INTERIOR_GRACE_MS) {
            awaitingMaterial = null
            classifyWaste.resolveContamination(material, frame)
        } else {
            classifyWaste.execute(frame)
        }

        // Con el plan B activo no se arranca la toma dirigida: la etapa de
        // contaminación no transfiere a suciedad real, así que resolverla por
        // nuestra cuenta produciría una respuesta casi siempre equivocada que
        // además pisaría la que el usuario está a punto de dar. La pantalla
        // pregunta y resuelve por selección manual (ver SoilQuestionCard).
        if (!askUserAboutContamination &&
            awaitingMaterial == null &&
            CaptureHint.POINT_INSIDE in result.hints
        ) {
            awaitingMaterial = result.classification?.material
            awaitingSince = clock()
        }

        // Si el usuario ya decidió sobre este mismo material, su respuesta
        // manda: el análisis sigue corriendo —y sus indicaciones también— pero
        // no le pisa la decisión. Al cambiar de objeto se suelta.
        val decidedMaterial = decidedByUserFor
        val stillTheSameObject = decidedMaterial != null &&
            result.classification?.material == decidedMaterial
        if (!stillTheSameObject) {
            decidedByUserFor = null
            if (result.disposal != null || result.needsUserDecision) {
                outcome = result
            }
        }
        hints.offer(result.hints)
    }

    companion object {
        /** Gracia para que el usuario reoriente la cámara hacia el interior. */
        const val INTERIOR_GRACE_MS = 1_500L
    }
}
