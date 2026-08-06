package com.botabien.domain.usecase

import com.botabien.testing.FakeBinAvailabilityRepository
import com.botabien.testing.FakeProfileRepository
import com.botabien.testing.TestProfiles
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pruebas del caso de uso de configuración de país (CUS-001, RF-001, RF-003),
 * incluido el reinicio de canecas al cambiar de país (coordinación #65).
 */
class SelectCountryUseCaseTest {

    /** Segundo país sintético para probar el cambio de normativa. */
    private val otherCountry = TestProfiles.threeBins.copy(isoCode = "yy")

    private fun useCase(
        profiles: FakeProfileRepository = FakeProfileRepository(),
        bins: FakeBinAvailabilityRepository = FakeBinAvailabilityRepository(),
    ) = SelectCountryUseCase(profiles = profiles, binAvailability = bins)

    @Test
    fun exponeElCatalogoDePaisesDisponibles() = runTest {
        assertEquals(listOf(TestProfiles.threeBins), useCase().availableCountries())
    }

    @Test
    fun enElPrimerArranqueNoHayPerfilActivoYSeleccionarLoActiva() = runTest {
        val subject = useCase(profiles = FakeProfileRepository(initiallyActive = null))

        assertNull(subject.activeProfileOrNull())

        subject.select(TestProfiles.threeBins.isoCode)

        assertEquals(TestProfiles.threeBins, subject.activeProfileOrNull())
    }

    @Test
    fun seleccionarUnPaisFueraDelCatalogoFalla() = runTest {
        assertFailsWith<IllegalArgumentException> {
            useCase().select("xx")
        }
    }

    @Test
    fun cambiarDePaisReiniciaLasCanecasConfirmadas() = runTest {
        val bins = FakeBinAvailabilityRepository(
            initialBins = setOf(TestProfiles.whiteBin.id, TestProfiles.blackBin.id),
        )
        val subject = useCase(
            profiles = FakeProfileRepository(catalog = listOf(TestProfiles.threeBins, otherCountry)),
            bins = bins,
        )

        subject.select(otherCountry.isoCode)

        assertTrue(bins.availableBins().isEmpty(), "Las canecas del país anterior no valen en el nuevo")
    }

    @Test
    fun reseleccionarElMismoPaisConservaLasCanecas() = runTest {
        val selection = setOf(TestProfiles.whiteBin.id)
        val bins = FakeBinAvailabilityRepository(initialBins = selection)
        val subject = useCase(bins = bins)

        subject.select(TestProfiles.threeBins.isoCode)

        assertEquals(selection, bins.availableBins())
    }

    @Test
    fun laSeleccionDelPrimerArranqueNoTocaLasCanecas() = runTest {
        val selection = setOf(TestProfiles.greenBin.id)
        val bins = FakeBinAvailabilityRepository(initialBins = selection)
        val subject = useCase(
            profiles = FakeProfileRepository(initiallyActive = null),
            bins = bins,
        )

        subject.select(TestProfiles.threeBins.isoCode)

        assertEquals(selection, bins.availableBins())
    }
}
