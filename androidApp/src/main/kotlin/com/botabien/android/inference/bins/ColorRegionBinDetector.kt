package com.botabien.android.inference.bins

import com.botabien.domain.model.DetectedBin
import com.botabien.domain.model.ImageFrame
import com.botabien.domain.port.BinDetector
import com.botabien.rules.bins.ColorRegionFinder

/**
 * Frame que expone sus píxeles como búfer ARGB para el detector de canecas.
 *
 * La implementación de [ImageFrame] del módulo de cámara (agente CAM) la
 * implementa haciendo la conversión YUV→ARGB submuestreada. El búfer se
 * consume y se descarta: nunca se persiste ni se registra (RNF-012).
 */
interface PixelReadableFrame : ImageFrame {

    /** Píxeles ARGB en orden de filas, de tamaño `width * height`. */
    fun argbPixels(): IntArray
}

/**
 * Detector de canecas por regiones de color (RF-005, CUS-002).
 *
 * Adaptador Android del contrato M0 [BinDetector]: delega el análisis en
 * [ColorRegionFinder], el núcleo multiplataforma determinista de
 * `shared/rules/bins`, y traduce cada región dominante a una [DetectedBin]
 * con su color y confianza. El emparejamiento con las canecas del perfil
 * activo NO ocurre aquí: es responsabilidad de la capa de aplicación con
 * `BinColorMatcher` (contrato de `DetectedBin`).
 *
 * Un frame que no exponga píxeles ([PixelReadableFrame]) produce una lista
 * vacía: el detector no puede ver, no adivina.
 */
class ColorRegionBinDetector : BinDetector {

    override suspend fun detectBins(frame: ImageFrame): List<DetectedBin> {
        val readable = frame as? PixelReadableFrame ?: return emptyList()

        return ColorRegionFinder
            .findRegions(readable.argbPixels(), frame.width, frame.height)
            .map { region -> region.toDetectedBin() }
    }
}
