package com.recycol.rules.bins

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Color en el espacio HSV, la representación de trabajo del escaneo de canecas.
 *
 * El matiz es estable frente a cambios de brillo, que es lo que hace al
 * emparejamiento robusto a iluminación variable (RF-005): una caneca azul bajo
 * luz tenue sigue teniendo matiz azul aunque su valor caiga.
 *
 * @property hue matiz en grados `[0, 360)`.
 * @property saturation saturación en `[0, 1]`.
 * @property value valor (brillo) en `[0, 1]`.
 */
data class Hsv(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

/**
 * Conversión y comparación de colores para el escaneo de canecas (CUS-002).
 * Aritmética pura, sin ninguna dependencia de plataforma (RNF-005).
 */
object ColorSpace {

    /** Saturación bajo la cual un color se trata como acromático (blanco, gris, negro). */
    const val ACHROMATIC_SATURATION: Float = 0.25f

    /** Distancia entre un color acromático y uno cromático: incomparables. */
    const val INCOMPARABLE: Float = 1f

    private const val HUE_WEIGHT = 0.6f
    private const val SATURATION_WEIGHT = 0.15f
    private const val VALUE_WEIGHT = 0.25f

    /** Convierte un color ARGB empaquetado a HSV; el canal alfa se ignora. */
    fun fromArgb(argb: Int): Hsv = fromRgb(
        red = (argb shr 16) and 0xFF,
        green = (argb shr 8) and 0xFF,
        blue = argb and 0xFF,
    )

    /** Convierte un color `#RRGGBB` (formato de los perfiles) a HSV. */
    fun fromHex(hex: String): Hsv {
        require(hex.length == 7 && hex[0] == '#') { "Color fuera del formato #RRGGBB: «$hex»" }
        return fromRgb(
            red = hex.substring(1, 3).toInt(16),
            green = hex.substring(3, 5).toInt(16),
            blue = hex.substring(5, 7).toInt(16),
        )
    }

    /** Formatea componentes RGB `[0, 255]` como `#RRGGBB`. */
    fun toHex(red: Int, green: Int, blue: Int): String =
        "#${hexByte(red)}${hexByte(green)}${hexByte(blue)}"

    fun fromRgb(red: Int, green: Int, blue: Int): Hsv {
        val r = red / 255f
        val g = green / 255f
        val b = blue / 255f
        val maxComponent = max(r, max(g, b))
        val minComponent = min(r, min(g, b))
        val delta = maxComponent - minComponent

        val hue = when {
            delta == 0f -> 0f
            maxComponent == r -> 60f * (((g - b) / delta) + 6f) % 360f
            maxComponent == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        val saturation = if (maxComponent == 0f) 0f else delta / maxComponent

        return Hsv(hue = hue, saturation = saturation, value = maxComponent)
    }

    fun isAchromatic(color: Hsv): Boolean = color.saturation < ACHROMATIC_SATURATION

    /**
     * Distancia perceptual sencilla entre dos colores, en `[0, 1]`.
     *
     * - Dos acromáticos se comparan solo por valor: blanco, gris y negro se
     *   distinguen por brillo, no por matiz.
     * - Un acromático y un cromático son [INCOMPARABLE].
     * - Dos cromáticos ponderan matiz (dominante), saturación y valor; el peso
     *   bajo del valor absorbe los cambios de iluminación.
     */
    fun distance(a: Hsv, b: Hsv): Float {
        val aAchromatic = isAchromatic(a)
        val bAchromatic = isAchromatic(b)
        return when {
            aAchromatic && bAchromatic -> abs(a.value - b.value)
            aAchromatic != bAchromatic -> INCOMPARABLE
            else -> HUE_WEIGHT * (hueDelta(a.hue, b.hue) / 180f) +
                SATURATION_WEIGHT * abs(a.saturation - b.saturation) +
                VALUE_WEIGHT * abs(a.value - b.value)
        }
    }

    /** Distancia angular mínima entre dos matices, en grados `[0, 180]`. */
    fun hueDelta(a: Float, b: Float): Float {
        val delta = abs(a - b) % 360f
        return if (delta > 180f) 360f - delta else delta
    }

    private fun hexByte(component: Int): String =
        component.coerceIn(0, 255).toString(16).padStart(2, '0').uppercase()
}
