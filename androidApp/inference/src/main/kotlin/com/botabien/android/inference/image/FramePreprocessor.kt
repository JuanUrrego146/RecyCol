package com.botabien.android.inference.image

import com.botabien.android.inference.frame.PixelAccessFrame
import com.botabien.android.inference.model.InputTensorSpec
import com.botabien.android.inference.roi.CropRegion
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Convierte un frame en el tensor de entrada que espera el modelo.
 *
 * Pipeline fijo: lectura de la región (la pedida o el cuadrado central por
 * defecto) → remuestreo bilineal al lado de la spec → escritura RGB por
 * filas. Con entrada cuantizada escribe UINT8 `[0, 255]`; con entrada
 * flotante normaliza con la media y desviación de la spec. Trabaja sobre el
 * arreglo de píxeles, sin tipos de plataforma, para que sea comprobable en
 * JVM y determinista en cualquier dispositivo.
 *
 * Presupuesto (RNF-001, RNF-007): se lee solo la región a muestrear (nunca el
 * frame completo si la implementación del frame lo permite), el muestreo es
 * O(lado² del modelo) y el búfer de salida se **reutiliza** entre llamadas
 * para no asignar memoria nativa en el bucle de análisis continuo.
 *
 * Contrato del búfer devuelto: pertenece al preprocesador y se sobrescribe en
 * la siguiente llamada; el llamador debe consumirlo (ejecutar la inferencia)
 * antes de volver a llamar. Las llamadas están sincronizadas, así que la
 * instancia puede compartirse entre etapas sin corromper datos.
 */
class FramePreprocessor {

    private var reusableBuffer: ByteBuffer? = null

    /**
     * Produce el búfer de entrada, directo y en orden nativo, con la posición
     * en cero y el límite en el tamaño exacto del tensor, listo para el
     * intérprete.
     *
     * @param region región cuadrada a recortar (RF-010); si es nula se usa el
     *   cuadrado central máximo. Debe caber en el frame.
     */
    @Synchronized
    fun preprocess(
        frame: PixelAccessFrame,
        spec: InputTensorSpec,
        region: CropRegion? = null,
    ): ByteBuffer {
        val crop = region ?: CropRegion.centeredSquare(frame.width, frame.height)
        require(crop.left + crop.size <= frame.width && crop.top + crop.size <= frame.height) {
            "La región $crop no cabe en un frame de ${frame.width}x${frame.height}."
        }

        // Solo la región que se va a muestrear; coordenadas relativas a ella.
        val pixels = frame.readArgbRegion(crop.left, crop.top, crop.size)
        require(pixels.size == crop.size * crop.size) {
            "La región de lado ${crop.size} entregó ${pixels.size} píxeles."
        }

        val target = spec.inputSize
        val bytesPerChannel = if (spec.quantizedInput) 1 else 4
        val buffer = acquireBuffer(target * target * CHANNELS * bytesPerChannel)

        val side = crop.size
        val scale = side.toFloat() / target
        val maxCoordinate = (side - 1).toFloat()

        for (ty in 0 until target) {
            val sourceY = ((ty + 0.5f) * scale - 0.5f).coerceIn(0f, maxCoordinate)
            for (tx in 0 until target) {
                val sourceX = ((tx + 0.5f) * scale - 0.5f).coerceIn(0f, maxCoordinate)
                val argb = sampleBilinear(pixels, side, sourceX, sourceY)
                writePixel(buffer, argb, spec)
            }
        }

        buffer.rewind()
        return buffer
    }

    /**
     * Reutiliza el búfer directo entre llamadas (RNF-007): solo se asigna uno
     * nuevo si el tamaño requerido crece. El límite queda en el tamaño exacto
     * del tensor para que el intérprete vea `remaining()` correcto.
     */
    private fun acquireBuffer(byteCount: Int): ByteBuffer {
        val existing = reusableBuffer
        if (existing != null && existing.capacity() >= byteCount) {
            existing.clear()
            existing.limit(byteCount)
            return existing
        }
        return ByteBuffer.allocateDirect(byteCount)
            .order(ByteOrder.nativeOrder())
            .also { reusableBuffer = it }
    }

    /** Interpolación bilineal de los cuatro vecinos de una coordenada continua. */
    private fun sampleBilinear(pixels: IntArray, side: Int, x: Float, y: Float): Int {
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = minOf(x0 + 1, side - 1)
        val y1 = minOf(y0 + 1, side - 1)
        val fx = x - x0
        val fy = y - y0

        val topLeft = pixels[y0 * side + x0]
        val topRight = pixels[y0 * side + x1]
        val bottomLeft = pixels[y1 * side + x0]
        val bottomRight = pixels[y1 * side + x1]

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
