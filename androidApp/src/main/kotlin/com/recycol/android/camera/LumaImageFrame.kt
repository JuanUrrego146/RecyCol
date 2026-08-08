package com.recycol.android.camera

import com.recycol.android.inference.frame.PixelAccessFrame

/**
 * Implementación Android del frame de cámara: metadatos más los dos búferes
 * que consume la app.
 *
 * - [luma], plano de luminancia, para las heurísticas de calidad (nitidez,
 *   luminancia, suciedad del lente).
 * - [argb], píxeles en color, para el clasificador a través de
 *   [PixelAccessFrame] — este es el punto de encuentro con
 *   `androidApp/inference/`.
 *
 * Ambos búferes vienen ya **rotados a la vertical del usuario**
 * ([ArgbFrameConverter]), así que las coordenadas de las regiones de recorte
 * son las mismas que ve quien apunta el teléfono.
 *
 * Propiedad de los búferes: pertenecen a un anillo reutilizado por
 * [CameraXFrameSource]. El frame es válido solo durante el procesamiento
 * síncrono del colector; no debe retenerse ni copiarse fuera de ese ciclo.
 *
 * Invariante de privacidad (RNF-012): este frame no se persiste, no se
 * serializa y no se registra en logs. `toString` no expone píxeles.
 *
 * @property luma plano de luminancia compactado, un byte por píxel.
 * @property argb píxeles ARGB_8888 empaquetados, un `Int` por píxel.
 */
class LumaImageFrame(
    override val width: Int,
    override val height: Int,
    override val timestampMillis: Long,
    val luma: ByteArray,
    val argb: IntArray,
) : PixelAccessFrame {

    override fun readArgbPixels(): IntArray = argb.copyOf(width * height)

    override fun readArgbRegion(left: Int, top: Int, side: Int): IntArray {
        require(left >= 0 && top >= 0 && side > 0 && left + side <= width && top + side <= height) {
            "La región ($left, $top, lado $side) no cabe en un frame de ${width}x$height."
        }
        val region = IntArray(side * side)
        for (row in 0 until side) {
            System.arraycopy(argb, (top + row) * width + left, region, row * side, side)
        }
        return region
    }

    override fun toString(): String = "LumaImageFrame(${width}x$height, t=$timestampMillis)"
}
