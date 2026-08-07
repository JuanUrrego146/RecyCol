package com.botabien.rules

import com.botabien.domain.model.BinId
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.DisposalRoute
import com.botabien.domain.model.FallbackReason
import com.botabien.domain.model.WasteMaterial
import com.botabien.rules.profile.ProfileParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Batería del motor de reglas sobre el perfil oficial de Colombia (S29, RF-012).
 *
 * El perfil se lee del catálogo real (`resources/profiles/co.json`) con el
 * cargador de producción de S30.
 */
class ColombiaRuleEngineTest {

    private val engine = DefaultRuleEngine()
    private val profile = File("resources/profiles/co.json")
        .let { ProfileParser.parseProfile(it.name, it.readText()) }

    private val white = BinId("white")
    private val black = BinId("black")
    private val green = BinId("green")
    private val special = BinId("special")

    private val recyclables = listOf(
        WasteMaterial.PLASTIC,
        WasteMaterial.PAPER,
        WasteMaterial.CARDBOARD,
        WasteMaterial.BEVERAGE_CARTON,
        WasteMaterial.GLASS,
        WasteMaterial.METAL,
    )

    private fun resolve(
        material: WasteMaterial,
        contamination: ContaminationState = ContaminationState.CLEAN,
        availableBins: Set<BinId> = emptySet(),
    ) = engine.resolve(material, contamination, availableBins, profile)

    @Test
    fun cadaMaterialLimpioVaALaCanecaQueDeclaraLaResolucion2184() {
        val expected = mapOf(
            WasteMaterial.PLASTIC to white,
            WasteMaterial.PAPER to white,
            WasteMaterial.CARDBOARD to white,
            WasteMaterial.BEVERAGE_CARTON to white,
            WasteMaterial.GLASS to white,
            WasteMaterial.METAL to white,
            WasteMaterial.ORGANIC to green,
            WasteMaterial.TEXTILE to black,
            // Pilas y RAEE van al punto de recolección posconsumo, fuera del
            // código de colores (decisión de v1, coordinación #54).
            WasteMaterial.BATTERY to special,
            WasteMaterial.ELECTRONIC to special,
            WasteMaterial.RESIDUAL to black,
        )

        assertEquals(WasteMaterial.entries.toSet(), expected.keys, "La tabla esperada cubre todo el vocabulario")

        expected.forEach { (material, bin) ->
            assertEquals(bin, resolve(material).bin.id, "Destino limpio de $material")
        }
    }

    @Test
    fun losReciclablesContaminadosSeDegradanALaCanecaNegra() {
        recyclables.forEach { material ->
            val disposal = resolve(material, ContaminationState.CONTAMINATED)

            assertEquals(black, disposal.bin.id, "Destino contaminado de $material")
            assertEquals(DisposalRoute.NON_RECYCLABLE, disposal.route)
            assertTrue(disposal.degradedByContamination, "$material contaminado debe marcarse degradado")
        }
    }

    @Test
    fun elVasoDeCartonLimpioVaALaBlancaYConResiduoALaNegra() {
        val clean = resolve(WasteMaterial.BEVERAGE_CARTON, ContaminationState.CLEAN)
        val contaminated = resolve(WasteMaterial.BEVERAGE_CARTON, ContaminationState.CONTAMINATED)

        assertEquals(white, clean.bin.id, "Limpio y seco: caneca blanca")
        assertEquals(DisposalRoute.RECYCLABLE, clean.route)
        assertFalse(clean.degradedByContamination)

        assertEquals(black, contaminated.bin.id, "Con residuo de bebida: caneca negra")
        assertEquals(DisposalRoute.NON_RECYCLABLE, contaminated.route)
        assertTrue(contaminated.degradedByContamination)
    }

    @Test
    fun elOrganicoContaminadoSigueEnLaCanecaVerde() {
        val disposal = resolve(WasteMaterial.ORGANIC, ContaminationState.CONTAMINATED)

        assertEquals(green, disposal.bin.id)
        assertFalse(disposal.degradedByContamination, "La regla no declara alternativa: no hay degradación")
    }

    @Test
    fun laJustificacionSiempreEsLaDeclaradaEnElPerfil() {
        WasteMaterial.entries.forEach { material ->
            val rule = profile.rules.single { it.material == material }
            val disposal = resolve(material)

            assertEquals(rule.justification, disposal.justification, "Justificación de $material")
            assertTrue(disposal.justification.isNotBlank())
        }
    }

    @Test
    fun sinLaCanecaBlancaDisponibleUnReciclableCaeEnLaNegra() {
        val disposal = resolve(WasteMaterial.PLASTIC, availableBins = setOf(black, green))

        assertEquals(black, disposal.bin.id)
        assertEquals(DisposalRoute.NON_RECYCLABLE, disposal.route)
    }

    @Test
    fun elAvisoAprobadoExplicaPorQueNoSeRecomendoLaCanecaIdeal() {
        val restricted = resolve(WasteMaterial.PLASTIC, availableBins = setOf(black, green))
        val unrestricted = resolve(WasteMaterial.PLASTIC)

        assertEquals(unrestricted.justification, restricted.justification, "La regla citada se conserva pura")
        assertEquals(FallbackReason.UNAVAILABLE_BIN, restricted.fallbackReason)
        assertEquals(
            "No hay Caneca blanca disponible; usa Caneca negra.",
            restricted.unavailableBinNotice,
            "Frase aprobada el 06/08/2026 (#61/#78), renderizada con los nombres del perfil",
        )
    }

    @Test
    fun elVasoDeCartonNoVerificadoVaALaNegraPorPrecaucion() {
        val disposal = resolve(WasteMaterial.BEVERAGE_CARTON, ContaminationState.UNKNOWN)

        assertEquals(black, disposal.bin.id, "Sin vista interior verificada el sistema no adivina")
        assertTrue(disposal.degradedByContamination)
    }

    @Test
    fun soloElCartonParaBebidasRequiereInspeccionEnColombia() {
        val requiring = WasteMaterial.entries.filter { profile.requiresInspection(it) }

        assertEquals(listOf(WasteMaterial.BEVERAGE_CARTON), requiring)

        // Para el resto, el estado desconocido resuelve igual que limpio.
        (WasteMaterial.entries - WasteMaterial.BEVERAGE_CARTON).forEach { material ->
            assertEquals(
                resolve(material, ContaminationState.CLEAN).bin.id,
                resolve(material, ContaminationState.UNKNOWN).bin.id,
                "Destino desconocido de $material",
            )
        }
    }
}
