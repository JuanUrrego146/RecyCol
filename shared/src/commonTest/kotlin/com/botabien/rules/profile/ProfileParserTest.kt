package com.botabien.rules.profile

import com.botabien.domain.model.BinId
import com.botabien.domain.model.DisposalRoute
import com.botabien.domain.model.WasteMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Validación de perfiles (S30, RF-002, RF-004): un archivo inválido se rechaza
 * con un error que enumera cada problema; uno válido se traduce fiel al dominio.
 */
class ProfileParserTest {

    private fun parse(json: String) = ProfileParser.parseProfile("prueba.json", json)

    private fun failure(json: String): ProfileValidationException =
        assertFailsWith { parse(json) }

    @Test
    fun unPerfilValidoSeTraduceFielAlDominio() {
        val profile = parse(VALID_PROFILE)

        assertEquals("zz", profile.isoCode)
        assertEquals("Norma de prueba", profile.regulationName)
        assertEquals(2, profile.bins.size)
        assertEquals(DisposalRoute.RECYCLABLE, profile.bins.first().route)
        assertEquals(BinId("black"), profile.conservativeBin)

        val plastic = profile.rules.single { it.material == WasteMaterial.PLASTIC }
        assertEquals(BinId("white"), plastic.targetBin)
        assertEquals(BinId("black"), plastic.contaminatedFallback)

        val residual = profile.rules.single { it.material == WasteMaterial.RESIDUAL }
        assertNull(residual.contaminatedFallback, "contaminatedFallback null se traduce a null")

        val inspection = profile.inspectionRules.single()
        assertEquals(WasteMaterial.PLASTIC, inspection.material)
        assertTrue(inspection.requiresInteriorView)
    }

    @Test
    fun unContaminatedFallbackOmitidoEquivaleANull() {
        val json = VALID_PROFILE.replace(
            """"contaminatedFallback": null,""",
            "",
        )

        val residual = parse(json).rules.single { it.material == WasteMaterial.RESIDUAL }

        assertNull(residual.contaminatedFallback)
    }

    @Test
    fun unMaterialFueraDelVocabularioSeReportaConSuNombre() {
        val json = VALID_PROFILE.replace(""""material": "PLASTIC",""", """"material": "PLASTICO",""")

        val error = failure(json)

        assertTrue(error.problems.any { "PLASTICO" in it }, error.message.orEmpty())
    }

    @Test
    fun unaRutaDesconocidaSeReporta() {
        val json = VALID_PROFILE.replace(""""route": "NON_RECYCLABLE"""", """"route": "COMPOST"""")

        val error = failure(json)

        assertTrue(error.problems.any { "COMPOST" in it })
    }

    @Test
    fun unaCanecaReferenciadaInexistenteSeReporta() {
        val json = VALID_PROFILE.replace(""""targetBin": "white",""", """"targetBin": "blue",""")

        val error = failure(json)

        assertTrue(error.problems.any { "blue" in it })
    }

    @Test
    fun laCanecaConservadoraInexistenteSeReporta() {
        val json = VALID_PROFILE.replace(""""conservativeBin": "black"""", """"conservativeBin": "gray"""")

        val error = failure(json)

        assertTrue(error.problems.any { "gray" in it && "conservativeBin" in it })
    }

    @Test
    fun unColorFueraDeFormatoSeReporta() {
        val json = VALID_PROFILE.replace(""""colorHex": "#FFFFFF",""", """"colorHex": "blanco",""")

        val error = failure(json)

        assertTrue(error.problems.any { "blanco" in it })
    }

    @Test
    fun unaCanecaDuplicadaSeReporta() {
        val json = VALID_PROFILE.replace(""""id": "black",""", """"id": "white",""")

        val error = failure(json)

        assertTrue(error.problems.any { "más de una vez" in it })
    }

    @Test
    fun unaInspeccionDeUnMaterialSinReglaSeReporta() {
        val json = VALID_PROFILE.replace(
            """"material": "PLASTIC", "promptKey"""",
            """"material": "GLASS", "promptKey"""",
        )

        val error = failure(json)

        assertTrue(error.problems.any { "GLASS" in it && "regla" in it })
    }

    @Test
    fun unCampoAusenteSeRechazaConErrorDeForma() {
        val json = VALID_PROFILE.replace(""""regulationName": "Norma de prueba",""", "")

        val error = failure(json)

        assertTrue(error.problems.single().contains("forma del esquema"))
    }

    @Test
    fun unaClaveDesconocidaSeRechaza() {
        val json = VALID_PROFILE.replace(""""isoCode": "zz",""", """"isoCode": "zz", "pais": "zz",""")

        assertFailsWith<ProfileValidationException> { parse(json) }
    }

    @Test
    fun unJsonMalformadoSeRechazaSinLanzarOtraCosa() {
        assertFailsWith<ProfileValidationException> { parse("{ esto no es json") }
    }

    @Test
    fun losProblemasSeAcumulanEnUnSoloRechazo() {
        val json = VALID_PROFILE
            .replace(""""conservativeBin": "black"""", """"conservativeBin": "gray"""")
            .replace(""""colorHex": "#FFFFFF",""", """"colorHex": "blanco",""")

        val error = failure(json)

        assertTrue(error.problems.size >= 2, "Se esperaban al menos dos problemas: ${error.problems}")
    }

    @Test
    fun elAvisoDeCanecaNoDisponibleEsOpcionalPeroNoPuedeEstarVacio() {
        assertEquals("", parse(VALID_PROFILE).unavailableBinNotice, "Sin declarar equivale a vacío")

        val withNotice = VALID_PROFILE.replace(
            """"conservativeBin": "black"""",
            """"conservativeBin": "black", "unavailableBinNotice": "Usa {assigned} en lugar de {ideal}."""",
        )
        assertEquals("Usa {assigned} en lugar de {ideal}.", parse(withNotice).unavailableBinNotice)

        val blankNotice = VALID_PROFILE.replace(
            """"conservativeBin": "black"""",
            """"conservativeBin": "black", "unavailableBinNotice": "  """" ,
        )
        val error = failure(blankNotice)
        assertTrue(error.problems.any { "unavailableBinNotice" in it })
    }

    @Test
    fun unCatalogoValidoSeParseaYUnoInvalidoAcumulaProblemas() {
        val descriptors = ProfileParser.parseCatalog("catalog.json", VALID_CATALOG)

        assertEquals(listOf("zz", "zz-inst"), descriptors.map { it.id })
        assertEquals(listOf(true, false), descriptors.map { it.isDefault })
        assertEquals("zz.json", descriptors.first().fileName)

        val twoDefaults = VALID_CATALOG.replace(""""default": false""", """"default": true""")
        val error = assertFailsWith<ProfileValidationException> {
            ProfileParser.parseCatalog("catalog.json", twoDefaults)
        }
        assertTrue(error.problems.any { "por defecto" in it })
    }

    private companion object {
        val VALID_PROFILE = """
            {
              "isoCode": "zz",
              "regulationName": "Norma de prueba",
              "regulationReference": "Referencia citable de la norma de prueba",
              "bins": [
                { "id": "white", "displayName": "Caneca blanca", "colorHex": "#FFFFFF", "route": "RECYCLABLE" },
                { "id": "black", "displayName": "Caneca negra", "colorHex": "#1C1C1C", "route": "NON_RECYCLABLE" }
              ],
              "rules": [
                { "material": "PLASTIC", "targetBin": "white", "contaminatedFallback": "black", "justification": "Aprovechable limpio y seco" },
                { "material": "RESIDUAL", "targetBin": "black", "contaminatedFallback": null, "justification": "No aprovechable" }
              ],
              "inspectionRules": [
                { "material": "PLASTIC", "promptKey": "inspection.point_inside", "requiresInteriorView": true }
              ],
              "conservativeBin": "black"
            }
        """.trimIndent()

        val VALID_CATALOG = """
            {
              "profiles": [
                { "id": "zz", "country": "zz", "displayName": "País de prueba — norma nacional", "file": "zz.json", "default": true },
                { "id": "zz-inst", "country": "zz", "displayName": "País de prueba — variante institucional", "file": "zz-inst.json", "default": false }
              ]
            }
        """.trimIndent()
    }
}
