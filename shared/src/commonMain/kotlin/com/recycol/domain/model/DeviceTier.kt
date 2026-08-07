package com.recycol.domain.model

/**
 * Gama del dispositivo, resuelta al arrancar combinando capacidades declaradas
 * y un micro-benchmark de latencia real (CUS-008). La clasificación por cámara
 * funciona en las tres gamas sin excepción; lo que se degrada son las
 * funciones auxiliares.
 */
enum class DeviceTier {
    LOW,
    MID,
    HIGH,
}

/**
 * Funciones costosas cuya activación depende de la gama del dispositivo.
 * Ningún módulo asume NPU, GPU ni memoria: antes de activar cualquiera de
 * estas funciones se consulta `DeviceTierPolicy`.
 */
enum class Feature {
    /** Clasificación continua sobre el flujo de cámara (vs. bajo demanda con botón). */
    CONTINUOUS_CLASSIFICATION,

    /** Detección y recorte del objeto en el encuadre (vs. marco guía fijo). */
    OBJECT_DETECTION,

    /** Etapa de contaminación automática (vs. solo bajo demanda de la regla). */
    AUTOMATIC_CONTAMINATION_INSPECTION,

    /** Escaneo continuo de canecas (vs. foto única). */
    CONTINUOUS_BIN_SCAN,

    /** Análisis completo de calidad de imagen (vs. solo nitidez y luz). */
    FULL_FRAME_QUALITY_ANALYSIS,
}
