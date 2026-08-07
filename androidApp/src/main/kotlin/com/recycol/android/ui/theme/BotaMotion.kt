package com.recycol.android.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * Tokens de movimiento del design system. Las transiciones son suaves y
 * discretas (RNF-009): muelles sin rebote apenas perceptible y curvas de
 * entrada/salida equivalentes a las de iOS. Ninguna pantalla define
 * duraciones ni curvas propias.
 */
object BotaMotion {

    /** Duración corta: cambios de estado de un control. */
    const val DURATION_FAST_MS = 200

    /** Duración estándar: aparición y desaparición de contenido. */
    const val DURATION_BASE_MS = 300

    /** Duración larga: transiciones de pantalla completa. */
    const val DURATION_SLOW_MS = 450

    /** Aparición del bote en el arranque, antes de que empiece a brotar. */
    const val DURATION_LAUNCH_SETTLE_MS = 200

    /**
     * Duración del brote de la pantalla de arranque. Es el único movimiento
     * largo de la aplicación y aun así se queda por debajo de dos tercios de
     * segundo: quien abre la app quince veces al día no debe esperar a una
     * animación. El contenido real se compone por debajo mientras ocurre, así
     * que esto no retrasa el arranque, solo lo tapa.
     */
    const val DURATION_LAUNCH_GROWTH_MS = 620

    /** Reposo entre el final del brote y la retirada del velo de arranque. */
    const val DURATION_LAUNCH_HOLD_MS = 100

    /** Retirada del velo de arranque. */
    const val DURATION_LAUNCH_EXIT_MS = 260

    /** Curva estándar de iOS para animaciones con duración fija. */
    val easeInOut = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)

    /** Curva de salida suave para contenido que entra en pantalla. */
    val easeOut = CubicBezierEasing(0.0f, 0.0f, 0.25f, 1.0f)

    /**
     * Curva del brote: arranca con decisión y se posa muy despacio, que es como
     * crece algo vivo. Sin sobreimpulso a propósito, porque el progreso del
     * brote alimenta longitudes y escalas que no admiten pasarse de uno.
     */
    val growth = CubicBezierEasing(0.16f, 0.84f, 0.32f, 1.0f)

    /** Muelle para la respuesta táctil de los controles: firme y sin rebote. */
    fun <T> pressSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Muelle para superficies que se deslizan (hojas, superposiciones). */
    fun <T> surfaceSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessLow,
    )

    /** Factor de escala de un control mientras está presionado. */
    const val PRESSED_SCALE = 0.96f

    /** Opacidad del contenido de un control mientras está presionado. */
    const val PRESSED_ALPHA = 0.75f
}
