package com.recycol.profiles

import com.recycol.domain.model.DisposalRoute
import com.recycol.domain.model.WasteMaterial
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Validación del perfil de Colombia (S03, RF-002, RNF-004, CUS-001).
 *
 * Tres frentes:
 * 1. `co.json` valida contra `profile.schema.json` con un validador JSON Schema real.
 * 2. Los vocabularios del esquema están sincronizados con los enumerados del dominio.
 * 3. El contenido cumple la Resolución 2184 de 2019: tres canecas y el caso
 *    explícito del cartón para bebidas limpio frente a contaminado.
 */
class ColombiaProfileTest {

    private val profilesDir = File("resources/profiles")
    private val schemaFile = File(profilesDir, "profile.schema.json")
    private val profileFile = File(profilesDir, "co.json")

    private val profile = Json.parseToJsonElement(profileFile.readText()).jsonObject

    @Test
    fun elPerfilDeColombiaValidaContraElEsquema() {
        val schema = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(schemaFile.toURI())

        val errors = schema.validate(ObjectMapper().readTree(profileFile))

        assertTrue(errors.isEmpty(), "co.json no valida contra el esquema:\n${errors.joinToString("\n")}")
    }

    @Test
    fun elVocabularioDeMaterialesDelEsquemaCoincideConElEnumerado() {
        val schema = Json.parseToJsonElement(schemaFile.readText()).jsonObject
        val schemaMaterials = schema.getValue("\$defs").jsonObject
            .getValue("wasteMaterial").jsonObject
            .getValue("enum").jsonArray
            .map { it.jsonPrimitive.content }

        assertEquals(WasteMaterial.entries.map { it.name }, schemaMaterials)
    }

    @Test
    fun elVocabularioDeRutasDelEsquemaCoincideConElEnumerado() {
        val schema = Json.parseToJsonElement(schemaFile.readText()).jsonObject
        val schemaRoutes = schema.getValue("\$defs").jsonObject
            .getValue("disposalRoute").jsonObject
            .getValue("enum").jsonArray
            .map { it.jsonPrimitive.content }

        assertEquals(DisposalRoute.entries.map { it.name }, schemaRoutes)
    }

    @Test
    fun cubreLasTresCanecasDeLaResolucion2184MasLaRecoleccionEspecial() {
        val bins = profile.getValue("bins").jsonArray.associate {
            it.jsonObject.getValue("id").jsonPrimitive.content to
                it.jsonObject.getValue("route").jsonPrimitive.content
        }

        assertEquals(4, bins.size, "Tres canecas del código de colores más el punto de recolección especial")
        assertEquals("RECYCLABLE", bins["white"], "La caneca blanca es la corriente aprovechable")
        assertEquals("NON_RECYCLABLE", bins["black"], "La caneca negra es la corriente no aprovechable")
        assertEquals("ORGANIC", bins["green"], "La caneca verde es la corriente orgánica aprovechable")
        // Pilas y RAEE quedan fuera del código de colores (decisión de v1,
        // coordinación #54): el punto posconsumo se modela como destino propio.
        assertEquals("SPECIAL_COLLECTION", bins["special"], "El punto de recolección posconsumo es un destino fuera del código de colores")
    }

    @Test
    fun declaraExplicitamenteElCasoDelVasoDeCartonLimpioFrenteAContaminado() {
        val cartonRule = profile.getValue("rules").jsonArray
            .map { it.jsonObject }
            .single { it.getValue("material").jsonPrimitive.content == WasteMaterial.BEVERAGE_CARTON.name }

        assertEquals("white", cartonRule.getValue("targetBin").jsonPrimitive.content, "Limpio y seco va a la blanca")
        assertEquals("black", cartonRule.getValue("contaminatedFallback").jsonPrimitive.content, "Contaminado se degrada a la negra")

        val inspection = profile.getValue("inspectionRules").jsonArray
            .map { it.jsonObject }
            .singleOrNull { it.getValue("material").jsonPrimitive.content == WasteMaterial.BEVERAGE_CARTON.name }

        assertNotNull(inspection, "El cartón para bebidas exige regla de inspección")
        assertTrue(
            inspection.getValue("requiresInteriorView").jsonPrimitive.content.toBoolean(),
            "La inspección exige la vista interior del envase",
        )
    }

    @Test
    fun todaCanecaReferenciadaExisteYTodoMaterialTieneRegla() {
        val binIds = profile.getValue("bins").jsonArray
            .map { it.jsonObject.getValue("id").jsonPrimitive.content }
            .toSet()

        val rules = profile.getValue("rules").jsonArray.map { it.jsonObject }
        rules.forEach { rule ->
            assertTrue(rule.getValue("targetBin").jsonPrimitive.content in binIds)
            val fallback = rule["contaminatedFallback"]?.jsonPrimitive
            if (fallback != null && fallback.isString) {
                assertTrue(fallback.content in binIds)
            }
        }
        assertTrue(profile.getValue("conservativeBin").jsonPrimitive.content in binIds)

        // Decisión de diseño del perfil: cobertura explícita de todo el vocabulario,
        // ningún material depende del respaldo implícito de la ruta conservadora.
        val ruledMaterials = rules.map { it.getValue("material").jsonPrimitive.content }.toSet()
        assertEquals(WasteMaterial.entries.map { it.name }.toSet(), ruledMaterials)
    }
}
