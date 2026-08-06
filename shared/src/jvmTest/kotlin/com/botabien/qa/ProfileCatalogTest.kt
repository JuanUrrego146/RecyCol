package com.botabien.qa

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
import kotlin.test.assertTrue

/**
 * Validación de TODO el catálogo de perfiles normativos (RF-002, RNF-004).
 *
 * A diferencia de `ColombiaProfileTest`, que verifica el contenido específico
 * de la Resolución 2184, esta prueba recorre todos los `.json` del catálogo
 * `resources/profiles` sin conocer ningún país: cualquier perfil nuevo —por
 * ejemplo el segundo país de S33— queda validado automáticamente sin tocar
 * código Kotlin, que es exactamente lo que promete RNF-004.
 */
class ProfileCatalogTest {

    private val profilesDir = File("resources/profiles")
    private val schemaFile = File(profilesDir, "profile.schema.json")

    private val profiles = profilesDir
        .listFiles { file -> file.extension == "json" && file.name != schemaFile.name }
        .orEmpty()
        .sortedBy { it.name }

    @Test
    fun elCatalogoNoEstaVacio() {
        assertTrue(profiles.isNotEmpty(), "El catálogo debe contener al menos un perfil (co.json)")
    }

    @Test
    fun todoPerfilDelCatalogoValidaContraElEsquema() {
        val schema = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(schemaFile.toURI())
        val mapper = ObjectMapper()

        profiles.forEach { file ->
            val errors = schema.validate(mapper.readTree(file))
            assertTrue(errors.isEmpty(), "${file.name} no valida contra el esquema:\n${errors.joinToString("\n")}")
        }
    }

    @Test
    fun elCodigoIsoDeCadaPerfilCoincideConSuNombreDeArchivo() {
        profiles.forEach { file ->
            val isoCode = Json.parseToJsonElement(file.readText()).jsonObject
                .getValue("isoCode").jsonPrimitive.content

            assertEquals(
                file.nameWithoutExtension,
                isoCode,
                "El archivo ${file.name} declara isoCode '$isoCode'; el catálogo se indexa por nombre de archivo",
            )
        }
    }

    @Test
    fun todaCanecaReferenciadaPorUnPerfilExisteEnSusPropiasCanecas() {
        profiles.forEach { file ->
            val profile = Json.parseToJsonElement(file.readText()).jsonObject
            val binIds = profile.getValue("bins").jsonArray
                .map { it.jsonObject.getValue("id").jsonPrimitive.content }
                .toSet()

            assertTrue(
                profile.getValue("conservativeBin").jsonPrimitive.content in binIds,
                "${file.name}: conservativeBin no existe entre las canecas declaradas",
            )

            profile.getValue("rules").jsonArray.map { it.jsonObject }.forEach { rule ->
                val material = rule.getValue("material").jsonPrimitive.content
                assertTrue(
                    rule.getValue("targetBin").jsonPrimitive.content in binIds,
                    "${file.name}: la regla de $material apunta a una caneca inexistente",
                )
                val fallback = rule["contaminatedFallback"]?.jsonPrimitive
                if (fallback != null && fallback.isString) {
                    assertTrue(
                        fallback.content in binIds,
                        "${file.name}: el respaldo por contaminación de $material apunta a una caneca inexistente",
                    )
                }
            }
        }
    }

    @Test
    fun todaReglaDeInspeccionRefiereUnMaterialConReglaDeCaneca() {
        // Una regla de inspección sin regla de material sería inalcanzable: el
        // RuleEngine jamás pediría la vista interior de un material que resuelve
        // por la ruta conservadora implícita.
        profiles.forEach { file ->
            val profile = Json.parseToJsonElement(file.readText()).jsonObject
            val ruledMaterials = profile.getValue("rules").jsonArray
                .map { it.jsonObject.getValue("material").jsonPrimitive.content }
                .toSet()

            profile.getValue("inspectionRules").jsonArray.map { it.jsonObject }.forEach { inspection ->
                val material = inspection.getValue("material").jsonPrimitive.content
                assertTrue(
                    material in ruledMaterials,
                    "${file.name}: la inspección de $material no tiene regla de caneca asociada",
                )
            }
        }
    }
}
