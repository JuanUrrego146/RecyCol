package com.recycol.rules

import com.recycol.domain.model.BinId
import com.recycol.domain.model.ContaminationState
import com.recycol.domain.model.DisposalRoute
import com.recycol.domain.model.WasteMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Semántica del motor de reglas (S29, RF-012, CUS-003) sobre perfiles en
 * memoria. La batería contra el perfil oficial de Colombia vive en
 * `jvmTest/ColombiaRuleEngineTest`.
 */
class DefaultRuleEngineTest {

    private val engine = DefaultRuleEngine()
    private val profile = RuleProfileFixtures.threeBins

    private fun resolve(
        material: WasteMaterial,
        contamination: ContaminationState = ContaminationState.CLEAN,
        availableBins: Set<BinId> = emptySet(),
    ) = engine.resolve(material, contamination, availableBins, profile)

    @Test
    fun unMaterialLimpioVaASuCanecaObjetivoConLaJustificacionDeLaRegla() {
        val disposal = resolve(WasteMaterial.PLASTIC)

        assertEquals(RuleProfileFixtures.whiteBin, disposal.bin)
        assertEquals(DisposalRoute.RECYCLABLE, disposal.route)
        assertEquals(RuleProfileFixtures.plasticRule.justification, disposal.justification)
        assertFalse(disposal.degradedByContamination)
    }

    @Test
    fun unMaterialContaminadoSeDegradaALaCanecaAlternativaDeLaRegla() {
        val disposal = resolve(WasteMaterial.PLASTIC, ContaminationState.CONTAMINATED)

        assertEquals(RuleProfileFixtures.blackBin, disposal.bin)
        assertEquals(DisposalRoute.NON_RECYCLABLE, disposal.route)
        assertTrue(disposal.degradedByContamination)
    }

    @Test
    fun elVasoDeCartonLimpioVaALaBlancaYContaminadoALaNegra() {
        val clean = resolve(WasteMaterial.BEVERAGE_CARTON, ContaminationState.CLEAN)
        val contaminated = resolve(WasteMaterial.BEVERAGE_CARTON, ContaminationState.CONTAMINATED)

        assertEquals(RuleProfileFixtures.whiteBin, clean.bin)
        assertFalse(clean.degradedByContamination)
        assertEquals(RuleProfileFixtures.blackBin, contaminated.bin)
        assertTrue(contaminated.degradedByContamination)
        assertEquals(RuleProfileFixtures.beverageCartonRule.justification, contaminated.justification)
    }

    @Test
    fun laContaminacionSinCanecaAlternativaNoCambiaElDestinoNiDegrada() {
        val disposal = resolve(WasteMaterial.ORGANIC, ContaminationState.CONTAMINATED)

        assertEquals(RuleProfileFixtures.greenBin, disposal.bin)
        assertFalse(disposal.degradedByContamination)
    }

    @Test
    fun unMaterialSinReglaCaeEnLaCanecaConservadoraCitandoLaNorma() {
        val disposal = resolve(WasteMaterial.GLASS)

        assertEquals(RuleProfileFixtures.blackBin, disposal.bin)
        assertEquals(profile.regulationReference, disposal.justification)
        assertFalse(disposal.degradedByContamination)
    }

    @Test
    fun unEstadoDesconocidoSinReglaDeInspeccionResuelveComoLimpio() {
        val disposal = resolve(WasteMaterial.PLASTIC, ContaminationState.UNKNOWN)

        assertEquals(RuleProfileFixtures.whiteBin, disposal.bin)
        assertFalse(disposal.degradedByContamination)
    }

    @Test
    fun unConjuntoVacioDeCanecasSignificaSinRestriccion() {
        val disposal = resolve(WasteMaterial.PLASTIC, availableBins = emptySet())

        assertEquals(RuleProfileFixtures.whiteBin, disposal.bin)
    }

    @Test
    fun laCanecaIdealDisponibleSeRespeta() {
        val disposal = resolve(
            WasteMaterial.PLASTIC,
            availableBins = setOf(RuleProfileFixtures.whiteBin.id, RuleProfileFixtures.blackBin.id),
        )

        assertEquals(RuleProfileFixtures.whiteBin, disposal.bin)
    }

    @Test
    fun sinLaCanecaIdealDisponibleSeCaeEnLaConservadora() {
        val disposal = resolve(
            WasteMaterial.PLASTIC,
            availableBins = setOf(RuleProfileFixtures.greenBin.id, RuleProfileFixtures.blackBin.id),
        )

        assertEquals(RuleProfileFixtures.blackBin, disposal.bin)
        assertEquals(DisposalRoute.NON_RECYCLABLE, disposal.route)
    }

    @Test
    fun laRutaDeLaDecisionSiempreEsLaDeLaCanecaResuelta() {
        val degraded = resolve(WasteMaterial.BEVERAGE_CARTON, ContaminationState.CONTAMINATED)

        assertEquals(degraded.bin.route, degraded.route)
    }
}
