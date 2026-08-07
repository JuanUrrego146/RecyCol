package com.recycol.android.camera

import com.recycol.domain.model.FrameQuality
import com.recycol.domain.model.ImageFrame
import com.recycol.domain.port.FrameQualityAnalyzer

/**
 * Implementación del puerto [FrameQualityAnalyzer] con heurísticas
 * deterministas y sin ML (RF-015, CUS-004):
 *
 * - **Nitidez**: varianza del Laplaciano sobre el plano de luminancia. Un
 *   frame desenfocado tiene bordes suaves y varianza baja.
 * - **Luminancia**: media del plano Y normalizada a `[0,1]`. Los umbrales de
 *   sub/sobreexposición viven en [FrameQualityThresholds].
 * - **Encuadre**: proporción de la energía de bordes que cae dentro del área
 *   útil central (el marco guía de gama baja). Sin detector de objetos: eso
 *   es del agente EDGE y de gamas superiores.
 * - **Suciedad del lente**: delega en [LensSoilingDetector] (S12); por defecto
 *   un detector nulo que nunca acusa suciedad.
 *
 * Todas las métricas se calculan sobre una rejilla submuestreada
 * ([SAMPLE_STEP]) para no consumir el presupuesto de latencia de la
 * clasificación (RNF-001). El análisis es síncrono y no retiene el frame
 * (RNF-012).
 */
class HeuristicFrameQualityAnalyzer(
    private val soilingDetector: LensSoilingDetector = LensSoilingDetector.None,
) : FrameQualityAnalyzer {

    override fun analyze(frame: ImageFrame): FrameQuality {
        val lumaFrame = frame as? LumaImageFrame
            ?: throw IllegalArgumentException(
                "HeuristicFrameQualityAnalyzer requiere LumaImageFrame; recibió ${frame::class.simpleName}",
            )
        val luma = lumaFrame.luma
        val width = lumaFrame.width
        val height = lumaFrame.height

        return FrameQuality(
            sharpness = sharpness(luma, width, height),
            luminance = meanLuminance(luma, width, height),
            lensSoiling = soilingDetector.update(lumaFrame),
            objectCentered = objectCentered(luma, width, height),
        )
    }

    /**
     * Varianza del Laplaciano de 4 vecinos, normalizada a `[0,1]` saturando en
     * [FrameQualityThresholds.SHARPNESS_SATURATION_VARIANCE].
     */
    private fun sharpness(luma: ByteArray, width: Int, height: Int): Float {
        val step = SAMPLE_STEP
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        var y = step
        while (y < height - step) {
            var x = step
            val row = y * width
            while (x < width - step) {
                val center = luma[row + x].toInt() and 0xFF
                val left = luma[row + x - step].toInt() and 0xFF
                val right = luma[row + x + step].toInt() and 0xFF
                val up = luma[row - step * width + x].toInt() and 0xFF
                val down = luma[row + step * width + x].toInt() and 0xFF
                val response = (4 * center - left - right - up - down).toDouble()
                sum += response
                sumSq += response * response
                count++
                x += step
            }
            y += step
        }
        if (count == 0) return 0f
        val mean = sum / count
        val variance = sumSq / count - mean * mean
        val normalized = variance / FrameQualityThresholds.SHARPNESS_SATURATION_VARIANCE
        return normalized.coerceIn(0.0, 1.0).toFloat()
    }

    /** Luminancia media del plano Y en `[0,1]`. */
    private fun meanLuminance(luma: ByteArray, width: Int, height: Int): Float {
        val step = SAMPLE_STEP
        var sum = 0L
        var count = 0
        var y = 0
        while (y < height) {
            val row = y * width
            var x = 0
            while (x < width) {
                sum += luma[row + x].toInt() and 0xFF
                count++
                x += step
            }
            y += step
        }
        if (count == 0) return 0f
        return (sum.toDouble() / count / 255.0).toFloat()
    }

    /**
     * `true` si el área útil central concentra suficiente energía de bordes:
     * el objeto está dentro del marco guía y tiene detalle analizable.
     */
    private fun objectCentered(luma: ByteArray, width: Int, height: Int): Boolean {
        val step = SAMPLE_STEP
        val marginX = (width * (1 - FrameQualityThresholds.USEFUL_AREA_FRACTION) / 2).toInt()
        val marginY = (height * (1 - FrameQualityThresholds.USEFUL_AREA_FRACTION) / 2).toInt()
        var centerEnergy = 0.0
        var totalEnergy = 0.0
        var y = step
        while (y < height - step) {
            val row = y * width
            var x = step
            while (x < width - step) {
                val center = luma[row + x].toInt() and 0xFF
                val right = luma[row + x + step].toInt() and 0xFF
                val down = luma[row + step * width + x].toInt() and 0xFF
                val energy = (abs(center - right) + abs(center - down)).toDouble()
                totalEnergy += energy
                val inUsefulArea =
                    x >= marginX && x < width - marginX && y >= marginY && y < height - marginY
                if (inUsefulArea) centerEnergy += energy
                x += step
            }
            y += step
        }
        if (totalEnergy < FrameQualityThresholds.MIN_TOTAL_EDGE_ENERGY) return false
        return centerEnergy / totalEnergy >= FrameQualityThresholds.CENTER_ENERGY_FRACTION
    }

    private fun abs(v: Int): Int = if (v < 0) -v else v

    companion object {
        /**
         * Paso de submuestreo de la rejilla de análisis. Con 2, se procesa un
         * cuarto de los píxeles: suficiente para heurísticas y cuatro veces
         * más barato.
         */
        const val SAMPLE_STEP = 2
    }
}
