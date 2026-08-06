package com.botabien.rules

import com.botabien.domain.model.BinId
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.DisposalRoute
import com.botabien.domain.model.WasteMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Restricción a canecas disponibles con respaldo conservador (S32, RF-008,
 * CUS-002/CUS-003): la recomendación se limita a lo que existe en el entorno,
 * la alternativa es la más conservadora y el aviso del perfil explica por qué
 * no se recomendó la ideal. Cubre los escenarios de una, dos y tres canecas.
 */
class AvailableBinsRuleEngineTest {

    private val engine = DefaultRuleEngine()
    private val profile = RuleProfileFixtures.threeBins

    private val white = RuleProfileFixtures.whiteBin
    private val green = RuleProfileFixtures.greenBin
    private val black = RuleProfileFixtures.blackBin

    private fun resolve(
        material: WasteMaterial,
        vararg available: BinId,
        contamination: ContaminationState = ContaminationState.CLEAN,
    ) = engine.resolve(material, contamination, available.toSet(), profile)

    @Test
    fun conLasTresCanecasDisponiblesSeRespetaLaIdealSinAviso() {
        val disposal = resolve(WasteMaterial.PLASTIC, white.id, green.id, black.id)

        assertEquals(white, disposal.bin)
        assertEquals(RuleProfileFixtures.plasticRule.justification, disposal.justification, "Sin aviso: la ideal está disponible")
    }

    @Test
    fun conDosCanecasSinLaIdealSePropoLaConservadoraYSeInformaElMotivo() {
        val disposal = resolve(WasteMaterial.PLASTIC, green.id, black.id)

        assertEquals(black, disposal.bin)
        assertTrue(disposal.justification.startsWith(RuleProfileFixtures.plasticRule.justification))
        assertTrue(white.displayName in disposal.justification, "El aviso nombra la caneca ideal")
        assertTrue(black.displayName in disposal.justification, "El aviso nombra la caneca asignada")
    }

    @Test
    fun conUnaSolaCanecaEsaEsLaRecomendacionAunqueNoSeaLaConservadora() {
        val disposal = resolve(WasteMaterial.PLASTIC, green.id)

        assertEquals(green, disposal.bin)
        assertEquals(DisposalRoute.ORGANIC, disposal.route)
        assertTrue(green.displayName in disposal.justification, "El aviso explica la asignación")
    }

    @Test
    fun conSoloLaCanecaConservadoraTodoMaterialCaeEnElla() {
        WasteMaterial.entries.forEach { material ->
            val disposal = resolve(material, black.id)

            assertEquals(black, disposal.bin, "Única caneca para $material")
        }
    }

    @Test
    fun sinLaConservadoraDisponibleGanaLaRutaMasConservadora() {
        // RESIDUAL apunta a la negra, que no está; entre blanca (RECYCLABLE) y
        // verde (ORGANIC) el ranking conservador prefiere la blanca: la corriente
        // orgánica es la más sensible a un residuo mal ubicado.
        val disposal = resolve(WasteMaterial.RESIDUAL, white.id, green.id)

        assertEquals(white, disposal.bin)
    }

    @Test
    fun losIdentificadoresAjenosAlPerfilSeIgnoranComoSinRestriccion() {
        val disposal = resolve(WasteMaterial.PLASTIC, BinId("blue"))

        assertEquals(white, disposal.bin)
        assertEquals(RuleProfileFixtures.plasticRule.justification, disposal.justification)
    }

    @Test
    fun unMaterialSinReglaRestringidoTambienRecibeElAviso() {
        val disposal = resolve(WasteMaterial.GLASS, green.id)

        assertEquals(green, disposal.bin)
        assertTrue(disposal.justification.startsWith(profile.regulationReference))
        assertTrue(green.displayName in disposal.justification)
    }

    @Test
    fun laDegradacionPorContaminacionYLaRestriccionSeComponen() {
        val disposal = resolve(
            WasteMaterial.BEVERAGE_CARTON,
            green.id,
            black.id,
            contamination = ContaminationState.CONTAMINATED,
        )

        assertEquals(black, disposal.bin, "La alternativa por contaminación está disponible: se usa directa")
        assertTrue(disposal.degradedByContamination)
        assertEquals(
            RuleProfileFixtures.beverageCartonRule.justification,
            disposal.justification,
            "La caneca asignada es la ideal degradada: no hay aviso de indisponibilidad",
        )
    }

    @Test
    fun unPerfilSinAvisoDeclaradoNoAlteraLaJustificacion() {
        val silentProfile = profile.copy(unavailableBinNotice = "")

        val disposal = engine.resolve(
            WasteMaterial.PLASTIC,
            ContaminationState.CLEAN,
            setOf(green.id, black.id),
            silentProfile,
        )

        assertEquals(black, disposal.bin)
        assertEquals(RuleProfileFixtures.plasticRule.justification, disposal.justification)
    }
}
