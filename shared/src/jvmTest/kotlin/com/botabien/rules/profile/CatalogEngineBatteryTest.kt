package com.botabien.rules.profile

import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.WasteMaterial
import com.botabien.rules.DefaultRuleEngine
import com.botabien.rules.requiresInspection
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Batería genérica del motor sobre **todos** los perfiles registrados en el
 * catálogo real (RNF-004). No nombra ningún país: cuando se agrega un perfil
 * nuevo, estas pruebas lo cubren automáticamente, de modo que incorporar un
 * país sea exclusivamente un trabajo de datos con verificación incluida.
 */
class CatalogEngineBatteryTest {

    private val engine = DefaultRuleEngine()

    private val catalog = ProfileCatalog(
        source = { fileName -> File("resources/profiles", fileName).takeIf { it.isFile }?.readText() },
    )

    private val profiles = catalog.descriptors().getOrThrow()
        .map { it.id to catalog.load(it.id).getOrThrow() }

    @Test
    fun todoPerfilRegistradoValidaContraElEsquemaJson() {
        val schema = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(File("resources/profiles/profile.schema.json").toURI())
        val mapper = ObjectMapper()

        catalog.descriptors().getOrThrow().forEach { descriptor ->
            val errors = schema.validate(mapper.readTree(File("resources/profiles", descriptor.fileName)))

            assertTrue(
                errors.isEmpty(),
                "«${descriptor.fileName}» no valida contra el esquema:\n${errors.joinToString("\n")}",
            )
        }
    }

    @Test
    fun todoMaterialResuelveEnTodoEstadoYPerfilConJustificacionCitable() {
        profiles.forEach { (id, profile) ->
            WasteMaterial.entries.forEach { material ->
                ContaminationState.entries.forEach { state ->
                    val disposal = engine.resolve(material, state, emptySet(), profile)

                    assertTrue(
                        profile.bins.any { it.id == disposal.bin.id },
                        "[$id] $material/$state resuelve a una caneca del perfil",
                    )
                    assertTrue(
                        disposal.justification.isNotBlank(),
                        "[$id] $material/$state tiene justificación citable",
                    )
                    assertEquals(disposal.bin.route, disposal.route, "[$id] la ruta es la de la caneca")
                }
            }
        }
    }

    @Test
    fun todoMaterialLimpioVaASuCanecaObjetivoYSinReglaALaConservadora() {
        profiles.forEach { (id, profile) ->
            WasteMaterial.entries.forEach { material ->
                val rule = profile.rules.firstOrNull { it.material == material }
                val expected = rule?.targetBin ?: profile.conservativeBin

                val disposal = engine.resolve(material, ContaminationState.CLEAN, emptySet(), profile)

                assertEquals(expected, disposal.bin.id, "[$id] destino limpio de $material")
            }
        }
    }

    @Test
    fun todaReglaConAlternativaDegradaAlConfirmarseContaminacion() {
        profiles.forEach { (id, profile) ->
            profile.rules.filter { it.contaminatedFallback != null }.forEach { rule ->
                val disposal = engine.resolve(
                    rule.material,
                    ContaminationState.CONTAMINATED,
                    emptySet(),
                    profile,
                )

                assertEquals(rule.contaminatedFallback, disposal.bin.id, "[$id] destino contaminado de ${rule.material}")
                assertTrue(disposal.degradedByContamination, "[$id] ${rule.material} contaminado se marca degradado")
            }
        }
    }

    @Test
    fun todaReglaDeInspeccionConAlternativaAplicaLaRutaConservadoraSinVerificar() {
        profiles.forEach { (id, profile) ->
            profile.inspectionRules.forEach { inspection ->
                val rule = profile.rules.first { it.material == inspection.material }
                val fallback = rule.contaminatedFallback ?: return@forEach

                assertTrue(profile.requiresInspection(inspection.material))

                val disposal = engine.resolve(
                    inspection.material,
                    ContaminationState.UNKNOWN,
                    emptySet(),
                    profile,
                )

                assertEquals(fallback, disposal.bin.id, "[$id] ${inspection.material} sin verificar")
                assertTrue(disposal.degradedByContamination)
            }
        }
    }

    @Test
    fun conSoloLaCanecaConservadoraDisponibleTodoMaterialCaeEnElla() {
        profiles.forEach { (id, profile) ->
            WasteMaterial.entries.forEach { material ->
                val disposal = engine.resolve(
                    material,
                    ContaminationState.CLEAN,
                    setOf(profile.conservativeBin),
                    profile,
                )

                assertEquals(profile.conservativeBin, disposal.bin.id, "[$id] única caneca para $material")
            }
        }
    }
}
