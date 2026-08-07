package com.recycol.domain.model

/**
 * Representación neutra de un frame de cámara dentro del dominio.
 *
 * El dominio nunca ve búferes de píxeles ni tipos de plataforma: cada plataforma
 * envuelve su frame nativo (ImageProxy en Android, CVPixelBuffer en iOS) en una
 * implementación propia de esta interfaz, en su propio módulo (RNF-005).
 *
 * Invariante de privacidad (RNF-012): las implementaciones no se persisten,
 * no se serializan y no se registran en logs.
 */
interface ImageFrame {
    /** Ancho del frame en píxeles. */
    val width: Int

    /** Alto del frame en píxeles. */
    val height: Int

    /** Marca de tiempo de captura, en milisegundos desde época Unix. */
    val timestampMillis: Long
}
