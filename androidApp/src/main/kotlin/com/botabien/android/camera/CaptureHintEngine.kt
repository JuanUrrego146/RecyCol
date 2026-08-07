package com.botabien.android.camera

import com.botabien.domain.model.CaptureHint

/**
 * Motor de indicaciones con política anti-saturación (RF-017, RF-018, S13).
 *
 * Trabaja sobre el contrato del dominio: recibe las indicaciones que emite
 * `ClassifyWasteUseCase` en `ClassificationOutcome.hints` y decide cuál (una
 * sola) debe estar visible. El caso de uso delega explícitamente esta política
 * en este motor; la UI (agente FRONT) solo pinta el resultado.
 *
 * Reglas que implementa, en este orden:
 * 1. Si la confianza de clasificación ya es suficiente, no se molesta al
 *    usuario: ninguna indicación de calidad, aunque haya métricas degradadas.
 * 2. Sin indicaciones activas, la vigente se retira inmediatamente.
 * 3. Solo se muestra una a la vez: la causa dominante según [PRIORITY]
 *    (la causa raíz primero: un lente sucio degrada todo lo demás).
 * 4. Entre una indicación nueva y la siguiente pasa al menos
 *    [minIntervalMillis]; mantener visible la vigente no cuenta como nueva, y
 *    un cambio de causa también respeta el intervalo (mientras tanto se
 *    conserva la vigente si sigue activa, o no se muestra nada).
 *
 * [CaptureHint.POINT_INSIDE] no es una indicación de calidad sino el arranque
 * del flujo de inspección: la gestiona [DirectedCaptureController] (S14) con
 * la solicitud del perfil, y este motor la ignora.
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

    private var currentHint: CaptureHint? = null
    private var lastShownAtMillis: Long = NEVER

    /**
     * Evalúa el estado actual y devuelve la indicación que debe verse en
     * pantalla, o `null` si no debe verse ninguna.
     *
     * @param hints indicaciones activas del último `ClassificationOutcome`.
     * @param classificationConfidence confianza de la última clasificación, o
     *   `null` si todavía no hay resultado.
     * @param nowMillis reloj monótono en milisegundos.
     */
    fun evaluate(
        hints: List<CaptureHint>,
        classificationConfidence: Float?,
        nowMillis: Long,
    ): CaptureHint? {
        if (classificationConfidence != null && classificationConfidence >= sufficientConfidence) {
            currentHint = null
            return null
        }

        val active = PRIORITY.filter { it in hints }
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

    companion object {
        /**
         * Prioridad de las indicaciones de calidad, causa raíz primero.
         * [CaptureHint.POINT_INSIDE] no aparece: no es una degradación de
         * captura sino un paso del flujo de inspección (S14).
         */
        val PRIORITY: List<CaptureHint> = listOf(
            CaptureHint.CLEAN_LENS,
            CaptureHint.MORE_LIGHT,
            CaptureHint.MOVE_CLOSER,
            CaptureHint.CENTER_OBJECT,
        )

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
