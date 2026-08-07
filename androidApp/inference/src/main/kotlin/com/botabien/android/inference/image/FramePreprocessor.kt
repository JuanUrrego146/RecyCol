package com.botabien.android.inference.image

import com.botabien.android.inference.frame.PixelAccessFrame
import com.botabien.android.inference.model.InputTensorSpec
import com.botabien.android.inference.roi.CropRegion
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Convierte un frame en el tensor de entrada que espera el modelo.
 *
 * Pipeline fijo: recorte (la región pedida o el cuadrado central por defecto)
 * → remuestreo bilineal al lado de la spec → escritura RGB por filas. Con
 * entrada cuantizada escribe UINT8 `[0, 255]`; con entrada flotante normaliza
 * con la media y desviación de la spec. Trabaja sobre el arreglo de píxeles,
 * sin tipos de plataforma, para que sea comprobable en JVM y determinista en
 * cualquier dispositivo.
 *
 * El costo es O(lado²) del destino: se muestrea solo lo que el modelo consume,
 * nunca se recorre el frame completo (presupuesto de latencia, RNF-001).
 */
class FramePreprocessor {

    /**
     * Produce el búfer de entrada, directo y en orden nativo, con la posición
     * en cero, listo para el intérprete.
     *
     * @param region región cuadrada a recortar (RF-010); si es nula se usa el
     *   cuadrado central máximo. Debe caber en el frame.
     */
    fun preprocess(
        frame: PixelAccessFrame,
        spec: InputTensorSpec,
        region: CropRegion? = null,
    ): ByteBuffer {
        val pixels = frame.readArgbPixels()
        require(pixels.size == frame.width * frame.height) {
            "El frame declara ${frame.width}x${frame.height} pero entrega ${pixels.size} píxeles."
        }
        val crop = region ?: CropRegion.centeredSquare(frame.width, frame.height)
        require(crop.left + crop.size <= frame.width && crop.top + crop.size <= frame.height) {
            "La región $crop no cabe en un frame de ${frame.width}x${frame.height}."
        }

        val target = spec.inputSize
        val bytesPerChannel = if (spec.quantizedInput) 1 else 4
        val buffer = ByteBuffer
            .allocateDirect(target * target * CHANNELS * bytesPerChannel)
            .order(ByteOrder.nativeOrder())

        val scale = crop.size.toFloat() / target

        for (ty in 0 until target) {
            val sourceY = clampToCrop(crop.top + (ty + 0.5f) * scale - 0.5f, crop.top, crop.size)
            for (tx in 0 until target) {
                val sourceX = clampToCrop(crop.left + (tx + 0.5f) * scale - 0.5f, crop.left, crop.size)
                val argb = sampleBilinear(pixels, frame.width, sourceX, sourceY)
                writePixel(buffer, argb, spec)
            }
        }

        buffer.rewind()
        return buffer
    }

    /** Coordenada continua limitada al interior del recorte `[origin, origin + side - 1]`. */
    private fun clampToCrop(value: Float, origin: Int, side: Int): Float =
        value.coerceIn(origin.toFloat(), (origin + side - 1).toFloat())

    /** Interpolación bilineal de los cuatro vecinos de una coordenada continua. */
    private fun sampleBilinear(pixels: IntArray, width: Int, x: Float, y: Float): Int {
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = minOf(x0 + 1, width - 1)
        val y1 = minOf(y0 + 1, pixels.size / width - 1)
        val fx = x - x0
        val fy = y - y0

        val topLeft = pixels[y0 * width + x0]
        val topRight = pixels[y0 * width + x1]
        val bottomLeft = pixels[y1 * width + x0]
        val bottomRight = pixels[y1 * width + x1]

        var result = 0
        for (shift in intArrayOf(16, 8, 0)) {
            val top = lerp(channel(topLeft, shift), channel(topRight, shift), fx)
            val bottom = lerp(channel(bottomLeft, shift), channel(bottomRight, shift), fx)
            val value = lerp(top, bottom, fy).toInt().coerceIn(0, 255)
            result = result or (value shl shift)
        }
        return result
    }

    private fun writePixel(buffer: ByteBuffer, argb: Int, spec: InputTensorSpec) {
        for (shift in intArrayOf(16, 8, 0)) {
            val channel = channel(argb, shift)
            if (spec.quantizedInput) {
                buffer.put(channel.toInt().toByte())
            } else {
                buffer.putFloat((channel - spec.inputMean) / spec.inputStd)
            }
        }
    }

    private fun channel(argb: Int, shift: Int): Float = ((argb shr shift) and 0xFF).toFloat()

    private fun lerp(from: Float, to: Float, fraction: Float): Float =
        from + (to - from) * fraction

    private companion object {
        /** Canales de entrada del modelo: RGB, sin alfa. */
        const val CHANNELS = 3
    }
}
