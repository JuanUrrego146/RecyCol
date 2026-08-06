package com.botabien.rules.profile

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verificación del catálogo real de recursos (S30, RF-002, RNF-004): el índice
 * parsea, todo perfil registrado existe y valida, y Colombia queda registrada
 * con su perfil nacional por defecto. Estas pruebas son las que garantizan que
 * «agregar un país» sea solo agregar datos: si el archivo nuevo es inválido,
 * fallan aquí antes de llegar a la aplicación.
 */
class ProfileResourcesTest {

    private val profilesDir = File("resources/profiles")

    private val catalog = ProfileCatalog(
        source = { fileName -> File(profilesDir, fileName).takeIf { it.isFile }?.readText() },
    )

    @Test
    fun elIndiceDelCatalogoParsea() {
        val descriptors = catalog.descriptors().getOrThrow()

        assertTrue(descriptors.isNotEmpty())
    }

    @Test
    fun todoPerfilRegistradoExisteValidaYCoincideConSuPais() {
        val descriptors = catalog.descriptors().getOrThrow()

        descriptors.forEach { descriptor ->
            val profile = catalog.load(descriptor.id).getOrThrow()
            assertEquals(descriptor.country, profile.isoCode, "País de «${descriptor.id}»")
        }
    }

    @Test
    fun colombiaEstaRegistradaConElPerfilNacionalPorDefecto() {
        val descriptor = catalog.defaultFor("co").getOrThrow()

        assertEquals("co", descriptor.id)

        val profile = catalog.load(descriptor.id).getOrThrow()
        assertEquals("Resolución 2184 de 2019", profile.regulationName)
        assertEquals(3, profile.bins.size)
    }
}
