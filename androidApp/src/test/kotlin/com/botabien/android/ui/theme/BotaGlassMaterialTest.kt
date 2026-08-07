package com.botabien.android.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El material de cristal es lo único de la interfaz que puede costar fluidez en
 * la cámara, así que la regla que decide cuándo se renuncia a él se prueba
 * entera: son pocas combinaciones y equivocarse se paga en el dispositivo del
 * usuario, donde ya no se ve.
 */
class BotaGlassMaterialTest {

    @Test
    fun `sin ninguna senal en contra sirve el cristal transparente`() {
        assertEquals(
            BotaGlassMaterial.Clear,
            glassMaterialFor(
                lowRamDevice = false,
                savingPower = false,
                animationsDisabled = false,
            ),
        )
    }

    @Test
    fun `un dispositivo de poca memoria renuncia a la translucidez`() {
        assertEquals(
            BotaGlassMaterial.Veil,
            glassMaterialFor(
                lowRamDevice = true,
                savingPower = false,
                animationsDisabled = false,
            ),
        )
    }

    @Test
    fun `el ahorro de energia renuncia a la translucidez`() {
        assertEquals(
            BotaGlassMaterial.Veil,
            glassMaterialFor(
                lowRamDevice = false,
                savingPower = true,
                animationsDisabled = false,
            ),
        )
    }

    /**
     * Es la señal más cercana que Android ofrece al «reducir transparencia» de
     * iOS: quien apaga las animaciones no quiere efectos, y la translucidez lo
     * es tanto como el movimiento.
     */
    @Test
    fun `las animaciones desactivadas renuncian a la translucidez`() {
        assertEquals(
            BotaGlassMaterial.Veil,
            glassMaterialFor(
                lowRamDevice = false,
                savingPower = false,
                animationsDisabled = true,
            ),
        )
    }

    @Test
    fun `basta una senal aunque las demas permitan el cristal`() {
        val combinations = listOf(
            Triple(true, true, false),
            Triple(true, false, true),
            Triple(false, true, true),
            Triple(true, true, true),
        )
        combinations.forEach { (lowRam, savingPower, animationsOff) ->
            assertEquals(
                BotaGlassMaterial.Veil,
                glassMaterialFor(lowRam, savingPower, animationsOff),
                "memoria=$lowRam energia=$savingPower animaciones=$animationsOff",
            )
        }
    }
}
