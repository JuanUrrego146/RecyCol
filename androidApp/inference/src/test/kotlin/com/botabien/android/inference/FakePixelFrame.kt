package com.botabien.android.inference

import com.botabien.android.inference.frame.PixelAccessFrame

/**
 * Frame de prueba respaldado por un arreglo de píxeles ARGB en memoria.
 */
internal class FakePixelFrame(
    override val width: Int,
    override val height: Int,
    private val pixels: IntArray,
    override val timestampMillis: Long = 0L,
) : PixelAccessFrame {

    init {
        require(pixels.size == width * height)
    }

    override fun readArgbPixels(): IntArray = pixels.copyOf()

    companion object {
        /** Frame de un solo color ARGB. */
        fun solid(width: Int, height: Int, argb: Int): FakePixelFrame =
            FakePixelFrame(width, height, IntArray(width * height) { argb })
    }
}
