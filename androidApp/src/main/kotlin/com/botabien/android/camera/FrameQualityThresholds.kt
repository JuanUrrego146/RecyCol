package com.botabien.android.camera

/**
 * Umbrales calibrados de las heurísticas de calidad (RF-015).
 *
 * Calibración: conjunto sintético de frames de prueba en
 * `androidApp/src/test/.../SyntheticFrames.kt` — texturas nítidas frente a sus
 * versiones suavizadas, y niveles de gris controlados. Los valores separan con
 * margen los casos degradados de los aceptables en ese conjunto; la
 * recalibración con capturas reales queda trazada a la verificación en
 * dispositivo de M8 (S41).
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
     * Fracción mínima de la energía de bordes que debe caer en el área útil
     * para considerar el objeto encuadrado. El área útil ocupa el 36 % de la
     * superficie: exigir la mitad de la energía implica detalle concentrado
     * claramente en el centro.
     */
    const val CENTER_ENERGY_FRACTION = 0.5

    /**
     * Energía de bordes total mínima para que el encuadre sea evaluable; por
     * debajo, la escena es plana (pared, tapa del lente) y no hay objeto.
     */
    const val MIN_TOTAL_EDGE_ENERGY = 30_000.0
}
