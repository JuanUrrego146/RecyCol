package com.botabien.android.inference

import kotlin.math.exp

/**
 * Utilidades de post-procesamiento sobre las puntuaciones del modelo.
 */
internal object Scores {

    /** Índice de la puntuación máxima. Exige un arreglo no vacío. */
    fun argmax(scores: FloatArray): Int {
        require(scores.isNotEmpty()) { "El modelo devolvió un tensor de salida vacío." }
        var best = 0
        for (index in 1 until scores.size) {
            if (scores[index] > scores[best]) best = index
        }
        return best
    }

    /**
     * Convierte las puntuaciones a probabilidades. Si el modelo ya emite
     * softmax se devuelven tal cual; si emite logits se aplica softmax con
     * estabilización numérica (resta del máximo).
     */
    fun toProbabilities(scores: FloatArray, outputsProbabilities: Boolean): FloatArray {
        if (outputsProbabilities) return scores
        val max = scores.max()
        val exponentials = FloatArray(scores.size) { exp((scores[it] - max).toDouble()).toFloat() }
        val sum = exponentials.sum()
        return FloatArray(scores.size) { exponentials[it] / sum }
    }
}
