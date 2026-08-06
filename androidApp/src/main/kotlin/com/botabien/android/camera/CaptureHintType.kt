package com.botabien.android.camera

/**
 * Indicaciones de asistencia a la captura (RF-017, CUS-004), en orden de
 * prioridad descendente: ante varias degradaciones simultáneas se muestra solo
 * la primera de esta lista (RF-018: una indicación a la vez).
 *
 * El orden responde a la causa raíz: un lente sucio degrada todo lo demás; la
 * luz gobierna la nitidez medible; el encuadre solo importa cuando ya se ve
 * bien. La capa de UI (agente FRONT) traduce cada valor a su recurso de cadena
 * e icono; aquí no hay ningún texto visible (RNF-011).
 */
enum class CaptureHintType {

    /** El lente tiene suciedad persistente: sugerir limpiarlo. */
    CLEAN_LENS,

    /** Luz insuficiente: sugerir más luz. */
    MORE_LIGHT,

    /** Sobreexposición: sugerir apartarse de la fuente de luz. */
    TOO_BRIGHT,

    /** Frame borroso: sugerir sostener firme o acercarse despacio. */
    HOLD_STEADY,

    /** El objeto no está dentro del área útil: sugerir centrarlo. */
    CENTER_OBJECT,
}
