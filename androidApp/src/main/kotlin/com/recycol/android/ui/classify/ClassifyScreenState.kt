package com.recycol.android.ui.classify

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.recycol.domain.model.ClassificationOutcome
import com.recycol.domain.model.ImageFrame
import com.recycol.domain.model.WasteMaterial
import com.recycol.domain.usecase.StabilizedDecision
import com.recycol.domain.usecase.TrackClassificationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * Estado de la pantalla de clasificación (CUS-003, CUS-004). Bombea frames hacia
 * [TrackClassificationUseCase] y publica lo que este decida: aquí no se decide
 * nada (invariante 4).
 *
 * Ya no hay pegajosidad propia. La que había —«si el usuario decidió sobre este
 * material, su respuesta manda»— se soltaba con un único fotograma borroso,
 * porque un frame sin clasificación se leía como «otro objeto». Ahora esa
 * política vive en el estabilizador, donde un fotograma sin evidencia
 * sencillamente no vota.
 *
 * Los frames se conflan: si el análisis va más lento que la cámara se procesa
 * siempre el más reciente y la vista en vivo jamás se bloquea (RF-009).
 */
@Stable
class ClassifyScreenState(
    private val tracker: TrackClassificationUseCase,
    private val scope: CoroutineScope,
    val hints: HintPresenter = HintPresenter(),
) {

    /** Decisión visible con su identidad; `null` mientras no haya ninguna. */
    var decision: StabilizedDecision? by mutableStateOf(null)
        private set

    val outcome: ClassificationOutcome?
        get() = decision?.outcome

    private var collectJob: Job? = null

    /**
     * Hipótesis con las que sembrar la hoja de selección manual. Se consulta al
     * abrirla, no se guarda: la ventana de votación sigue moviéndose y una lista
     * congelada en el instante de la publicación tendría un solo elemento y
     * varios segundos de antigüedad.
     */
    fun candidates(): List<WasteMaterial> = tracker.candidates()

    /** Empieza a consumir el flujo de frames. Idempotente mientras corre. */
    fun start(frames: Flow<ImageFrame>) {
        if (collectJob != null) return
        collectJob = scope.launch {
            frames.conflate().collect { frame -> process(frame) }
        }
    }

    /**
     * Deja de consumir frames y olvida todo el seguimiento. La tarjeta que el
     * usuario está viendo permanece —acaba de verla— pero la evidencia no: volver
     * a la pantalla un minuto después no puede reanudar votando con lo que se vio
     * antes, ni conservar una caneca resuelta contra una disponibilidad que el
     * usuario pudo cambiar en la pantalla de escaneo.
     */
    fun stop() {
        collectJob?.cancel()
        collectJob = null
        tracker.reset()
    }

    /**
     * Selección manual o respuesta a la pregunta de suciedad (CUS-006).
     *
     * **Devuelve la decisión ya fijada**, con el epoch nuevo. La pantalla tiene
     * que registrar ese epoch, no el que había antes de llamar: fijar la decisión
     * lo incrementa, así que leerlo antes garantiza que nunca coincidan y deja el
     * guard de la pregunta de suciedad como código muerto.
     */
    fun applyManualOutcome(manualOutcome: ClassificationOutcome): StabilizedDecision {
        val fixed = tracker.applyUserDecision(manualOutcome)
        decision = fixed
        hints.offer(emptyList())
        return fixed
    }

    private suspend fun process(frame: ImageFrame) {
        val tracked = tracker.onFrame(frame)
        // Solo se reasigna cuando hay novedad: escribir el mismo valor tres veces
        // por segundo recompondría la pantalla entera para nada.
        tracked.decision?.let { decision = it }
        hints.offer(tracked.hints)
    }
}
