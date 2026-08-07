package com.recycol.rules.bins

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Detección de regiones de color sobre frames sintéticos (S34, RF-005): tres
 * canecas del código colombiano sobre fondo gris, evaluadas bajo luz neutra,
 * cálida, tenue y fría, con y sin ruido de sensor determinista.
 */
class ColorRegionFinderTest {

    private val white = "#F2F2F2"
    private val black = "#1C1C1C"
    private val green = "#2E7D32"
    private val background = "#8A8578"

    private val width = 96
    private val height = 128

    /** Pinta tres franjas verticales tipo caneca sobre el fondo. */
    private fun frame(
        light: (Int, Int, Int) -> Triple<Int, Int, Int>,
        noise: Random? = null,
    ): IntArray {
        val pixels = IntArray(width * height) { SyntheticLighting.argbUnder(background, light) }

        fun paint(hex: String, xRange: IntRange, yRange: IntRange) {
            val color = SyntheticLighting.argbUnder(hex, light)
            for (y in yRange) {
                for (x in xRange) {
                    pixels[y * width + x] = if (noise == null) color else jitter(color, noise)
                }
            }
        }

        paint(white, 8..27, 56..119)
        paint(black, 38..57, 56..119)
        paint(green, 68..87, 56..119)
        return pixels
    }

    private fun jitter(argb: Int, noise: Random): Int {
        fun channel(value: Int) = (value + noise.nextInt(-12, 13)).coerceIn(0, 255)
        return (0xFF shl 24) or
            (channel((argb shr 16) and 0xFF) shl 16) or
            (channel((argb shr 8) and 0xFF) shl 8) or
            channel(argb and 0xFF)
    }

    /** `true` si alguna región tiene el color esperado bajo esa luz. */
    private fun List<ColorRegion>.containsColor(
        hex: String,
        light: (Int, Int, Int) -> Triple<Int, Int, Int>,
    ): Boolean {
        val expected = ColorSpace.fromHex(SyntheticLighting.applyToHex(hex, light))
        return any { region ->
            val actual = ColorSpace.fromHex(region.colorHex)
            ColorSpace.distance(actual, expected) < 0.12f
        }
    }

    @Test
    fun detectaLasTresCanecasBajoTodasLasCondicionesDeLuz() {
        SyntheticLighting.ALL.forEach { (name, light) ->
            val regions = ColorRegionFinder.findRegions(frame(light), width, height)

            assertTrue(regions.containsColor(white, light), "Caneca blanca bajo luz $name")
            assertTrue(regions.containsColor(black, light), "Caneca negra bajo luz $name")
            assertTrue(regions.containsColor(green, light), "Caneca verde bajo luz $name")
        }
    }

    @Test
    fun detectaLasCanecasConRuidoDeSensorDeterminista() {
        val regions = ColorRegionFinder.findRegions(
            frame(SyntheticLighting.NEUTRAL, noise = Random(42)),
            width,
            height,
        )

        assertTrue(regions.containsColor(white, SyntheticLighting.NEUTRAL), "Blanca con ruido")
        assertTrue(regions.containsColor(black, SyntheticLighting.NEUTRAL), "Negra con ruido")
        assertTrue(regions.containsColor(green, SyntheticLighting.NEUTRAL), "Verde con ruido")
    }

    @Test
    fun unFondoUniformeNoProduceCanecasFantasma() {
        val pixels = IntArray(width * height) { SyntheticLighting.argbUnder(background, SyntheticLighting.NEUTRAL) }

        val regions = ColorRegionFinder.findRegions(pixels, width, height)

        assertEquals(1, regions.size, "El fondo uniforme es una sola región")
        assertTrue(regions.single().coverage > 0.95f)
    }

    @Test
    fun lasRegionesVienenOrdenadasPorAreaYConMetricasEnRango() {
        val regions = ColorRegionFinder.findRegions(frame(SyntheticLighting.NEUTRAL), width, height)

        assertEquals(regions.sortedByDescending { it.coverage }, regions, "Orden por área descendente")
        regions.forEach { region ->
            assertTrue(region.coverage in 0f..1f)
            assertTrue(region.cohesion in 0f..1f)
            assertTrue(region.colorHex.matches(Regex("^#[0-9A-F]{6}$")), region.colorHex)
        }
    }

    @Test
    fun laConfianzaDeUnaRegionGrandeYUniformeSuperaElUmbralDelMatcher() {
        val regions = ColorRegionFinder.findRegions(frame(SyntheticLighting.NEUTRAL), width, height)
        val greenRegion = regions.first { region ->
            ColorSpace.distance(ColorSpace.fromHex(region.colorHex), ColorSpace.fromHex(green)) < 0.12f
        }

        assertTrue(greenRegion.toDetectedBin().confidence >= 0.35f, "Una caneca clara debe ser proponible")
    }
}
