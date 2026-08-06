package com.botabien.testing

import com.botabien.domain.model.BinId
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.DisposalRoute
import com.botabien.domain.model.FallbackReason
import com.botabien.domain.model.WasteMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Documenta la semántica mínima del fake de `RuleEngine`, incluido el caso
 * emblema del proyecto: el cartón para bebidas limpio va a la caneca blanca
 * y contaminado se degrada a la negra.
 */
class FakeRuleEngineTest {

    private val engine = FakeRuleEngine()
    private val profile = TestProfiles.threeBins

    @Test
    fun cartonParaBebidasLimpioVaALaCanecaBlanca() {
        val disposal = engine.resolve(
            material = WasteMaterial.BEVERAGE_CARTON,
            contamination = ContaminationState.CLEAN,
            availableBins = emptySet(),
            profile = profile,
        )

        assertEquals(TestProfiles.whiteBin, disposal.bin)
        assertEquals(DisposalRoute.RECYCLABLE, disposal.route)
        assertFalse(disposal.degradedByContamination)
    }

    @Test
    fun cartonParaBebidasContaminadoSeDegradaALaCanecaNegra() {
        val disposal = engine.resolve(
            material = WasteMaterial.BEVERAGE_CARTON,
            contamination = ContaminationState.CONTAMINATED,
            availableBins = emptySet(),
            profile = profile,
        )

        assertEquals(TestProfiles.blackBin, disposal.bin)
        assertEquals(DisposalRoute.NON_RECYCLABLE, disposal.route)
        assertTrue(disposal.degradedByContamination)
    }

    @Test
    fun materialSinReglaCaeEnLaCanecaConservadora()  {
        val disposal = engine.resolve(
            material = WasteMaterial.TEXTILE,
            contamination = ContaminationState.UNKNOWN,
            availableBins = emptySet(),
            profile = profile,
        )

        assertEquals(profile.conservativeBin, disposal.bin.id)
        assertFalse(disposal.degradedByContamination)
    }

    @Test
    fun siLaCanecaIdealNoEstaDisponibleSeProponeLaConservadora() {
        val onlyBlackAvailable = setOf(BinId("black"))

        val disposal = engine.resolve(
            material = WasteMaterial.PLASTIC,
            contamination = ContaminationState.CLEAN,
            availableBins = onlyBlackAvailable,
            profile = profile,
        )

        assertEquals(TestProfiles.blackBin, disposal.bin)
    }

    @Test
    fun conjuntoVacioDeCanecasSignificaSinRestriccion() {
        val disposal = engine.resolve(
            material = WasteMaterial.ORGANIC,
            contamination = ContaminationState.UNKNOWN,
            availableBins = emptySet(),
            profile = profile,
        )

        assertEquals(TestProfiles.greenBin, disposal.bin)
    }

    @Test
    fun electronicoVaAlPuntoDeRecoleccionEspecialAunqueNoEsteDisponible() {
        val onlyBlackNearby = setOf(TestProfiles.blackBin.id)

        val disposal = engine.resolve(
            material = WasteMaterial.ELECTRONIC,
            contamination = ContaminationState.UNKNOWN,
            availableBins = onlyBlackNearby,
            profile = profile,
        )

        assertEquals(TestProfiles.specialCollectionBin, disposal.bin, "La recolección especial no se degrada por disponibilidad (#54)")
        assertEquals(FallbackReason.NONE, disposal.fallbackReason)
    }

    @Test
    fun lasPilasRecibenElMismoTratoQueLosElectronicos() {
        val disposal = engine.resolve(
            material = WasteMaterial.BATTERY,
            contamination = ContaminationState.UNKNOWN,
            availableBins = setOf(TestProfiles.whiteBin.id),
            profile = profile,
        )

        assertEquals(TestProfiles.specialCollectionBin, disposal.bin)
    }

    @Test
    fun laContaminacionSeReportaComoMotivoDelCambio() {
        val disposal = engine.resolve(
            material = WasteMaterial.BEVERAGE_CARTON,
            contamination = ContaminationState.CONTAMINATED,
            availableBins = emptySet(),
            profile = profile,
        )

        assertEquals(FallbackReason.CONTAMINATION, disposal.fallbackReason)
    }

    @Test
    fun laCanecaAusenteSeReportaComoMotivoDelCambio() {
        val disposal = engine.resolve(
            material = WasteMaterial.PLASTIC,
            contamination = ContaminationState.CLEAN,
            availableBins = setOf(TestProfiles.blackBin.id),
            profile = profile,
        )

        assertEquals(TestProfiles.blackBin, disposal.bin)
        assertEquals(FallbackReason.UNAVAILABLE_BIN, disposal.fallbackReason)
    }
}
