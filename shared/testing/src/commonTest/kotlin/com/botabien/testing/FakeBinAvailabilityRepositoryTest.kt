package com.botabien.testing

import com.botabien.domain.model.BinId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Documenta el contrato del fake de `BinAvailabilityRepository`: vacío por
 * defecto («sin restricción») y persistencia en memoria de la confirmación.
 */
class FakeBinAvailabilityRepositoryTest {

    @Test
    fun porDefectoNoHayRestriccionDeCanecas() = runTest {
        val repository = FakeBinAvailabilityRepository()

        assertTrue(repository.availableBins().isEmpty())
    }

    @Test
    fun guardarLaSeleccionLaDevuelveIntacta() = runTest {
        val repository = FakeBinAvailabilityRepository()
        val selection = setOf(BinId("white"), BinId("black"))

        repository.saveAvailableBins(selection)

        assertEquals(selection, repository.availableBins())
    }
}
