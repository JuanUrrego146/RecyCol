package com.botabien.domain.usecase

import com.botabien.testing.FakeProfileRepository
import com.botabien.testing.TestProfiles
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Pruebas del caso de uso de configuración de país (CUS-001, RF-001, RF-003).
 */
class SelectCountryUseCaseTest {

    @Test
    fun exponeElCatalogoDePaisesDisponibles() = runTest {
        val useCase = SelectCountryUseCase(FakeProfileRepository())

        assertEquals(listOf(TestProfiles.threeBins), useCase.availableCountries())
    }

    @Test
    fun enElPrimerArranqueNoHayPerfilActivoYSeleccionarLoActiva() = runTest {
        val useCase = SelectCountryUseCase(FakeProfileRepository(initiallyActive = null))

        assertNull(useCase.activeProfileOrNull())

        useCase.select(TestProfiles.threeBins.isoCode)

        assertEquals(TestProfiles.threeBins, useCase.activeProfileOrNull())
    }

    @Test
    fun seleccionarUnPaisFueraDelCatalogoFalla() = runTest {
        val useCase = SelectCountryUseCase(FakeProfileRepository())

        assertFailsWith<IllegalArgumentException> {
            useCase.select("xx")
        }
    }
}
