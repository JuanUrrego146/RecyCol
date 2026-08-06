package com.botabien.domain.model

/**
 * Caneca detectada por cámara durante el escaneo del entorno (CUS-002).
 *
 * El detector reporta color y confianza; el emparejamiento con las
 * [BinDefinition] del perfil activo ocurre en la capa de aplicación,
 * nunca dentro del detector.
 *
 * @property colorHex color dominante detectado, en formato `#RRGGBB`.
 * @property confidence confianza de la detección, en el rango `[0.0, 1.0]`.
 */
data class DetectedBin(
    val colorHex: String,
    val confidence: Float,
)
