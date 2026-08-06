package com.botabien.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Escala de espaciado del design system, en pasos de 4 dp. El aire generoso
 * es parte de la estética (RNF-009): ante la duda, se elige el paso mayor.
 * Ningún componente ni pantalla usa valores de `dp` sueltos para separar
 * contenido; siempre un token de esta escala.
 */
@Immutable
data class BotaSpacing(
    /** 2 dp: separaciones hairline dentro de un mismo control. */
    val xxs: Dp = 2.dp,
    /** 4 dp: icono y texto dentro de una píldora. */
    val xs: Dp = 4.dp,
    /** 8 dp: elementos íntimamente relacionados. */
    val sm: Dp = 8.dp,
    /** 12 dp: interior compacto de controles. */
    val md: Dp = 12.dp,
    /** 16 dp: relleno interior por defecto de tarjetas y celdas. */
    val lg: Dp = 16.dp,
    /** 20 dp: margen lateral de pantalla. */
    val xl: Dp = 20.dp,
    /** 24 dp: separación entre bloques de contenido. */
    val xxl: Dp = 24.dp,
    /** 32 dp: separación entre secciones. */
    val xxxl: Dp = 32.dp,
    /** Margen horizontal estándar de toda pantalla. */
    val screenMargin: Dp = 20.dp,
)

/** Proveedor interno de la escala de espaciado activa. */
internal val LocalBotaSpacing = staticCompositionLocalOf { BotaSpacing() }
