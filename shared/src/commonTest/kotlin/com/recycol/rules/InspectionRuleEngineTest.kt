package com.recycol.rules

import com.recycol.domain.model.ContaminationState
import com.recycol.domain.model.DisposalRoute
import com.recycol.domain.model.InspectionRule
import com.recycol.domain.model.WasteMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reglas de inspección y reclasificación por contaminación (S31, RF-019,
 * RF-022, CUS-005): el perfil declara qué materiales exigen inspección; el
 * motor reclasifica al confirmarse contaminación y aplica la ruta conservadora
 * cuando el interior no se pudo verificar. El sistema no adivina.
 */
class InspectionRuleEngineTest {

    private val engine = DefaultRuleEngine()
    private val profile = RuleProfileFixtures.threeBins

    @Test
    fun unReciclableConfirmadoContaminadoSeReasignaALaCanecaDeNoAprovechablesConJustificacion() {
        val disposal = engine.resolve(
            WasteMaterial.BEVERAGE_CARTON,
            ContaminationState.CONTAMINATED,
            emptySet(),
            profile,
        )

        assertEquals(RuleProfileFixtures.blackBin, disposal.bin)
        assertEquals(DisposalRoute.NON_RECYCLABLE, disposal.route)
        assertTrue(disposal.degradedByContamination)
        assertEquals(RuleProfileFixtures.beverageCartonRule.justification, disposal.justification)
    }

    @Test
    fun unInteriorNoVerificadoAplicaLaRutaConservadoraSinAdivinar() {
        val disposal = engine.resolve(
            WasteMaterial.BEVERAGE_CARTON,
            ContaminationState.UNKNOWN,
            emptySet(),
            profile,
        )

        assertEquals(RuleProfileFixtures.blackBin, disposal.bin, "Ante la duda, la caneca conservadora")
        assertTrue(disposal.degradedByContamination, "La duda no descartada se marca como degradación")
    }

    @Test
    fun laInspeccionLimpiaRecuperaLaCanecaObjetivo() {
        val disposal = engine.resolve(
            WasteMaterial.BEVERAGE_CARTON,
            ContaminationState.CLEAN,
            emptySet(),
            profile,
        )

        assertEquals(RuleProfileFixtures.whiteBin, disposal.bin)
        assertFalse(disposal.degradedByContamination)
    }

    @Test
    fun unMaterialSinReglaDeInspeccionNoSeDegradaPorEstadoDesconocido() {
        val disposal = engine.resolve(
            WasteMaterial.PLASTIC,
            ContaminationState.UNKNOWN,
            emptySet(),
            profile,
        )

        assertEquals(RuleProfileFixtures.whiteBin, disposal.bin)
        assertFalse(disposal.degradedByContamination)
    }

    @Test
    fun sinCanecaAlternativaLaInspeccionNoVerificadaNoCambiaElDestino() {
        val organicWithInspection = profile.copy(
            inspectionRules = profile.inspectionRules + InspectionRule(
                material = WasteMaterial.ORGANIC,
                promptKey = "inspection.show_contents",
                requiresInteriorView = false,
            ),
        )

        val disposal = engine.resolve(
            WasteMaterial.ORGANIC,
            ContaminationState.UNKNOWN,
            emptySet(),
            organicWithInspection,
        )

        assertEquals(RuleProfileFixtures.greenBin, disposal.bin, "Sin alternativa declarada no hay degradación posible")
        assertFalse(disposal.degradedByContamination)
    }

    @Test
    fun elPerfilDeclaraQueMaterialesRequierenInspeccionYConQueMensaje() {
        val rule = profile.inspectionRuleFor(WasteMaterial.BEVERAGE_CARTON)

        assertEquals("inspection.point_inside", rule?.promptKey)
        assertEquals(true, rule?.requiresInteriorView)
        assertTrue(profile.requiresInspection(WasteMaterial.BEVERAGE_CARTON))

        assertNull(profile.inspectionRuleFor(WasteMaterial.PLASTIC))
        assertFalse(profile.requiresInspection(WasteMaterial.PLASTIC))
    }
}
