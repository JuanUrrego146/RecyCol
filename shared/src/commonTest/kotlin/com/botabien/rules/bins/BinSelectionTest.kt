package com.botabien.rules.bins

import com.botabien.domain.model.BinDefinition
import com.botabien.domain.model.BinId
import com.botabien.domain.model.CountryProfile
import com.botabien.domain.model.DetectedBin
import com.botabien.domain.model.DisposalRoute
import com.botabien.domain.port.BinAvailabilityRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Confirmación y edición manual de la selección de canecas (S35, RF-007,
 * CUS-002): la propuesta sale del escaneo, el usuario decide, la selección
 * confirmada se persiste y omitir el escaneo asume todas las del perfil.
 */
class BinSelectionTest {

    private val white = BinDefinition(BinId("white"), "Caneca blanca", "#FFFFFF", DisposalRoute.RECYCLABLE)
    private val black = BinDefinition(BinId("black"), "Caneca negra", "#1C1C1C", DisposalRoute.NON_RECYCLABLE)
    private val green = BinDefinition(BinId("green"), "Caneca verde", "#2E7D32", DisposalRoute.ORGANIC)

    private val profile = CountryProfile(
        isoCode = "zz",
        regulationName = "Perfil de prueba",
        regulationReference = "Perfil sintético para pruebas — no citable",
        bins = listOf(white, black, green),
        rules = emptyList(),
        inspectionRules = emptyList(),
        conservativeBin = black.id,
    )

    private fun scanWith(vararg bins: BinDefinition) = BinScanResult(
        matches = bins.map { bin ->
            BinMatch(DetectedBin(bin.colorHex, 0.9f), bin, colorDistance = 0.02f)
        },
        unmatched = emptyList(),
    )

    private class RecordingRepository : BinAvailabilityRepository {
        var saved: Set<BinId>? = null

        override suspend fun availableBins(): Set<BinId> = saved.orEmpty()

        override suspend fun saveAvailableBins(bins: Set<BinId>) {
            saved = bins
        }
    }

    @Test
    fun elEscaneoProponeLasCanecasReconocidas() {
        val selection = BinSelection.fromScan(scanWith(white, green), profile)

        assertEquals(setOf(white.id, green.id), selection.selected)
        assertEquals(listOf(white, green), selection.selectedBins, "En el orden del perfil")
        assertEquals(listOf(black), selection.addable)
    }

    @Test
    fun elUsuarioAgregaYEliminaCanecasManualmenteDesdeElPerfil() {
        val selection = BinSelection.fromScan(scanWith(white), profile)
            .add(black.id)
            .remove(white.id)

        assertEquals(setOf(black.id), selection.selected)
        assertEquals(listOf(white, green), selection.addable)
    }

    @Test
    fun unIdentificadorAjenoAlPerfilSeIgnora() {
        val selection = BinSelection.fromScan(scanWith(white), profile).add(BinId("blue"))

        assertEquals(setOf(white.id), selection.selected)
    }

    @Test
    fun sinCanecasReconocidasLaPropuestaEstaVaciaYNoConfirmable() {
        val selection = BinSelection.fromScan(scanWith(), profile)

        assertTrue(selection.selected.isEmpty())
        assertFalse(selection.canConfirm)
        assertEquals(profile.bins, selection.addable, "Todas quedan disponibles para añadir a mano")
    }

    @Test
    fun omitirElEscaneoAsumeTodasLasCanecasDelPerfil() {
        val selection = BinSelection.allOf(profile)

        assertEquals(setOf(white.id, black.id, green.id), selection.selected)
        assertTrue(selection.canConfirm)
    }

    @Test
    fun laSeleccionConfirmadaSePersisteEnElRepositorio() = runTest {
        val repository = RecordingRepository()
        val selection = BinSelection.fromScan(scanWith(white, green), profile).add(black.id)

        selection.persistTo(repository)

        assertEquals(setOf(white.id, green.id, black.id), repository.saved)
        assertEquals(selection.selected, repository.availableBins())
    }

    @Test
    fun confirmarUnaSeleccionVaciaEsUnErrorExplicitoYNoPersisteNada() = runTest {
        val repository = RecordingRepository()
        val selection = BinSelection.fromScan(scanWith(), profile)

        assertFailsWith<IllegalArgumentException> { selection.persistTo(repository) }
        assertNull(repository.saved, "El conjunto vacío está reservado para «sin restricción»")
    }
}
