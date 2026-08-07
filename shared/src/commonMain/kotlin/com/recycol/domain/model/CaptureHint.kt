package com.recycol.domain.model

/**
 * Indicación de captura que la aplicación puede dar al usuario (CUS-004).
 *
 * El dominio emite el concepto; la UI lo traduce a texto desde recursos de
 * cadenas (RNF-011) y aplica la política anti-saturación de indicaciones.
 */
enum class CaptureHint {
    /** El objeto se ve pequeño o lejano: acercarse. */
    MOVE_CLOSER,

    /** Luminancia insuficiente: buscar más luz. */
    MORE_LIGHT,

    /** Suciedad persistente detectada: limpiar el lente. */
    CLEAN_LENS,

    /** El objeto no está encuadrado: centrarlo. */
    CENTER_OBJECT,

    /** El material exige inspección interior: apuntar hacia adentro del objeto. */
    POINT_INSIDE,
}
