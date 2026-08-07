package com.recycol.android.camera

/**
 * Umbrales calibrados de las heurísticas de calidad (RF-015).
 *
 * Calibración original: conjunto sintético de frames de prueba en
 * `androidApp/src/test/.../SyntheticFrames.kt` — texturas nítidas frente a sus
 * versiones suavizadas, y niveles de gris controlados.
 *
 * **Recalibrados con capturas reales (S41, Galaxy A35).** El conjunto
 * sintético usa ajedrezados de alto contraste sobre fondo liso, que concentran
 * en el objeto muchísima más energía de bordes que cualquier escena real: con
 * los valores derivados de él, [CENTER_ENERGY_FRACTION] no llegaba a cumplirse
 * **nunca** sobre cámara real, y como un frame que no pasa la calidad no llega
 * al clasificador, la app no reconocía nada. Las cifras de abajo salen de
 * medir el flujo real de la cámara, no de estimarlas.
 *
 * Los consumen el motor de indicaciones (S13) y las pruebas. La UI no
 * interpreta números: recibe indicaciones ya decididas.
 */
object FrameQualityThresholds {

    /**
     * Varianza del Laplaciano a la que la nitidez satura en 1.0. Sobre luma
     * 0–255, una escena enfocada con textura supera con holgura este valor.
     */
    const val SHARPNESS_SATURATION_VARIANCE = 900.0

    /** Nitidez normalizada por debajo de la cual el frame se considera borroso. */
    const val BLURRY_BELOW = 0.18f

    /** Luminancia media por debajo de la cual hay luz insuficiente. */
    const val UNDEREXPOSED_BELOW = 0.16f

    /** Luminancia media por encima de la cual hay sobreexposición. */
    const val OVEREXPOSED_ABOVE = 0.92f

    /** Fracción lineal del frame que ocupa el área útil central (marco guía). */
    const val USEFUL_AREA_FRACTION = 0.6f

    /**
     * Densidad de bordes exigida en el área útil, relativa a la del frame
     * entero. Con 1.0 se pediría que el centro tuviese tanto detalle por píxel
     * como la media; con 0.75, algo menos.
     *
     * Medido sobre cámara real: un objeto bien encuadrado y enfocado da entre
     * 0,30 y 0,45 de fracción de energía, con el área útil ocupando el 36 % de
     * la superficie — o sea, una densidad relativa que ronda 1,0 y baja de ahí
     * con objetos poco texturados (un vaso transparente apenas tiene bordes
     * propios, y el fondo casi siempre tiene más). El 0,5 de fracción anterior
     * exigía una densidad de 1,39: inalcanzable fuera del conjunto sintético.
     */
    const val CENTER_ENERGY_DENSITY = 0.75

    /**
     * Fracción mínima de energía de bordes en el área útil, derivada de
     * [CENTER_ENERGY_DENSITY] y del tamaño del área: si el área útil cambia,
     * el umbral se ajusta solo en lugar de quedarse obsoleto.
     */
    val CENTER_ENERGY_FRACTION: Double =
        USEFUL_AREA_FRACTION.toDouble() * USEFUL_AREA_FRACTION * CENTER_ENERGY_DENSITY

    /**
     * Energía de bordes total mínima para que el encuadre sea evaluable; por
     * debajo, la escena es plana (pared, tapa del lente) y no hay objeto.
     */
    const val MIN_TOTAL_EDGE_ENERGY = 30_000.0
}
