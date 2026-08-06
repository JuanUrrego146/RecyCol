package com.botabien.domain.usecase

import com.botabien.domain.model.BinId
import com.botabien.domain.model.DetectedBin
import com.botabien.testing.FakeBinAvailabilityRepository
import com.botabien.testing.FakeBinDetector
import com.botabien.testing.FakeProfileRepository
import com.botabien.testing.StubImageFrame
import com.botabien.testing.TestProfiles
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pruebas del caso de uso de escaneo de canecas (CUS-002): el emparejamiento
 * con el perfil es lógica de dominio, no del ViewModel.
 */
class ScanBinsUseCaseTest {

    private fun useCase(
        detector: FakeBinDetector = FakeBinDetector(),
        bins: FakeBinAvailabilityRepository = FakeBinAvailabilityRepository(),
    ) = ScanBinsUseCase(
        detector = detector,
        profiles = FakeProfileRepository(),
        binAvailability = bins,
    )

    @Test
    fun lasDeteccionesSeEmparejanConLasCanecasDelPerfil() = runTest {
        val recognized = useCase().scan(StubImageFrame())

        assertEquals(
            listOf(TestProfiles.whiteBin, TestProfiles.greenBin, TestProfiles.blackBin),
            recognized.map { it.definition },
        )
    }

    @Test
    fun unColorQueElPerfilNoDeclaraSeIgnora() = runTest {
        val alien = FakeBinDetector(
            detections = listOf(DetectedBin(colorHex = "#FF00FF", confidence = 0.99f)),
        )

        assertTrue(useCase(detector = alien).scan(StubImageFrame()).isEmpty())
    }

    @Test
    fun deteccionesRepetidasDeUnaCanecaSeReducenAUna() = runTest {
        val doubled = FakeBinDetector(
            detections = listOf(
                DetectedBin(colorHex = TestProfiles.whiteBin.colorHex, confidence = 0.95f),
                DetectedBin(colorHex = TestProfiles.whiteBin.colorHex.lowercase(), confidence = 0.80f),
            ),
        )

        val recognized = useCase(detector = doubled).scan(StubImageFrame())

        assertEquals(1, recognized.size)
        assertEquals(0.95f, recognized.single().confidence, "Se conserva la primera detección")
    }

    @Test
    fun confirmarPersisteLaSeleccionDelUsuario() = runTest {
        val repository = FakeBinAvailabilityRepository()
        val selection = setOf(TestProfiles.whiteBin.id, TestProfiles.blackBin.id)

        useCase(bins = repository).confirm(selection)

        assertEquals(selection, repository.availableBins())
    }

    @Test
    fun confirmarCanecasFueraDelPerfilFalla() = runTest {
        assertFailsWith<IllegalArgumentException> {
            useCase().confirm(setOf(BinId("purple")))
        }
    }
}
