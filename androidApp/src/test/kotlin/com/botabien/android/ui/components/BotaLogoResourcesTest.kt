package com.botabien.android.ui.components

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * El logo existe en cuatro sitios a la vez —el composable, el recurso vectorial
 * de plataforma, las dos capas del icono de lanzador y el SVG maestro— porque
 * cada consumidor exige un formato distinto: el lanzador no ejecuta Compose y
 * Compose no puede animar por piezas un recurso vectorial.
 *
 * Esa duplicación solo es aceptable si no puede divergir en silencio, y de eso
 * se ocupa esta prueba: si alguien retoca una curva en un sitio y se olvida del
 * resto, el build falla en vez de dejar dos logos distintos en la misma app.
 */
class BotaLogoResourcesTest {

    private val paths = mapOf(
        "tierra" to BotaLogoPaths.SOIL,
        "borde" to BotaLogoPaths.RIM,
        "cuerpo" to BotaLogoPaths.BODY,
        "tallo" to BotaLogoPaths.STEM,
        "hoja izquierda" to BotaLogoPaths.FIRST_LEAF,
        "hoja derecha" to BotaLogoPaths.SECOND_LEAF,
        "flor" to BotaLogoPaths.FLOWER,
    )

    @Test
    fun `el recurso vectorial del logo usa los trazados del composable`() {
        assertContainsEveryPath("androidApp/src/main/res/drawable/ic_logo_botabien.xml")
    }

    @Test
    fun `la capa de primer plano del icono usa los trazados del composable`() {
        assertContainsEveryPath("androidApp/src/main/res/drawable/ic_launcher_foreground.xml")
    }

    @Test
    fun `la capa monocroma del icono usa los trazados del composable`() {
        assertContainsEveryPath("androidApp/src/main/res/drawable/ic_launcher_monochrome.xml")
    }

    @Test
    fun `el SVG maestro usa los trazados del composable`() {
        assertContainsEveryPath("docs/brand/botabien-logo.svg")
    }

    /**
     * Las capas del icono adaptativo deben compartir transformación: si una se
     * mueve y la otra no, el icono temático de Android 13 aparece descuadrado
     * respecto al normal, y es un defecto que solo se ve en el dispositivo.
     */
    @Test
    fun `las dos capas del icono comparten la misma transformacion`() {
        val foreground = transformOf("androidApp/src/main/res/drawable/ic_launcher_foreground.xml")
        val monochrome = transformOf("androidApp/src/main/res/drawable/ic_launcher_monochrome.xml")
        assertTrue(
            foreground.isNotEmpty(),
            "La capa de primer plano debe declarar un grupo con su transformación",
        )
        assertTrue(
            foreground == monochrome,
            "Las capas divergen: primer plano $foreground frente a monocroma $monochrome",
        )
    }

    private fun assertContainsEveryPath(relativePath: String) {
        val content = normalize(readFromRepository(relativePath))
        paths.forEach { (name, pathData) ->
            assertTrue(
                content.contains(normalize(pathData)),
                "El trazado de la $name no coincide con BotaLogoPaths en $relativePath",
            )
        }
    }

    /** Atributos de escala y traslación del grupo de un recurso vectorial. */
    private fun transformOf(relativePath: String): List<String> {
        val content = readFromRepository(relativePath)
        return TRANSFORM_ATTRIBUTES.mapNotNull { attribute ->
            Regex("""android:$attribute="([^"]+)"""")
                .find(content)
                ?.let { "$attribute=${it.groupValues[1]}" }
        }
    }

    /**
     * Localiza un archivo del repositorio sin depender de cuál sea el
     * directorio de trabajo de la tarea de pruebas, que cambia según se
     * ejecute desde la raíz o desde el módulo.
     */
    private fun readFromRepository(relativePath: String): String {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        fail("No se encontró $relativePath partiendo de ${File("").absolutePath}")
    }

    /**
     * Deja las cadenas comparables entre formatos: el vectorial de Android
     * separa coordenadas con coma y el SVG con espacio, y el sangrado difiere.
     */
    private fun normalize(value: String): String =
        value.replace(',', ' ').replace(WHITESPACE, " ").trim()

    private companion object {
        val WHITESPACE = Regex("""\s+""")
        val TRANSFORM_ATTRIBUTES = listOf(
            "pivotX", "pivotY", "scaleX", "scaleY", "translateX", "translateY",
        )
    }
}
