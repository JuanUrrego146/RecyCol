package com.botabien.testing

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Documenta el contrato del fake de `BinDetector`: detección fija, en orden
 * estable, con las tres canecas del perfil de prueba por defecto.
 */
class FakeBinDetectorTest {

    @Test
    fun porDefectoDetectaLasTresCanecasDelPerfilDePrueba() = runTest {
        val detector = FakeBinDetector()

        val detections = detector.detectBins(StubImageFrame())

        assertEquals(3, detections.size)
        assertEquals(
            listOf(
                TestProfiles.whiteBin.colorHex,
                TestProfiles.greenBin.colorHex,
                TestProfiles.blackBin.colorHex,
            ),
            detections.map { it.colorHex },
        )
    }

    @Test
    fun devuelveLaMismaListaEnCadaInvocacion() = runTest {
        val detector = FakeBinDetector()

        val first = detector.detectBins(StubImageFrame())
        val second = detector.detectBins(StubImageFrame(timestampMillis = 12345L))

        assertEquals(first, second)
    }
}
