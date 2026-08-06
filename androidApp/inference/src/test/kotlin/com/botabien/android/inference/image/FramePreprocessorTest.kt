package com.botabien.android.inference.image

import com.botabien.android.inference.FakePixelFrame
import com.botabien.android.inference.model.ModelSpec
import kotlin.test.Test
import kotlin.test.assertEquals

class FramePreprocessorTest {

    private val preprocessor = FramePreprocessor()

    private fun quantizedSpec(size: Int) = ModelSpec(
        assetFileName = "test.tflite",
        inputSize = size,
        quantizedInput = true,
        outputClasses = 2,
    )

    @Test
    fun `un frame de color solido produce un tensor uniforme con los canales RGB en orden`() {
        // ARGB: A=FF, R=10, G=20, B=30
        val frame = FakePixelFrame.solid(width = 100, height = 60, argb = 0xFF102030.toInt())

        val buffer = preprocessor.preprocess(frame, quantizedSpec(8))

        assertEquals(8 * 8 * 3, buffer.remaining())
        for (pixel in 0 until 64) {
            assertEquals(0x10.toByte(), buffer.get(pixel * 3), "canal R del píxel $pixel")
            assertEquals(0x20.toByte(), buffer.get(pixel * 3 + 1), "canal G del píxel $pixel")
            assertEquals(0x30.toByte(), buffer.get(pixel * 3 + 2), "canal B del píxel $pixel")
        }
    }

    @Test
    fun `el recorte central descarta las bandas laterales de un frame apaisado`() {
        // Frame 8x2: recorte central de 2x2 en las columnas 3 y 4.
        // Columna 3 roja, columna 4 azul; el resto verde (debe descartarse).
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        val green = 0xFF00FF00.toInt()
        val pixels = IntArray(16) { index ->
            when (index % 8) {
                3 -> red
                4 -> blue
                else -> green
            }
        }
        val frame = FakePixelFrame(width = 8, height = 2, pixels = pixels)

        val buffer = preprocessor.preprocess(frame, quantizedSpec(2))

        // Píxel (0,0) → columna roja; píxel (1,0) → columna azul. Sin mezcla:
        // con destino 2 y recorte 2 el muestreo cae exactamente sobre cada columna.
        assertEquals(0xFF.toByte(), buffer.get(0), "R del píxel izquierdo")
        assertEquals(0x00.toByte(), buffer.get(2), "B del píxel izquierdo")
        assertEquals(0x00.toByte(), buffer.get(3), "R del píxel derecho")
        assertEquals(0xFF.toByte(), buffer.get(5), "B del píxel derecho")
    }

    @Test
    fun `la entrada flotante se normaliza con la media y desviacion de la spec`() {
        val frame = FakePixelFrame.solid(width = 10, height = 10, argb = 0xFF7F7F7F.toInt())
        val spec = ModelSpec(
            assetFileName = "float.tflite",
            inputSize = 4,
            quantizedInput = false,
            outputClasses = 2,
            inputMean = 127.5f,
            inputStd = 127.5f,
        )

        val buffer = preprocessor.preprocess(frame, spec)

        assertEquals(4 * 4 * 3 * 4, buffer.remaining())
        val value = buffer.asFloatBuffer().get(0)
        // (127 - 127.5) / 127.5 ≈ -0.0039
        assertEquals(-0.0039f, value, absoluteTolerance = 0.0005f)
    }

    @Test
    fun `la ampliacion de un frame pequeno interpola sin salirse del rango`() {
        val frame = FakePixelFrame.solid(width = 4, height = 4, argb = 0xFFFFFFFF.toInt())

        val buffer = preprocessor.preprocess(frame, quantizedSpec(224))

        assertEquals(224 * 224 * 3, buffer.remaining())
        assertEquals(0xFF.toByte(), buffer.get(0))
        assertEquals(0xFF.toByte(), buffer.get(buffer.remaining() - 1))
    }
}
