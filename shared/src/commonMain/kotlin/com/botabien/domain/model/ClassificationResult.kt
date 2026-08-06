package com.botabien.domain.model

/**
 * Resultado de la primera etapa del pipeline: material predicho y su confianza.
 *
 * El clasificador devuelve siempre un material, nunca una caneca; la caneca la
 * decide el motor de reglas contra el perfil normativo activo.
 *
 * @property material material predicho por el modelo.
 * @property confidence confianza de la predicción, en el rango `[0.0, 1.0]`.
 */
data class ClassificationResult(
    val material: WasteMaterial,
    val confidence: Float,
)
