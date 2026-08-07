package com.recycol.rules.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Comportamiento del catálogo (S30, RF-002, RF-004, CUS-001): los errores son
 * fallos explícitos de [Result], nunca excepciones que tumben la aplicación,
 * y un perfil inválido no afecta al que ya estaba cargado.
 */
class ProfileCatalogTest {

    private val nationalProfile = """
        {
          "isoCode": "zz",
          "regulationName": "Norma nacional de prueba",
          "regulationReference": "Referencia citable nacional",
          "bins": [
            { "id": "white", "displayName": "Caneca blanca", "colorHex": "#FFFFFF", "route": "RECYCLABLE" },
            { "id": "black", "displayName": "Caneca negra", "colorHex": "#1C1C1C", "route": "NON_RECYCLABLE" }
          ],
          "rules": [
            { "material": "PLASTIC", "targetBin": "white", "contaminatedFallback": "black", "justification": "Aprovechable limpio" }
          ],
          "inspectionRules": [],
          "conservativeBin": "black"
        }
    """.trimIndent()

    private val institutionalProfile = nationalProfile
        .replace("Norma nacional de prueba", "Norma institucional de prueba")
        .replace("Referencia citable nacional", "Referencia citable institucional")

    private val catalogIndex = """
        {
          "profiles": [
            { "id": "zz", "country": "zz", "displayName": "País de prueba", "file": "zz.json", "default": true },
            { "id": "zz-inst", "country": "zz", "displayName": "País de prueba — institucional", "file": "zz-inst.json", "default": false }
          ]
        }
    """.trimIndent()

    private fun catalog(vararg files: Pair<String, String>): ProfileCatalog {
        val storage = mapOf(ProfileCatalog.CATALOG_FILE_NAME to catalogIndex, *files)
        return ProfileCatalog(source = { fileName -> storage[fileName] })
    }

    private val fullCatalog = catalog(
        "zz.json" to nationalProfile,
        "zz-inst.json" to institutionalProfile,
    )

    @Test
    fun listaLosPerfilesRegistradosConSusVariantesInstitucionales() {
        val descriptors = fullCatalog.descriptors().getOrThrow()

        assertEquals(listOf("zz", "zz-inst"), descriptors.map { it.id })
        assertEquals(setOf("zz"), descriptors.map { it.country }.toSet(), "Ambos perfiles son del mismo país")
    }

    @Test
    fun cargaElPerfilNacionalYLaVarianteInstitucionalPorSuIdentificador() {
        val national = fullCatalog.load("zz").getOrThrow()
        val institutional = fullCatalog.load("zz-inst").getOrThrow()

        assertEquals("Norma nacional de prueba", national.regulationName)
        assertEquals("Norma institucional de prueba", institutional.regulationName)
    }

    @Test
    fun elPerfilPorDefectoDeUnPaisEsElDeclaradoEnElCatalogo() {
        val descriptor = fullCatalog.defaultFor("zz").getOrThrow()

        assertEquals("zz", descriptor.id)
        assertTrue(descriptor.isDefault)
    }

    @Test
    fun unPerfilNoRegistradoDevuelveFalloExplicitoSinLanzar() {
        val result = fullCatalog.load("xx")

        val error = assertIs<ProfileValidationException>(result.exceptionOrNull())
        assertTrue(error.problems.single().contains("xx"))
    }

    @Test
    fun unArchivoAusenteDevuelveFalloExplicitoSinLanzar() {
        val broken = catalog("zz.json" to nationalProfile) // zz-inst.json no existe

        val result = broken.load("zz-inst")

        val error = assertIs<ProfileValidationException>(result.exceptionOrNull())
        assertTrue(error.problems.single().contains("no existe"))
    }

    @Test
    fun unPerfilInvalidoSeRechazaYElPerfilAnteriorSigueOperativo() {
        val broken = catalog(
            "zz.json" to nationalProfile,
            "zz-inst.json" to institutionalProfile.replace(""""conservativeBin": "black"""", """"conservativeBin": "gray""""),
        )

        val previous = broken.load("zz").getOrThrow()

        val result = broken.load("zz-inst")
        val error = assertIs<ProfileValidationException>(result.exceptionOrNull())
        assertTrue(error.problems.any { "gray" in it }, "El rechazo cita el problema concreto")

        // El perfil previamente cargado no se ve afectado y el catálogo sigue operativo.
        assertEquals("Norma nacional de prueba", previous.regulationName)
        assertEquals(previous, broken.load("zz").getOrThrow())
    }

    @Test
    fun unPerfilRegistradoBajoOtroPaisSeRechaza() {
        val broken = catalog(
            "zz.json" to nationalProfile.replace(""""isoCode": "zz",""", """"isoCode": "yy","""),
            "zz-inst.json" to institutionalProfile,
        )

        val result = broken.load("zz")

        val error = assertIs<ProfileValidationException>(result.exceptionOrNull())
        assertTrue(error.problems.single().let { "yy" in it && "zz" in it })
    }

    @Test
    fun unIndiceAusenteOInvalidoDevuelveFalloExplicito() {
        val empty = ProfileCatalog(source = { null })

        val missing = empty.descriptors()
        assertIs<ProfileValidationException>(missing.exceptionOrNull())

        val malformed = ProfileCatalog(source = { "{ roto" })
        assertIs<ProfileValidationException>(malformed.descriptors().exceptionOrNull())
    }
}
