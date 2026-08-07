package com.recycol.android.camera

import java.nio.ByteBuffer

/**
 * Convierte el plano RGBA_8888 que entrega CameraX en los dos búferes que
 * consume la app: píxeles ARGB empaquetados (para el clasificador, a través de
 * `PixelAccessFrame`) y plano de luminancia (para las heurísticas de calidad).
 *
 * Aplica de una vez la rotación del sensor: el frame sale **derecho**, con la
 * misma orientación con la que el usuario ve el objeto. Sin esto el modelo
 * recibiría la imagen girada 90° en el uso normal en vertical —el sensor de un
 * teléfono entrega apaisado— y clasificaría sobre algo que no se parece a
 * nada de su entrenamiento.
 *
 * Una sola pasada produce ambos búferes: recorrer el frame dos veces costaría
 * el doble sin ganar nada, y el bucle de análisis es continuo (RNF-001).
 *
 * Función pura sobre `ByteBuffer`, comprobable en JVM sin dispositivo.
 */
object ArgbFrameConverter {

    /** Ancho del frame ya rotado: los cuartos de vuelta intercambian los lados. */
    fun rotatedWidth(sourceWidth: Int, sourceHeight: Int, rotationDegrees: Int): Int =
        if (rotationDegrees % 180 == 0) sourceWidth else sourceHeight

    /** Alto del frame ya rotado. */
    fun rotatedHeight(sourceWidth: Int, sourceHeight: Int, rotationDegrees: Int): Int =
        if (rotationDegrees % 180 == 0) sourceHeight else sourceWidth

    /**
     * Vuelca [source] en [argb] y [luma], ya rotado.
     *
     * @param source plano RGBA_8888 tal como lo entrega la cámara.
     * @param rowStride bytes entre el inicio de dos filas consecutivas de [source].
     * @param argb destino de `sourceWidth * sourceHeight` píxeles ARGB_8888.
     * @param luma destino de `sourceWidth * sourceHeight` bytes de luminancia.
     * @param rotationDegrees giro horario que hay que aplicar para verlo derecho.
     */
    fun convert(
        source: ByteBuffer,
        rowStride: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        rotationDegrees: Int,
        argb: IntArray,
        luma: ByteArray,
    ) {
        val pixelCount = sourceWidth * sourceHeight
        require(rotationDegrees % 90 == 0) {
            "Rotación de $rotationDegrees°: la cámara solo entrega cuartos de vuelta."
        }
        require(argb.size >= pixelCount && luma.size >= pixelCount) {
            "Destinos de ${argb.size}/${luma.size} para un frame de $pixelCount píxeles."
        }
        val rotation = ((rotationDegrees % 360) + 360) % 360

        for (y in 0 until sourceHeight) {
            val rowStart = y * rowStride
            for (x in 0 until sourceWidth) {
                val offset = rowStart + x * BYTES_PER_PIXEL
                val r = source.get(offset).toInt() and 0xFF
                val g = source.get(offset + 1).toInt() and 0xFF
                val b = source.get(offset + 2).toInt() and 0xFF

                val destIndex = destIndex(x, y, sourceWidth, sourceHeight, rotation)
                argb[destIndex] = OPAQUE_ALPHA or (r shl 16) or (g shl 8) or b
                luma[destIndex] = ((r * R_WEIGHT + g * G_WEIGHT + b * B_WEIGHT) shr 8).toByte()
            }
        }
    }

    /** Índice del píxel `(x, y)` del sensor dentro del frame ya rotado. */
    private fun destIndex(x: Int, y: Int, width: Int, height: Int, rotation: Int): Int =
        when (rotation) {
            90 -> x * height + (height - 1 - y)
            180 -> (height - 1 - y) * width + (width - 1 - x)
            270 -> (width - 1 - x) * height + y
            else -> y * width + x
        }

    private const val BYTES_PER_PIXEL = 4
    private const val OPAQUE_ALPHA = 0xFF shl 24

    /*
     * Pesos de luminancia BT.601 (0,299 / 0,587 / 0,114) en aritmética entera
     * sobre 256: el resultado coincide con el plano Y que la app venía usando,
     * así que los umbrales de calidad de CAM siguen calibrados igual.
     */
    private const val R_WEIGHT = 77
    private const val G_WEIGHT = 150
    private const val B_WEIGHT = 29
}
