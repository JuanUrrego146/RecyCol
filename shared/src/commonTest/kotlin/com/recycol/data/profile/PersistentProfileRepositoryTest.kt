package com.recycol.data.profile

import com.recycol.data.storage.InMemoryKeyValueStore
import com.recycol.domain.model.BinDefinition
import com.recycol.domain.model.BinId
import com.recycol.domain.model.CountryProfile
import com.recycol.domain.model.DisposalRoute
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Contrato de la persistencia de la selección de país (S36; CUS-001).
 * El catálogo es responsabilidad de RULES: aquí se usa uno mínimo en memoria.
 */
class PersistentProfileRepositoryTest {

    private val colombia = minimalProfile(isoCode = "co")
    private val chile = minimalProfile(isoCode = "cl")
    private val store = InMemoryKeyValueStore()
    private val repository = PersistentProfileRepository(
        catalogSource = { listOf(colombia, chile) },
        store = store,
    )

    @Test
    fun enElPrimerArranqueNoHayPerfilActivo() = runTest {
        assertNull(repository.activeProfileOrNull())
    }

    @Test
    fun elCatalogoExponeLosPerfilesDeLaFuente() = runTest {
        assertEquals(listOf(colombia, chile), repository.availableProfiles())
    }

    @Test
    fun seleccionarUnPaisActivaSuPerfil() = runTest {
        repository.setActiveProfile("co")

        assertEquals(colombia, repository.activeProfileOrNull())
    }

    @Test
    fun cambiarDePaisReemplazaLaSeleccionAnterior() = runTest {
        repository.setActiveProfile("co")
        repository.setActiveProfile("cl")

        assertEquals(chile, repository.activeProfileOrNull())
    }

    @Test
    fun seleccionarUnPaisFueraDelCatalogoFallaSinPersistirNada() = runTest {
        assertFailsWith<IllegalArgumentException> {
            repository.setActiveProfile("xx")
        }

        assertNull(repository.activeProfileOrNull())
    }

    @Test
    fun unaSeleccionQueYaNoExisteEnElCatalogoVuelveAlOnboarding() = runTest {
        repository.setActiveProfile("cl")
        val repositoryWithShrunkCatalog = PersistentProfileRepository(
            catalogSource = { listOf(colombia) },
            store = store,
        )

        assertNull(repositoryWithShrunkCatalog.activeProfileOrNull())
    }

    private fun minimalProfile(isoCode: String): CountryProfile {
        val bin = BinDefinition(
            id = BinId("black"),
            displayName = "Caneca negra",
            colorHex = "#000000",
            route = DisposalRoute.NON_RECYCLABLE,
        )
        return CountryProfile(
            isoCode = isoCode,
            regulationName = "Norma de prueba",
            regulationReference = "Perfil sintético para pruebas — no citable",
            bins = listOf(bin),
            rules = emptyList(),
            inspectionRules = emptyList(),
            conservativeBin = bin.id,
        )
    }
}
