package com.botabien.android.inference.bins

import com.botabien.domain.model.DetectedBin
import com.botabien.domain.model.ImageFrame
import com.botabien.domain.port.BinDetector
import com.botabien.domain.port.ProfileRepository
import com.botabien.rules.bins.BinColorMatcher
import com.botabien.rules.bins.ColorRegionFinder
import com.botabien.rules.bins.toCanonicalDetections

/**
 * Frame que expone sus píxeles como búfer ARGB para el detector de canecas.
 *
 * La implementación de [ImageFrame] del módulo de cámara (agente CAM) la
 * implementa haciendo la conversión YUV→ARGB submuestreada; `LumaImageFrame`
 * (S10) solo expone luminancia, así que esta interfaz es la coordinación
 * pendiente con CAM. El búfer se consume y se descarta: nunca se persiste ni
 * se registra (RNF-012).
 */
interface PixelReadableFrame : ImageFrame {

    /** Píxeles ARGB en orden de filas, de tamaño `width * height`. */
    fun argbPixels(): IntArray
}

/**
 * Detector de canecas por regiones de color (RF-005, RF-006, CUS-002).
 *
 * Adaptador Android del contrato M0 [BinDetector]: encuentra las regiones de
 * color dominantes con [ColorRegionFinder] y las empareja con las canecas del
 * perfil activo con [BinColorMatcher] —ambos núcleo multiplataforma de
 * `shared/rules/bins`—, emitiendo el **color canónico** de cada caneca
 * reconocida. Ese es el contrato con `ScanBinsUseCase` (#49): el caso de uso
 * empareja por hex exacto y la tolerancia a iluminación variable es
 * responsabilidad de este detector.
 *
 * Sin píxeles ([PixelReadableFrame]) o sin perfil activo devuelve una lista
 * vacía: el detector no puede ver ni conoce el estándar, no adivina.
 */
class ColorRegionBinDetector(
    private val profiles: ProfileRepository,
    private val matcher: BinColorMatcher = BinColorMatcher(),
) : BinDetector {

    override suspend fun detectBins(frame: ImageFrame): List<DetectedBin> {
        val readable = frame as? PixelReadableFrame ?: return emptyList()
        val profile = profiles.activeProfileOrNull() ?: return emptyList()

        val rawDetections = ColorRegionFinder
            .findRegions(readable.argbPixels(), frame.width, frame.height)
            .map { region -> region.toDetectedBin() }

        return matcher.match(rawDetections, profile).toCanonicalDetections()
    }
}
