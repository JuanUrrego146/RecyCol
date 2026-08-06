package com.botabien.android.camera

import java.nio.ByteBuffer

/**
 * Copia del plano de luminancia (Y) de un frame YUV_420_888 a un búfer
 * compacto de `width * height` bytes, eliminando el relleno de fila y de
 * píxel que impone el hardware.
 *
 * Es una función pura sobre `ByteBuffer` para poder probarla en JVM sin
 * dispositivo: la única parte específica de CameraX es quién le pasa el búfer.
 */
object LumaPlaneCopier {

    /**
     * Copia [height] filas de [width] píxeles desde [source] hacia [dest].
     *
     * @param source búfer del plano Y tal como lo entrega la cámara; su posición se modifica.
     * @param rowStride bytes entre el inicio de dos filas consecutivas en [source].
     * @param pixelStride bytes entre dos píxeles consecutivos de una fila en [source].
     * @param dest búfer destino de al menos `width * height` bytes.
     */
    fun copy(
        source: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        dest: ByteArray,
    ) {
        require(dest.size >= width * height) {
            "Destino de ${dest.size} bytes para un plano de ${width * height}"
        }
        if (pixelStride == 1) {
            copyRowAligned(source, rowStride, width, height, dest)
        } else {
            copyPixelByPixel(source, rowStride, pixelStride, width, height, dest)
        }
    }

    private fun copyRowAligned(
        source: ByteBuffer,
        rowStride: Int,
        width: Int,
        height: Int,
        dest: ByteArray,
    ) {
        if (rowStride == width) {
            source.position(0)
            source.get(dest, 0, width * height)
            return
        }
        for (row in 0 until height) {
            source.position(row * rowStride)
            source.get(dest, row * width, width)
        }
    }

    private fun copyPixelByPixel(
        source: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        dest: ByteArray,
    ) {
        for (row in 0 until height) {
            val rowStart = row * rowStride
            val destRow = row * width
            for (col in 0 until width) {
                dest[destRow + col] = source.get(rowStart + col * pixelStride)
            }
        }
    }
}
