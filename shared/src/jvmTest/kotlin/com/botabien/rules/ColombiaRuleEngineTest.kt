package com.botabien.rules

import com.botabien.domain.model.BinDefinition
import com.botabien.domain.model.BinId
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.CountryProfile
import com.botabien.domain.model.DisposalRoute
import com.botabien.domain.model.InspectionRule
import com.botabien.domain.model.MaterialRule
import com.botabien.domain.model.WasteMaterial
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Batería del motor de reglas sobre el perfil oficial de Colombia (S29, RF-012).
 *
 * El perfil se lee del catálogo real (`resources/profiles/co.json`) con un
 * cargador propio de esta prueba; el cargador de producción llega en S30 y
 * esta batería pasará a usarlo.
 */
class ColombiaRuleEngineTest {

    private val engine = DefaultRuleEngine()
    private val profile = loadProfile(File("resources/profiles/co.json"))

    private val white = BinId("white")
    private val black = BinId("black")
    private val green = BinId("green")

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
            WasteMaterial.BATTERY to black,
            WasteMaterial.ELECTRONIC to black,
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

    /**
     * Cargador mínimo de perfiles para esta batería. Falla la prueba con un
     * error claro si el perfil no tiene la forma esperada; la validación real
     * con mensajes de usuario llega en S30.
     */
    private fun loadProfile(file: File): CountryProfile {
        val root = Json.parseToJsonElement(file.readText()).jsonObject

        val bins = root.getValue("bins").jsonArray.map { element ->
            val bin = element.jsonObject
            BinDefinition(
                id = BinId(bin.getValue("id").jsonPrimitive.content),
                displayName = bin.getValue("displayName").jsonPrimitive.content,
                colorHex = bin.getValue("colorHex").jsonPrimitive.content,
                route = DisposalRoute.valueOf(bin.getValue("route").jsonPrimitive.content),
            )
        }

        val rules = root.getValue("rules").jsonArray.map { element ->
            val rule = element.jsonObject
            MaterialRule(
                material = WasteMaterial.valueOf(rule.getValue("material").jsonPrimitive.content),
                targetBin = BinId(rule.getValue("targetBin").jsonPrimitive.content),
                contaminatedFallback = rule["contaminatedFallback"]
                    ?.takeIf { it != JsonNull }
                    ?.let { BinId(it.jsonPrimitive.content) },
                justification = rule.getValue("justification").jsonPrimitive.content,
            )
        }

        val inspectionRules = root.getValue("inspectionRules").jsonArray.map { element ->
            val rule = element.jsonObject
            InspectionRule(
                material = WasteMaterial.valueOf(rule.getValue("material").jsonPrimitive.content),
                promptKey = rule.getValue("promptKey").jsonPrimitive.content,
                requiresInteriorView = rule.getValue("requiresInteriorView").jsonPrimitive.content.toBoolean(),
            )
        }

        return CountryProfile(
            isoCode = root.getValue("isoCode").jsonPrimitive.content,
            regulationName = root.getValue("regulationName").jsonPrimitive.content,
            regulationReference = root.getValue("regulationReference").jsonPrimitive.content,
            bins = bins,
            rules = rules,
            inspectionRules = inspectionRules,
            conservativeBin = BinId(root.getValue("conservativeBin").jsonPrimitive.content),
        )
    }
}
