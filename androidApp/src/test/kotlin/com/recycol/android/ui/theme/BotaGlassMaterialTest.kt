package com.recycol.android.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * La regla que decide el material se prueba entera porque ya se equivocó una
 * vez: degradaba a superficie opaca con el ahorro de energía activo o en
 * dispositivos de poca memoria, y como el cristal no lleva desenfoque —son dos
 * degradados y un borde— esa precaución no ahorraba nada y dejaba sin material
 * a cualquiera con el ahorro de batería encendido, que es muchísima gente.
 *
 * Ahora solo degrada la preferencia explícita del usuario.
 */
class BotaGlassMaterialTest {

    @Test
    fun `por defecto sirve el cristal transparente`() {
        assertEquals(
            BotaGlassMaterial.Clear,
            glassMaterialFor(animationsDisabled = false),
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
            glassMaterialFor(animationsDisabled = true),
        )
    }
}
