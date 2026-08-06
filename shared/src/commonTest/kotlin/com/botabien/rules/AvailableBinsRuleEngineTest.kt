package com.botabien.rules

import com.botabien.domain.model.BinDefinition
import com.botabien.domain.model.BinId
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.DisposalRoute
import com.botabien.domain.model.FallbackReason
import com.botabien.domain.model.MaterialRule
import com.botabien.domain.model.WasteMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Restricción a canecas disponibles con respaldo conservador (S32, RF-008,
 * CUS-002/CUS-003): la recomendación se limita a lo que existe en el entorno,
 * la alternativa es la más conservadora, la reasignación se señala con
 * [FallbackReason.UNAVAILABLE_BIN] y el aviso del perfil viaja renderizado en
 * `Disposal.unavailableBinNotice` (#61/#78). Cubre los escenarios de una, dos
 * y tres canecas y la exención de la recolección especial (#54).
 */
class AvailableBinsRuleEngineTest {

    private val engine = DefaultRuleEngine()
    private val profile = RuleProfileFixtures.threeBins

    private val white = RuleProfileFixtures.whiteBin
    private val green = RuleProfileFixtures.greenBin
    private val black = RuleProfileFixtures.blackBin

    /** Caneca que no pertenece al perfil, para el caso de identificadores ajenos. */
    private val foreign = BinDefinition(
        id = BinId("blue"),
        displayName = "Caneca ajena al perfil",
        colorHex = "#1565C0",
        route = DisposalRoute.RECYCLABLE,
    )

    private fun resolve(
        material: WasteMaterial,
        vararg available: BinDefinition,
        contamination: ContaminationState = ContaminationState.CLEAN,
    ) = engine.resolve(material, contamination, available.map { it.id }.toSet(), profile)

    @Test
    fun conLasTresCanecasDisponiblesSeRespetaLaIdealSinAviso() {
        val disposal = resolve(WasteMaterial.PLASTIC, white, green, black)

        assertEquals(white, disposal.bin)
        assertEquals(FallbackReason.NONE, disposal.fallbackReason)
        assertNull(disposal.unavailableBinNotice)
        assertEquals(RuleProfileFixtures.plasticRule.justification, disposal.justification)
    }

    @Test
    fun conDosCanecasSinLaIdealSePropoLaConservadoraYSeInformaElMotivo() {
        val disposal = resolve(WasteMaterial.PLASTIC, green, black)

        assertEquals(black, disposal.bin)
        assertEquals(FallbackReason.UNAVAILABLE_BIN, disposal.fallbackReason)
        assertEquals(
            "No hay ${white.displayName} disponible; usa ${black.displayName}.",
            disposal.unavailableBinNotice,
            "El aviso aprobado nombra la caneca ideal y la asignada",
        )
        assertEquals(
            RuleProfileFixtures.plasticRule.justification,
            disposal.justification,
            "La justificación citada se conserva pura: el aviso viaja aparte",
        )
    }

    @Test
    fun conUnaSolaCanecaEsaEsLaRecomendacionAunqueNoSeaLaConservadora() {
        val disposal = resolve(WasteMaterial.PLASTIC, green)

        assertEquals(green, disposal.bin)
        assertEquals(DisposalRoute.ORGANIC, disposal.route)
        assertEquals(FallbackReason.UNAVAILABLE_BIN, disposal.fallbackReason)
        assertTrue(green.displayName in disposal.unavailableBinNotice.orEmpty())
    }

    @Test
    fun conSoloLaCanecaConservadoraTodoMaterialCaeEnElla() {
        WasteMaterial.entries.forEach { material ->
            val disposal = resolve(material, black)

            assertEquals(black, disposal.bin, "Única caneca para $material")
        }
    }

    @Test
    fun sinLaConservadoraDisponibleGanaLaRutaMasConservadora() {
        // RESIDUAL apunta a la negra, que no está; entre blanca (RECYCLABLE) y
        // verde (ORGANIC) el ranking conservador prefiere la blanca: la corriente
        // orgánica es la más sensible a un residuo mal ubicado.
        val disposal = resolve(WasteMaterial.RESIDUAL, white, green)

        assertEquals(white, disposal.bin)
        assertEquals(FallbackReason.UNAVAILABLE_BIN, disposal.fallbackReason)
    }

    @Test
    fun losIdentificadoresAjenosAlPerfilSeIgnoranComoSinRestriccion() {
        val disposal = resolve(WasteMaterial.PLASTIC, foreign)

        assertEquals(white, disposal.bin)
        assertEquals(FallbackReason.NONE, disposal.fallbackReason)
        assertNull(disposal.unavailableBinNotice)
    }

    @Test
    fun unMaterialSinReglaRestringidoTambienRecibeElAviso() {
        val disposal = resolve(WasteMaterial.GLASS, green)

        assertEquals(green, disposal.bin)
        assertEquals(profile.regulationReference, disposal.justification)
        assertEquals(FallbackReason.UNAVAILABLE_BIN, disposal.fallbackReason)
        assertTrue(green.displayName in disposal.unavailableBinNotice.orEmpty())
    }

    @Test
    fun laDegradacionPorContaminacionYLaRestriccionSeComponen() {
        val disposal = resolve(
            WasteMaterial.BEVERAGE_CARTON,
            green,
            black,
            contamination = ContaminationState.CONTAMINATED,
        )

        assertEquals(black, disposal.bin, "La alternativa por contaminación está disponible: se usa directa")
        assertTrue(disposal.degradedByContamination)
        assertEquals(
            FallbackReason.CONTAMINATION,
            disposal.fallbackReason,
            "La caneca asignada es la ideal degradada: no hubo reasignación por disponibilidad",
        )
        assertNull(disposal.unavailableBinNotice)
    }

    @Test
    fun unPerfilSinAvisoDeclaradoReasignaSinTexto() {
        val silentProfile = profile.copy(unavailableBinNotice = "")

        val disposal = engine.resolve(
            WasteMaterial.PLASTIC,
            ContaminationState.CLEAN,
            setOf(green.id, black.id),
            silentProfile,
        )

        assertEquals(black, disposal.bin)
        assertEquals(FallbackReason.UNAVAILABLE_BIN, disposal.fallbackReason, "La señal no depende del texto")
        assertNull(disposal.unavailableBinNotice, "Sin plantilla no hay aviso que mostrar")
    }

    @Test
    fun laCanecaDeRecoleccionEspecialQuedaExentaDeLaRestriccion() {
        val special = BinDefinition(
            id = BinId("special"),
            displayName = "Punto de recolección especial",
            colorHex = "#795548",
            route = DisposalRoute.SPECIAL_COLLECTION,
        )
        val withSpecial = profile.copy(
            bins = profile.bins + special,
            rules = profile.rules + MaterialRule(
                material = WasteMaterial.BATTERY,
                targetBin = special.id,
                contaminatedFallback = null,
                justification = "Las pilas van al punto de recolección posconsumo",
            ),
        )

        // Solo la negra está registrada en el entorno: la recomendación del
        // punto especial no se degrada porque no es una caneca del entorno (#54).
        val disposal = engine.resolve(
            WasteMaterial.BATTERY,
            ContaminationState.CLEAN,
            setOf(black.id),
            withSpecial,
        )

        assertEquals(special, disposal.bin)
        assertEquals(FallbackReason.NONE, disposal.fallbackReason)
        assertNull(disposal.unavailableBinNotice)
        assertFalse(disposal.degradedByContamination)
    }
}
