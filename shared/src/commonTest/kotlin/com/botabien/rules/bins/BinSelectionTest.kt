package com.botabien.rules.bins

import com.botabien.domain.model.BinDefinition
import com.botabien.domain.model.BinId
import com.botabien.domain.model.CountryProfile
import com.botabien.domain.model.DetectedBin
import com.botabien.domain.model.DisposalRoute
import com.botabien.domain.model.ImageFrame
import com.botabien.domain.port.BinAvailabilityRepository
import com.botabien.domain.port.BinDetector
import com.botabien.domain.port.ProfileRepository
import com.botabien.domain.usecase.RecognizedBin
import com.botabien.domain.usecase.ScanBinsUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Confirmación y edición manual de la selección de canecas (S35, RF-007,
 * CUS-002): la propuesta sale del reconocimiento, el usuario decide y la
 * selección confirmada se persiste a través de `ScanBinsUseCase.confirm`
 * (#49), nunca directo al repositorio.
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

    private fun recognized(vararg bins: BinDefinition): List<RecognizedBin> =
        bins.map { RecognizedBin(definition = it, confidence = 0.9f) }

    @Test
    fun elReconocimientoProponeLasCanecasEmparejadas() {
        val selection = BinSelection.fromRecognized(recognized(white, green), profile)

        assertEquals(setOf(white.id, green.id), selection.selected)
        assertEquals(listOf(white, green), selection.selectedBins, "En el orden del perfil")
        assertEquals(listOf(black), selection.addable)
    }

    @Test
    fun elResultadoDelEmparejamientoPorColorTambienSirveDePropuesta() {
        val scan = BinScanResult(
            matches = listOf(BinMatch(DetectedBin(white.colorHex, 0.9f), white, colorDistance = 0.02f)),
            unmatched = emptyList(),
        )

        val selection = BinSelection.fromScan(scan, profile)

        assertEquals(setOf(white.id), selection.selected)
    }

    @Test
    fun elUsuarioAgregaYEliminaCanecasManualmenteDesdeElPerfil() {
        val selection = BinSelection.fromRecognized(recognized(white), profile)
            .add(black.id)
            .remove(white.id)

        assertEquals(setOf(black.id), selection.selected)
        assertEquals(listOf(white, green), selection.addable)
    }

    @Test
    fun unIdentificadorAjenoAlPerfilSeIgnora() {
        val selection = BinSelection.fromRecognized(recognized(white), profile).add(BinId("blue"))

        assertEquals(setOf(white.id), selection.selected)
    }

    @Test
    fun sinCanecasReconocidasLaPropuestaEstaVaciaYNoConfirmable() {
        val selection = BinSelection.fromRecognized(emptyList(), profile)

        assertTrue(selection.selected.isEmpty())
        assertFalse(selection.canConfirm, "El conjunto vacío está reservado para «sin restricción»")
        assertEquals(profile.bins, selection.addable, "Todas quedan disponibles para añadir a mano")
    }

    @Test
    fun omitirElEscaneoAsumeTodasLasCanecasDelPerfil() {
        val selection = BinSelection.allOf(profile)

        assertEquals(setOf(white.id, black.id, green.id), selection.selected)
        assertTrue(selection.canConfirm)
    }

    @Test
    fun losDestinosDeRecoleccionEspecialQuedanFueraDeLaSeleccion() {
        val special = BinDefinition(
            BinId("special"),
            "Punto de recolección especial",
            "#795548",
            DisposalRoute.SPECIAL_COLLECTION,
        )
        val withSpecial = profile.copy(bins = profile.bins + special)

        // No es una caneca del entorno (#54): ni se propone, ni se puede
        // añadir a mano, ni entra al omitir el escaneo.
        val fromScan = BinSelection.fromRecognized(
            listOf(RecognizedBin(definition = special, confidence = 0.9f)),
            withSpecial,
        )
        assertTrue(fromScan.selected.isEmpty())

        val added = BinSelection(withSpecial, emptySet()).add(special.id)
        assertTrue(added.selected.isEmpty())
        assertTrue(BinSelection(withSpecial, emptySet()).addable.none { it.id == special.id })

        assertEquals(
            setOf(white.id, black.id, green.id),
            BinSelection.allOf(withSpecial).selected,
            "Omitir el escaneo asume solo las canecas físicas",
        )
    }

    @Test
    fun laSeleccionEditadaSePersisteATravesDelCasoDeUso() = runTest {
        val repository = RecordingRepository()
        val useCase = ScanBinsUseCase(
            detector = NoOpDetector,
            profiles = FixedProfileRepository(profile),
            binAvailability = repository,
        )
        val selection = BinSelection.fromRecognized(recognized(white, green), profile)
            .add(black.id)
            .remove(green.id)

        useCase.confirm(selection.selected)

        assertEquals(setOf(white.id, black.id), repository.saved)
    }

    private class RecordingRepository : BinAvailabilityRepository {
        var saved: Set<BinId>? = null

        override suspend fun availableBins(): Set<BinId> = saved.orEmpty()

        override suspend fun saveAvailableBins(bins: Set<BinId>) {
            saved = bins
        }
    }

    private class FixedProfileRepository(private val profile: CountryProfile) : ProfileRepository {
        override suspend fun availableProfiles(): List<CountryProfile> = listOf(profile)

        override suspend fun activeProfileOrNull(): CountryProfile = profile

        override suspend fun setActiveProfile(isoCode: String) = Unit
    }

    private object NoOpDetector : BinDetector {
        override suspend fun detectBins(frame: ImageFrame): List<DetectedBin> = emptyList()
    }
}
