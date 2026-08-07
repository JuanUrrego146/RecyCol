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
 * Los frames se conflan: si el análisis va más lento que la cámara se procesa
 * siempre el más reciente y la vista en vivo jamás se bloquea (RF-009).
 */
@Stable
class ClassifyScreenState(
    private val classifyWaste: ClassifyWasteUseCase,
    private val scope: CoroutineScope,
    val hints: HintPresenter = HintPresenter(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Último resultado con decisión o con decisión pendiente del usuario. */
    var outcome: ClassificationOutcome? by mutableStateOf(null)
        private set

    private var collectJob: Job? = null
    private var awaitingMaterial: WasteMaterial? = null
    private var awaitingSince = 0L

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
     * Aplica el resultado de una selección manual del usuario (CUS-006): la
     * decisión sustituye a la visible y retira cualquier indicación pendiente.
     */
    fun applyManualOutcome(manualOutcome: ClassificationOutcome) {
        outcome = manualOutcome
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

        if (awaitingMaterial == null && CaptureHint.POINT_INSIDE in result.hints) {
            awaitingMaterial = result.classification?.material
            awaitingSince = clock()
        }

        if (result.disposal != null || result.needsUserDecision) {
            outcome = result
        }
        hints.offer(result.hints)
    }

    companion object {
        /** Gracia para que el usuario reoriente la cámara hacia el interior. */
        const val INTERIOR_GRACE_MS = 1_500L
    }
}
