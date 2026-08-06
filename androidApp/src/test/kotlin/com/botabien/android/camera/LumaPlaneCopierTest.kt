package com.botabien.android.camera

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class LumaPlaneCopierTest {

    @Test
    fun `copia directa cuando el plano no tiene relleno`() {
        val width = 4
        val height = 3
        val plano = ByteArray(width * height) { it.toByte() }
        val dest = ByteArray(width * height)

        LumaPlaneCopier.copy(
            source = ByteBuffer.wrap(plano),
            rowStride = width,
            pixelStride = 1,
            width = width,
            height = height,
            dest = dest,
        )

        assertContentEquals(plano, dest)
    }

    @Test
    fun `elimina el relleno de fila cuando rowStride excede el ancho`() {
        val width = 3
        val height = 2
        val rowStride = 5
        // Filas [0,1,2] y [10,11,12] con relleno 99 entre medias.
        val plano = byteArrayOf(0, 1, 2, 99, 99, 10, 11, 12, 99, 99)
        val dest = ByteArray(width * height)

        LumaPlaneCopier.copy(
            source = ByteBuffer.wrap(plano),
            rowStride = rowStride,
            pixelStride = 1,
            width = width,
            height = height,
            dest = dest,
        )

        assertContentEquals(byteArrayOf(0, 1, 2, 10, 11, 12), dest)
    }

    @Test
    fun `elimina el relleno de pixel cuando pixelStride es mayor que uno`() {
        val width = 3
        val height = 2
        val pixelStride = 2
        val rowStride = 6
        // Píxeles en posiciones pares; los impares son relleno.
        val plano = byteArrayOf(0, 99, 1, 99, 2, 99, 10, 99, 11, 99, 12, 99)
        val dest = ByteArray(width * height)

        LumaPlaneCopier.copy(
            source = ByteBuffer.wrap(plano),
            rowStride = rowStride,
            pixelStride = pixelStride,
            width = width,
            height = height,
            dest = dest,
        )

        assertContentEquals(byteArrayOf(0, 1, 2, 10, 11, 12), dest)
    }

    @Test
    fun `rechaza un destino demasiado pequeno`() {
        assertFailsWith<IllegalArgumentException> {
            LumaPlaneCopier.copy(
                source = ByteBuffer.allocate(16),
                rowStride = 4,
                pixelStride = 1,
                width = 4,
                height = 4,
                dest = ByteArray(8),
            )
        }
    }
}
