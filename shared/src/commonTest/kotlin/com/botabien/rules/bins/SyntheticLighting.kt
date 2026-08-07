package com.botabien.rules.bins

import kotlin.math.roundToInt

/**
 * Utilidades de iluminación sintética para evaluar el escaneo de canecas bajo
 * condiciones de luz variables (criterio de S34) de forma determinista.
 */
object SyntheticLighting {

    /** Luz neutra: el color no cambia. */
    val NEUTRAL: (Int, Int, Int) -> Triple<Int, Int, Int> = { r, g, b -> Triple(r, g, b) }

    /** Luz cálida de interior: refuerza rojos y apaga azules. */
    val WARM: (Int, Int, Int) -> Triple<Int, Int, Int> = { r, g, b ->
        Triple(scale(r, 1.05f), scale(g, 0.95f), scale(b, 0.8f))
    }

    /**
     * Luz tenue: todos los canales caen a un 70 %. La autoexposición de la
     * cámara evita caídas mayores sostenidas; una escena más oscura debe
     * resolverse con la indicación de «más luz» del análisis de calidad.
     */
    val DIM: (Int, Int, Int) -> Triple<Int, Int, Int> = { r, g, b ->
        Triple(scale(r, 0.7f), scale(g, 0.7f), scale(b, 0.7f))
    }

    /** Luz fría brillante: refuerza azules levemente. */
    val COOL: (Int, Int, Int) -> Triple<Int, Int, Int> = { r, g, b ->
        Triple(scale(r, 0.9f), scale(g, 0.95f), scale(b, 1.05f))
    }

    /** Todas las condiciones evaluadas, con nombre para los mensajes de fallo. */
    val ALL: Map<String, (Int, Int, Int) -> Triple<Int, Int, Int>> = mapOf(
        "neutra" to NEUTRAL,
        "cálida" to WARM,
        "tenue" to DIM,
        "fría" to COOL,
    )

    /** Aplica una condición de luz a un color `#RRGGBB` y devuelve `#RRGGBB`. */
    fun applyToHex(hex: String, light: (Int, Int, Int) -> Triple<Int, Int, Int>): String {
        val (r, g, b) = light(
            hex.substring(1, 3).toInt(16),
            hex.substring(3, 5).toInt(16),
            hex.substring(5, 7).toInt(16),
        )
        return ColorSpace.toHex(r, g, b)
    }

    /** Empaqueta un color `#RRGGBB` como ARGB opaco bajo una condición de luz. */
    fun argbUnder(hex: String, light: (Int, Int, Int) -> Triple<Int, Int, Int>): Int {
        val (r, g, b) = light(
            hex.substring(1, 3).toInt(16),
            hex.substring(3, 5).toInt(16),
            hex.substring(5, 7).toInt(16),
        )
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun scale(component: Int, factor: Float): Int =
        (component * factor).roundToInt().coerceIn(0, 255)
}
