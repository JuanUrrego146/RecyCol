package com.botabien.android.camera

import com.botabien.domain.model.ContaminationResult
import com.botabien.domain.model.ImageFrame
import com.botabien.domain.model.InspectionRule
import com.botabien.domain.port.FrameQualityAnalyzer
import com.botabien.domain.port.WasteClassifier

/**
 * Controlador de la captura dirigida para inspección de contaminación
 * (RF-020, CUS-004/CUS-005, S14).
 *
 * Cuando un material tiene [InspectionRule], el flujo de clasificación pide al
 * usuario una vista del interior o de la superficie crítica. Este controlador
 * gobierna ese modo:
 *
 * 1. [begin] publica [State.Requesting] con la `promptKey` de la regla; la UI
 *    la resuelve a texto desde recursos de cadenas (RNF-011). El primer frame
 *    fija la escena de referencia (la vista exterior actual).
 * 2. [onFrame] espera a que la escena **cambie** respecto a la referencia —
 *    señal de que el usuario reorientó la cámara — y a que el frame sea nítido
 *    y esté bien expuesto; entonces captura una instantánea y pasa a
 *    [State.Captured]. Sin el cambio de escena se capturaría la vista
 *    exterior en el primer frame bueno, que es exactamente lo que no sirve.
 * 3. Si el usuario no proporciona la toma en [timeoutMillis], pasa a
 *    [State.NotProvided]: el llamador resuelve con la ruta conservadora
 *    (`ContaminationState.UNKNOWN` hacia el motor de reglas).
 *
 * El reloj entra por parámetro: el controlador es determinista y probable en
 * JVM. No es hilo-seguro: se usa desde el hilo del analizador.
 */
class DirectedCaptureController(
    private val qualityAnalyzer: FrameQualityAnalyzer,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val minAimMillis: Long = DEFAULT_MIN_AIM_MILLIS,
) {

    /** Estados del modo de captura dirigida. */
    sealed interface State {

        /** No hay inspección en curso. */
        data object Inactive : State

        /**
         * Se está pidiendo la vista al usuario. [promptKey] viene del perfil
         * normativo y la UI la traduce vía recursos de cadenas.
         */
        data class Requesting(val promptKey: String) : State

        /** Vista capturada, lista para entregarse al puerto de contaminación. */
        data class Captured(val frame: ImageFrame) : State

        /**
         * El usuario no proporcionó la toma dentro del plazo: aplicar la ruta
         * conservadora del perfil.
         */
        data class NotProvided(val promptKey: String) : State
    }

    var state: State = State.Inactive
        private set

    private var rule: InspectionRule? = null
    private var startedAtMillis = 0L
    private val referenceGrid = FloatArray(LumaGrid.COLUMNS * LumaGrid.ROWS)
    private val currentGrid = FloatArray(LumaGrid.COLUMNS * LumaGrid.ROWS)
    private var hasReference = false

    /** Inicia la captura dirigida para [inspectionRule]. */
    fun begin(inspectionRule: InspectionRule, nowMillis: Long) {
        rule = inspectionRule
        startedAtMillis = nowMillis
        hasReference = false
        state = State.Requesting(inspectionRule.promptKey)
    }

    /** Cancela la inspección en curso y vuelve a [State.Inactive]. */
    fun cancel() {
        rule = null
        hasReference = false
        state = State.Inactive
    }

    /**
     * Procesa el siguiente frame del flujo mientras la inspección está activa.
     * Devuelve el estado vigente tras evaluarlo.
     */
    fun onFrame(frame: LumaImageFrame, nowMillis: Long): State {
        val current = state
        if (current !is State.Requesting) return current
        val activeRule = rule ?: return current

        if (nowMillis - startedAtMillis >= timeoutMillis) {
            state = State.NotProvided(activeRule.promptKey)
            return state
        }

        LumaGrid.cellMeans(frame, currentGrid)
        if (!hasReference) {
            currentGrid.copyInto(referenceGrid)
            hasReference = true
            return current
        }

        val aimedLongEnough = nowMillis - startedAtMillis >= minAimMillis
        val sceneChanged =
            LumaGrid.meanAbsoluteDifference(currentGrid, referenceGrid) >= SCENE_CHANGE_THRESHOLD
        if (aimedLongEnough && sceneChanged && isUsable(frame)) {
            state = State.Captured(snapshot(frame))
        }
        return state
    }

    /** Un frame es utilizable para inspección si está nítido y bien expuesto. */
    private fun isUsable(frame: LumaImageFrame): Boolean {
        val quality = qualityAnalyzer.analyze(frame)
        return quality.sharpness >= FrameQualityThresholds.BLURRY_BELOW &&
            quality.luminance > FrameQualityThresholds.UNDEREXPOSED_BELOW &&
            quality.luminance < FrameQualityThresholds.OVEREXPOSED_ABOVE
    }

    /**
     * Copia defensiva del frame capturado: los búferes del flujo continuo se
     * reutilizan en anillo y el frame dirigido debe seguir siendo válido hasta
     * entregarse al puerto de contaminación. Es una copia única en memoria de
     * proceso; no se persiste ni se registra (RNF-012).
     */
    private fun snapshot(frame: LumaImageFrame): LumaImageFrame = LumaImageFrame(
        width = frame.width,
        height = frame.height,
        timestampMillis = frame.timestampMillis,
        luma = frame.luma.copyOf(),
        rotationDegrees = frame.rotationDegrees,
    )

    companion object {
        /** Plazo para que el usuario proporcione la vista antes de degradar a ruta conservadora. */
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L

        /** Tiempo mínimo de reorientación: evita capturar la vista exterior de inmediato. */
        const val DEFAULT_MIN_AIM_MILLIS = 800L

        /**
         * Diferencia media por celda (niveles de luma) respecto a la escena de
         * referencia que cuenta como reorientación de la cámara.
         */
        const val SCENE_CHANGE_THRESHOLD = 12f
    }
}

/**
 * Entrega el resultado de la captura dirigida al puerto de contaminación.
 *
 * Devuelve el [ContaminationResult] de la vista capturada, o `null` si la toma
 * no se proporcionó: en ese caso el llamador resuelve con
 * `ContaminationState.UNKNOWN` y el motor de reglas aplica la ruta
 * conservadora del perfil (RF-020).
 */
suspend fun DirectedCaptureController.State.deliverTo(
    classifier: WasteClassifier,
): ContaminationResult? = when (this) {
    is DirectedCaptureController.State.Captured -> classifier.inspectContamination(frame)
    else -> null
}
