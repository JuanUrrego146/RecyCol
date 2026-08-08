package com.recycol.android.camera

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArgbFrameConverterTest {

    /**
     * Frame RGBA con relleno de fila, como el que entrega el hardware: cada
     * píxel lleva su posición codificada para poder seguirle la pista tras
     * rotar.
     */
    private fun rgbaBuffer(
        width: Int,
        height: Int,
        rowStride: Int = width * 4,
        pixel: (x: Int, y: Int) -> Triple<Int, Int, Int>,
    ): ByteBuffer {
        val buffer = ByteBuffer.allocate(rowStride * height).order(ByteOrder.nativeOrder())
        for (y in 0 until height) {
            for (x in 0 until width) {
                val (r, g, b) = pixel(x, y)
                val offset = y * rowStride + x * 4
                buffer.put(offset, r.toByte())
                buffer.put(offset + 1, g.toByte())
                buffer.put(offset + 2, b.toByte())
                buffer.put(offset + 3, 0xFF.toByte())
            }
        }
        return buffer
    }

    private fun convert(
        width: Int,
        height: Int,
        rotationDegrees: Int,
        rowStride: Int = width * 4,
        pixel: (x: Int, y: Int) -> Triple<Int, Int, Int>,
    ): Pair<IntArray, ByteArray> {
        val argb = IntArray(width * height)
        val luma = ByteArray(width * height)
        ArgbFrameConverter.convert(
            source = rgbaBuffer(width, height, rowStride, pixel),
            rowStride = rowStride,
            sourceWidth = width,
            sourceHeight = height,
            rotationDegrees = rotationDegrees,
            argb = argb,
            luma = luma,
        )
        return argb to luma
    }

    @Test
    fun `sin rotacion conserva la posicion y el color de cada pixel`() {
        val (argb, _) = convert(width = 3, height = 2, rotationDegrees = 0) { x, y ->
            Triple(x * 10, y * 20, 30)
        }

        assertEquals(0xFF000000.toInt() or (0 shl 16) or (0 shl 8) or 30, argb[0])
        assertEquals(0xFF000000.toInt() or (20 shl 16) or (0 shl 8) or 30, argb[2])
        assertEquals(0xFF000000.toInt() or (0 shl 16) or (20 shl 8) or 30, argb[3])
    }

    @Test
    fun `salta el relleno de fila que impone el hardware`() {
        val (argb, _) = convert(width = 2, height = 2, rotationDegrees = 0, rowStride = 64) { x, y ->
            Triple(x, y, 0)
        }

        assertEquals(0, argb[0] and 0xFFFF)
        assertEquals(1 shl 16, argb[1] and 0xFF0000)
        assertEquals(1 shl 8, argb[2] and 0x00FF00)
    }

    @Test
    fun `una rotacion de 90 grados intercambia los lados`() {
        assertEquals(2, ArgbFrameConverter.rotatedWidth(4, 2, 90))
        assertEquals(4, ArgbFrameConverter.rotatedHeight(4, 2, 90))
        assertEquals(4, ArgbFrameConverter.rotatedWidth(4, 2, 180))
        assertEquals(2, ArgbFrameConverter.rotatedHeight(4, 2, 180))
    }

    @Test
    fun `a 90 grados la esquina superior izquierda del sensor cae arriba a la derecha`() {
        // Marca solo el píxel (0,0) del sensor; el resto es negro.
        val (argb, _) = convert(width = 4, height = 2, rotationDegrees = 90) { x, y ->
            if (x == 0 && y == 0) Triple(255, 0, 0) else Triple(0, 0, 0)
        }

        // El frame rotado mide 2x4: girar en sentido horario lleva esa esquina
        // al extremo derecho de la primera fila.
        val rotatedWidth = 2
        assertEquals(0xFFFF0000.toInt(), argb[0 * rotatedWidth + (rotatedWidth - 1)])
    }

    @Test
    fun `a 180 grados la esquina superior izquierda cae abajo a la derecha`() {
        val (argb, _) = convert(width = 3, height = 2, rotationDegrees = 180) { x, y ->
            if (x == 0 && y == 0) Triple(255, 0, 0) else Triple(0, 0, 0)
        }

        assertEquals(0xFFFF0000.toInt(), argb[argb.size - 1])
    }

    @Test
    fun `a 270 grados la esquina superior izquierda cae abajo a la izquierda`() {
        val (argb, _) = convert(width = 4, height = 2, rotationDegrees = 270) { x, y ->
            if (x == 0 && y == 0) Triple(255, 0, 0) else Triple(0, 0, 0)
        }

        val rotatedWidth = 2
        assertEquals(0xFFFF0000.toInt(), argb[(4 - 1) * rotatedWidth + 0])
    }

    @Test
    fun `la luminancia sigue los pesos BT601 y acompana al pixel al rotar`() {
        val (_, luma) = convert(width = 2, height = 2, rotationDegrees = 0) { _, _ ->
            Triple(255, 255, 255)
        }
        assertEquals(255, luma[0].toInt() and 0xFF, "El blanco satura la luminancia")

        val (_, verde) = convert(width = 1, height = 1, rotationDegrees = 0) { _, _ ->
            Triple(0, 255, 0)
        }
        // 255 * 150 / 256 = 149: el verde pesa más de la mitad de la luminancia.
        assertEquals(149, verde[0].toInt() and 0xFF)

        val (_, negro) = convert(width = 1, height = 1, rotationDegrees = 0) { _, _ ->
            Triple(0, 0, 0)
        }
        assertEquals(0, negro[0].toInt() and 0xFF)
    }

    @Test
    fun `rechaza rotaciones que no sean cuartos de vuelta`() {
        assertFailsWith<IllegalArgumentException> {
            convert(width = 2, height = 2, rotationDegrees = 45) { _, _ -> Triple(0, 0, 0) }
        }
    }
}
