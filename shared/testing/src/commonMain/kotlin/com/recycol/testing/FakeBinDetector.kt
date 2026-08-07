package com.recycol.testing

import com.recycol.domain.model.DetectedBin
import com.recycol.domain.model.ImageFrame
import com.recycol.domain.port.BinDetector

/**
 * Fake determinista de `BinDetector` (implementación real: agente BINS).
 *
 * Por defecto «ve» las tres canecas del perfil de prueba [TestProfiles.threeBins],
 * siempre en el mismo orden y con la misma confianza.
 */
class FakeBinDetector(
    private val detections: List<DetectedBin> = listOf(
        DetectedBin(colorHex = TestProfiles.whiteBin.colorHex, confidence = 0.95f),
        DetectedBin(colorHex = TestProfiles.greenBin.colorHex, confidence = 0.93f),
        DetectedBin(colorHex = TestProfiles.blackBin.colorHex, confidence = 0.91f),
    ),
) : BinDetector {

    override suspend fun detectBins(frame: ImageFrame): List<DetectedBin> = detections
}
