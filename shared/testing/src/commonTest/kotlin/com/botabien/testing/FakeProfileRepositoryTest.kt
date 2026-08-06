package com.botabien.testing

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Documenta el contrato del fake de `ProfileRepository`: catálogo en memoria,
 * perfil activo preseleccionado por defecto y primer arranque simulable.
 */
class FakeProfileRepositoryTest {

    @Test
    fun porDefectoElPerfilDePruebaEstaActivoYEnElCatalogo() = runTest {
        val repository = FakeProfileRepository()

        assertEquals(listOf(TestProfiles.threeBins), repository.availableProfiles())
        assertEquals(TestProfiles.threeBins, repository.activeProfileOrNull())
    }

    @Test
    fun elPrimerArranqueSeSimulaSinPerfilActivo() = runTest {
        val repository = FakeProfileRepository(initiallyActive = null)

        assertNull(repository.activeProfileOrNull())

        repository.setActiveProfile(TestProfiles.threeBins.isoCode)

        assertEquals(TestProfiles.threeBins, repository.activeProfileOrNull())
    }

    @Test
    fun seleccionarUnPaisFueraDelCatalogoFalla() = runTest {
        val repository = FakeProfileRepository()

        assertFailsWith<IllegalArgumentException> {
            repository.setActiveProfile("xx")
        }
    }
}
