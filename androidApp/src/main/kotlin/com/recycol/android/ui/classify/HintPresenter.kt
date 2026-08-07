package com.recycol.android.ui.classify

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.recycol.domain.model.CaptureHint

/**
 * Política de presentación de indicaciones de captura (RF-017, RF-018).
 *
 * Las indicaciones son discretas por diseño: se muestra **una** a la vez,
 * una nueva no sustituye a la visible antes de [minIntervalMillis] y, cuando
 * la calidad mejora, la visible se retira solo tras [minVisibleMillis] para
 * no parpadear. La indicación de inspección interior es una directiva del
 * flujo (CUS-005), no una sugerencia: entra sin esperar el intervalo.
 *
 * El motor de indicaciones aguas arriba (frecuencia de análisis, histéresis
 * de métricas) es del agente CAM (S13); esta clase gobierna únicamente lo que
 * la pantalla muestra.
 */
@Stable
class HintPresenter(
    private val minIntervalMillis: Long = MIN_INTERVAL_MS,
    private val minVisibleMillis: Long = MIN_VISIBLE_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Indicación visible en pantalla, o `null` si no corresponde ninguna. */
    var visible: CaptureHint? by mutableStateOf(null)
        private set

    private var shownAt = Long.MIN_VALUE / 2

    /**
     * Ofrece las indicaciones pendientes de la última pasada. Una lista vacía
     * significa calidad suficiente: la indicación visible se retira cuando
     * cumple su tiempo mínimo en pantalla.
     */
    fun offer(hints: List<CaptureHint>) {
        val now = clock()
        val current = visible

        if (hints.isEmpty()) {
            if (current != null && now - shownAt >= minVisibleMillis) {
                visible = null
            }
            return
        }

        val candidate = hints.first()
        when {
            candidate == current -> Unit
            candidate == CaptureHint.POINT_INSIDE -> show(candidate, now)
            now - shownAt >= minIntervalMillis -> show(candidate, now)
            else -> Unit
        }
    }

    private fun show(hint: CaptureHint, now: Long) {
        visible = hint
        shownAt = now
    }

    companion object {
        /** Como máximo una indicación nueva cada 4 s (política anti-saturación). */
        const val MIN_INTERVAL_MS = 4_000L

        /** Permanencia mínima en pantalla antes de retirarse sin sustituta. */
        const val MIN_VISIBLE_MS = 1_500L
    }
}
