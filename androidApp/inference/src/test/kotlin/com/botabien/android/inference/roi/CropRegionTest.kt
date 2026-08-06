package com.botabien.android.inference.roi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CropRegionTest {

    @Test
    fun `el cuadrado central maximo usa el lado menor del frame`() {
        val region = CropRegion.centeredSquare(frameWidth = 800, frameHeight = 600)

        assertEquals(CropRegion(left = 100, top = 0, size = 600), region)
    }

    @Test
    fun `la fraccion centrada reduce el lado y se mantiene centrada`() {
        val region = CropRegion.centeredFraction(frameWidth = 100, frameHeight = 100, fraction = 0.5f)

        assertEquals(CropRegion(left = 25, top = 25, size = 50), region)
    }

    @Test
    fun `una region centrada cerca del borde se desplaza para caber en el frame`() {
        val region = CropRegion.centeredAt(
            centerX = 5,
            centerY = 5,
            side = 40,
            frameWidth = 100,
            frameHeight = 80,
        )

        assertEquals(0, region.left)
        assertEquals(0, region.top)
        assertEquals(40, region.size)
    }

    @Test
    fun `un lado mayor que el frame se acota al lado menor`() {
        val region = CropRegion.centeredAt(
            centerX = 50,
            centerY = 40,
            side = 500,
            frameWidth = 100,
            frameHeight = 80,
        )

        assertEquals(80, region.size)
        assertTrue(region.left + region.size <= 100)
        assertTrue(region.top + region.size <= 80)
    }

    @Test
    fun `una region degenerada se rechaza`() {
        assertFailsWith<IllegalArgumentException> { CropRegion(left = 0, top = 0, size = 0) }
        assertFailsWith<IllegalArgumentException> { CropRegion(left = -1, top = 0, size = 10) }
    }

    @Test
    fun `el marco guia ocupa la fraccion documentada del lado menor`() {
        val frame = com.botabien.android.inference.FakePixelFrame.solid(200, 100, 0xFF000000.toInt())
        val region = kotlinx.coroutines.runBlocking { GuideFrameRoi().findRegion(frame) }

        assertEquals((100 * GuideFrameRoi.GUIDE_FRACTION).toInt(), region.size)
        assertEquals((200 - region.size) / 2, region.left)
        assertEquals((100 - region.size) / 2, region.top)
    }
}
