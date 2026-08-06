package com.botabien.android.ui.theme

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

    /** Curva estándar de iOS para animaciones con duración fija. */
    val easeInOut = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)

    /** Curva de salida suave para contenido que entra en pantalla. */
    val easeOut = CubicBezierEasing(0.0f, 0.0f, 0.25f, 1.0f)

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
