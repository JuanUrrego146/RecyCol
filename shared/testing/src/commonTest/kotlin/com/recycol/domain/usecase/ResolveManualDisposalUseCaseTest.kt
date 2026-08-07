package com.recycol.domain.usecase

import com.recycol.domain.model.ContaminationState
import com.recycol.domain.model.WasteMaterial
import com.recycol.testing.FakeBinAvailabilityRepository
import com.recycol.testing.FakeProfileRepository
import com.recycol.testing.FakeRuleEngine
import com.recycol.testing.TestProfiles
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pruebas de la selección manual (RF-024/RF-025, coordinación #94), incluido
 * el caso que motivó la decisión de Juan en #54: el usuario declara que es un
 * electrónico y la app lo manda al punto de recolección especial.
 */
class ResolveManualDisposalUseCaseTest {

    private fun useCase(
        bins: FakeBinAvailabilityRepository = FakeBinAvailabilityRepository(),
        profiles: FakeProfileRepository = FakeProfileRepository(),
    ) = ResolveManualDisposalUseCase(
        ruleEngine = FakeRuleEngine(),
        profiles = profiles,
        binAvailability = bins,
    )

    @Test
    fun electronicoElegidoAManoVaAlPuntoDeRecoleccionEspecial() = runTest {
        val onlyBlackNearby = FakeBinAvailabilityRepository(
            initialBins = setOf(TestProfiles.blackBin.id),
        )

        val outcome = useCase(bins = onlyBlackNearby).resolve(WasteMaterial.ELECTRONIC)

        assertEquals(TestProfiles.specialCollectionBin, outcome.disposal?.bin)
        assertTrue(outcome.manualSelection)
        assertFalse(outcome.needsUserDecision)
    }

    @Test
    fun laSeleccionManualLlevaConfianzaPlena() = runTest {
        val outcome = useCase().resolve(WasteMaterial.PLASTIC)

        assertEquals(WasteMaterial.PLASTIC, outcome.classification?.material)
        assertEquals(1.0f, outcome.classification?.confidence)
    }

    @Test
    fun elCartonDeclaradoContaminadoSeDegradaALaNegra() = runTest {
        val outcome = useCase().resolve(
            material = WasteMaterial.BEVERAGE_CARTON,
            contamination = ContaminationState.CONTAMINATED,
        )

        assertEquals(TestProfiles.blackBin, outcome.disposal?.bin)
        assertTrue(outcome.disposal?.degradedByContamination == true)
    }

    @Test
    fun sinPerfilActivoFallaExplicitamente() = runTest {
        val firstBoot = FakeProfileRepository(initiallyActive = null)

        assertFailsWith<IllegalStateException> {
            useCase(profiles = firstBoot).resolve(WasteMaterial.PLASTIC)
        }
    }
}
