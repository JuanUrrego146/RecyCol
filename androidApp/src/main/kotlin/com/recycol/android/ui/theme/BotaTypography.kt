package com.recycol.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Escala tipográfica del design system, calcada de los estilos de texto de
 * iOS (Large Title … Caption 2) sobre la tipografía del sistema. Es la única
 * fuente de estilos de texto de la interfaz (RNF-009): ninguna pantalla
 * declara tamaños, pesos ni interlineados por su cuenta.
 */
@Immutable
data class BotaTypography(
    /** Título de portada de pantalla, 34 sp. */
    val largeTitle: TextStyle,
    /** Título de primer nivel, 28 sp. */
    val title1: TextStyle,
    /** Título de segundo nivel, 22 sp. */
    val title2: TextStyle,
    /** Título de tercer nivel, 20 sp. */
    val title3: TextStyle,
    /** Texto destacado del cuerpo, 17 sp seminegrita; botones y cabeceras de celda. */
    val headline: TextStyle,
    /** Cuerpo de texto por defecto, 17 sp. */
    val body: TextStyle,
    /** Cuerpo compacto, 16 sp. */
    val callout: TextStyle,
    /** Texto secundario, 15 sp. */
    val subheadline: TextStyle,
    /** Nota al pie, 13 sp; metadatos y aclaraciones. */
    val footnote: TextStyle,
    /** Nota al pie enfatizada, 13 sp seminegrita; píldoras de estado. */
    val footnoteEmphasized: TextStyle,
    /** Leyenda, 12 sp. */
    val caption1: TextStyle,
    /** Leyenda mínima, 11 sp. */
    val caption2: TextStyle,
)

private val SystemFont = FontFamily.Default

internal val DefaultBotaTypography = BotaTypography(
    largeTitle = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = (-0.4).sp,
    ),
    title1 = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp,
    ),
    title2 = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    title3 = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.1).sp,
    ),
    headline = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    body = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    callout = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    subheadline = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    footnote = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    footnoteEmphasized = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    caption1 = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    caption2 = TextStyle(
        fontFamily = SystemFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 13.sp,
    ),
)

/** Proveedor interno de la escala tipográfica activa. */
internal val LocalBotaTypography = staticCompositionLocalOf { DefaultBotaTypography }
