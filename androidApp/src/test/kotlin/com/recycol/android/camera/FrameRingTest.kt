package com.recycol.android.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class FrameRingTest {

    @Test
    fun `reutiliza el mismo bufer cada vuelta completa del anillo`() {
        val ring = FrameRing(slots = 3)
        val primera = ring.nextSlot(100)
        ring.nextSlot(100)
        ring.nextSlot(100)
        val segundaVuelta = ring.nextSlot(100)

        assertSame(primera.luma, segundaVuelta.luma, "Tras una vuelta completa debe volver el mismo búfer")
        assertSame(primera.argb, segundaVuelta.argb)
    }

    @Test
    fun `bufers consecutivos son distintos`() {
        val ring = FrameRing(slots = 3)
        val a = ring.nextSlot(100)
        val b = ring.nextSlot(100)

        assertNotSame(a.luma, b.luma, "Dos ranuras consecutivas no pueden compartir búfer")
        assertNotSame(a.argb, b.argb)
    }

    @Test
    fun `los dos planos de una ranura avanzan juntos`() {
        val ring = FrameRing(slots = 2)
        val primera = ring.nextSlot(100)
        val segunda = ring.nextSlot(100)
        val terceraEsLaPrimera = ring.nextSlot(100)

        assertSame(primera.luma, terceraEsLaPrimera.luma)
        assertSame(primera.argb, terceraEsLaPrimera.argb)
        assertNotSame(segunda.argb, terceraEsLaPrimera.argb)
    }

    @Test
    fun `un cambio de resolucion reasigna el bufer con el tamano nuevo`() {
        val ring = FrameRing(slots = 2)
        val chico = ring.nextSlot(100)
        ring.nextSlot(100)
        val grande = ring.nextSlot(200)

        assertNotSame(chico.luma, grande.luma)
        assertEquals(200, grande.luma.size)
        assertEquals(200, grande.argb.size)
    }

    @Test
    fun `la memoria es estable en una sesion larga`() {
        val ring = FrameRing(slots = 3)
        val vistos = mutableSetOf<ByteArray>()
        repeat(10_000) { vistos.add(ring.nextSlot(640 * 480).luma) }

        assertEquals(3, vistos.size, "Una sesión larga no debe asignar más búferes que ranuras")
    }

    @Test
    fun `rechaza un anillo demasiado pequeno`() {
        assertFailsWith<IllegalArgumentException> { FrameRing(slots = 1) }
    }
}
