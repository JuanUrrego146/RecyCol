package com.botabien.android.camera

import com.botabien.domain.model.FrameQuality

/**
 * Motor de indicaciones con política anti-saturación (RF-017, RF-018, S13).
 *
 * Reglas que implementa, en este orden:
 * 1. Si la confianza de clasificación ya es suficiente, no se molesta al
 *    usuario: ninguna indicación, aunque haya métricas degradadas.
 * 2. Si ninguna métrica está degradada, la indicación vigente se retira
 *    inmediatamente.
 * 3. Solo se muestra una indicación a la vez: la causa dominante según el
 *    orden de prioridad de [CaptureHintType].
 * 4. Entre una indicación nueva y la siguiente debe pasar al menos
 *    [minIntervalMillis]; mantener visible la vigente no cuenta como nueva.
 *    Un cambio de causa también respeta el intervalo: mientras tanto se
 *    conserva la vigente si su causa sigue activa, o no se muestra nada.
 *
 * El reloj entra por parámetro (`nowMillis`): el motor es puro y determinista,
 * y las pruebas no dependen del reloj real.
 *
 * No es hilo-seguro: se usa desde el hilo del analizador.
 */
class CaptureHintEngine(
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
    private val sufficientConfidence: Float = DEFAULT_SUFFICIENT_CONFIDENCE,
) {

    private var currentHint: CaptureHintType? = null
    private var lastShownAtMillis: Long = NEVER

    /**
     * Evalúa el estado actual y devuelve la indicación que debe verse en
     * pantalla, o `null` si no debe verse ninguna.
     *
     * @param quality métricas del último frame analizado.
     * @param classificationConfidence confianza de la última clasificación, o
     *   `null` si todavía no hay resultado.
     * @param nowMillis reloj monótono en milisegundos.
     */
    fun evaluate(
        quality: FrameQuality,
        classificationConfidence: Float?,
        nowMillis: Long,
    ): CaptureHintType? {
        if (classificationConfidence != null && classificationConfidence >= sufficientConfidence) {
            currentHint = null
            return null
        }

        val active = activeDegradations(quality)
        if (active.isEmpty()) {
            currentHint = null
            return null
        }

        val dominant = active.first()
        val shown = currentHint
        val intervalElapsed = nowMillis - lastShownAtMillis >= minIntervalMillis

        if (shown != null && shown in active) {
            // La causa vigente sigue activa: escalar a la dominante solo si ya
            // pasó el intervalo; si no, mantenerla evita el parpadeo.
            if (dominant != shown && intervalElapsed) {
                currentHint = dominant
                lastShownAtMillis = nowMillis
            }
            return currentHint
        }

        // La causa vigente se corrigió (o no había ninguna): retirar ya y
        // mostrar la siguiente solo cuando el intervalo lo permita.
        currentHint = null
        if (intervalElapsed) {
            currentHint = dominant
            lastShownAtMillis = nowMillis
        }
        return currentHint
    }

    /** Degradaciones activas ordenadas por prioridad de [CaptureHintType]. */
    private fun activeDegradations(quality: FrameQuality): List<CaptureHintType> = buildList {
        if (quality.lensSoiling) add(CaptureHintType.CLEAN_LENS)
        if (quality.luminance < FrameQualityThresholds.UNDEREXPOSED_BELOW) {
            add(CaptureHintType.MORE_LIGHT)
        }
        if (quality.luminance > FrameQualityThresholds.OVEREXPOSED_ABOVE) {
            add(CaptureHintType.TOO_BRIGHT)
        }
        if (quality.sharpness < FrameQualityThresholds.BLURRY_BELOW) {
            add(CaptureHintType.HOLD_STEADY)
        }
        if (!quality.objectCentered) add(CaptureHintType.CENTER_OBJECT)
    }

    companion object {
        /** Intervalo mínimo entre indicaciones nuevas consecutivas. */
        const val DEFAULT_MIN_INTERVAL_MILLIS = 3_000L

        /**
         * Confianza a partir de la cual las indicaciones se suprimen: si el
         * clasificador ya está seguro, molestar no aporta (RF-018). El umbral
         * definitivo de producto lo gobierna QA en S39; este valor es el
         * predeterminado del motor.
         */
        const val DEFAULT_SUFFICIENT_CONFIDENCE = 0.75f

        private const val NEVER = Long.MIN_VALUE / 2
    }
}
