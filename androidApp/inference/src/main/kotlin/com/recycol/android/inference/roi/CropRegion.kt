package com.recycol.android.inference.roi

/**
 * Región cuadrada de interés dentro de un frame, en píxeles del frame.
 *
 * Siempre cabe completa en el frame que la produjo: las fábricas de esta clase
 * recortan a los límites antes de construirla.
 *
 * @property left borde izquierdo, en píxeles.
 * @property top borde superior, en píxeles.
 * @property size lado del cuadrado, en píxeles.
 */
data class CropRegion(
    val left: Int,
    val top: Int,
    val size: Int,
) {
    init {
        require(size > 0) { "La región de recorte debe tener lado positivo." }
        require(left >= 0 && top >= 0) { "La región de recorte no puede empezar fuera del frame." }
    }

    companion object {

        /** Cuadrado máximo centrado: el recorte clásico cuando no hay detector. */
        fun centeredSquare(frameWidth: Int, frameHeight: Int): CropRegion {
            val side = minOf(frameWidth, frameHeight)
            return CropRegion(
                left = (frameWidth - side) / 2,
                top = (frameHeight - side) / 2,
                size = side,
            )
        }

        /**
         * Cuadrado de lado `fraction * min(ancho, alto)` centrado en el frame.
         * Es la geometría del marco guía fijo de gama baja (RF-010).
         */
        fun centeredFraction(frameWidth: Int, frameHeight: Int, fraction: Float): CropRegion {
            require(fraction > 0f && fraction <= 1f) { "La fracción debe estar en (0, 1]." }
            val side = (minOf(frameWidth, frameHeight) * fraction).toInt().coerceAtLeast(1)
            return CropRegion(
                left = (frameWidth - side) / 2,
                top = (frameHeight - side) / 2,
                size = side,
            )
        }

        /**
         * Cuadrado centrado en `(centerX, centerY)` con el lado pedido,
         * desplazado y recortado lo necesario para caber en el frame.
         */
        fun centeredAt(
            centerX: Int,
            centerY: Int,
            side: Int,
            frameWidth: Int,
            frameHeight: Int,
        ): CropRegion {
            val boundedSide = side.coerceIn(1, minOf(frameWidth, frameHeight))
            val left = (centerX - boundedSide / 2).coerceIn(0, frameWidth - boundedSide)
            val top = (centerY - boundedSide / 2).coerceIn(0, frameHeight - boundedSide)
            return CropRegion(left, top, boundedSide)
        }
    }
}
