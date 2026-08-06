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
}
