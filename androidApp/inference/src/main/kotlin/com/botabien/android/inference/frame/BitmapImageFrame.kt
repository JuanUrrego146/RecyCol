package com.botabien.android.inference.frame

import android.graphics.Bitmap

/**
 * [PixelAccessFrame] respaldado por un [Bitmap] en memoria.
 *
 * Útil para la captura dirigida (una foto puntual), el micro-benchmark de
 * arranque (S17) y las pruebas instrumentadas. El flujo continuo de cámara
 * usará el wrapper del agente CAM sobre su frame nativo.
 *
 * No retiene más que la referencia al bitmap: no lo copia, no lo persiste
 * y no lo registra (RNF-012).
 */
class BitmapImageFrame(
    private val bitmap: Bitmap,
    override val timestampMillis: Long,
) : PixelAccessFrame {

    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    override fun readArgbPixels(): IntArray {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels
    }

    override fun readArgbRegion(left: Int, top: Int, side: Int): IntArray {
        require(left >= 0 && top >= 0 && side > 0 && left + side <= width && top + side <= height) {
            "La región ($left, $top, lado $side) no cabe en un frame de ${width}x$height."
        }
        // Copia solo la región pedida: evita materializar el frame completo (RNF-007).
        val pixels = IntArray(side * side)
        bitmap.getPixels(pixels, 0, side, left, top, side, side)
        return pixels
    }
}
