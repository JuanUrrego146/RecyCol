package com.recycol.android.camera

import android.util.Log
import com.recycol.domain.model.FrameQuality
import com.recycol.domain.model.ImageFrame
import com.recycol.domain.port.FrameQualityAnalyzer

/**
 * Decorador temporal que vuelca al log las métricas de calidad medidas.
 *
 * Los umbrales de [FrameQualityThresholds] se calibraron con el conjunto
 * sintético y su propia documentación remite la recalibración con capturas
 * reales a la verificación en dispositivo (S41). Esto es esa medición: sin
 * ella, ajustar los umbrales sería adivinar.
 *
 * Solo números (RNF-012): ni píxeles, ni frames, ni nada reconstruible.
 */
class FrameQualityProbe(
    private val delegate: FrameQualityAnalyzer,
) : FrameQualityAnalyzer {

    private var frames = 0

    override fun analyze(frame: ImageFrame): FrameQuality {
        val quality = delegate.analyze(frame)
        if (frames++ % SAMPLE_EVERY == 0) {
            val ratio = (delegate as? HeuristicFrameQualityAnalyzer)?.lastCenterEnergyRatio ?: -1.0
            Log.d(
                TAG,
                "nitidez=%.3f luminancia=%.3f centrado=%b (bordes centro=%.3f) suciedad=%b (%dx%d)".format(
                    quality.sharpness,
                    quality.luminance,
                    quality.objectCentered,
                    ratio,
                    quality.lensSoiling,
                    frame.width,
                    frame.height,
                ),
            )
        }
        return quality
    }

    private companion object {
        const val TAG = "RecyColCalidad"

        /** Un frame de cada diez: suficiente para calibrar sin inundar el log. */
        const val SAMPLE_EVERY = 10
    }
}
