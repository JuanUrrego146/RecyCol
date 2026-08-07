package com.botabien.android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Paleta semántica del design system de BotaBien, inspirada en los colores
 * de sistema de iOS. Es la única fuente de color de la capa de interfaz
 * (RNF-009): las pantallas consumen [BotaTheme.colors] y nunca declaran
 * valores `Color(...)` propios.
 *
 * Excepción deliberada: los colores de caneca llegan como datos del perfil
 * normativo del país activo (`BinDefinition.colorHex`) y se convierten en
 * color en tiempo de ejecución. El design system no los conoce ni los fija.
 */
@Immutable
data class BotaColorScheme(
    /** Color de acento de la marca; único color protagonista de la UI. */
    val accent: Color,
    /** Contenido sobre superficies rellenas con [accent]. */
    val onAccent: Color,

    /** Fondo principal de pantallas planas. */
    val background: Color,
    /** Fondo base de pantallas con contenido agrupado en tarjetas. */
    val groupedBackground: Color,
    /** Superficie de tarjetas y hojas que flotan sobre el fondo. */
    val surfaceElevated: Color,

    /** Texto principal. */
    val label: Color,
    /** Texto secundario: subtítulos, descripciones. */
    val secondaryLabel: Color,
    /** Texto terciario: metadatos, estados deshabilitados. */
    val tertiaryLabel: Color,

    /** Relleno sutil para controles neutros (píldoras, asas, campos). */
    val fill: Color,
    /** Relleno aún más sutil, para fondos de control grandes. */
    val secondaryFill: Color,
    /** Líneas divisorias finas. */
    val separator: Color,

    /** Estado positivo: clasificación resuelta, confianza alta. */
    val success: Color,
    /** Estado de precaución: confianza media, sugerencias de captura. */
    val warning: Color,
    /** Estado de error: fallo de clasificación, perfil inválido. */
    val error: Color,
    /** Estado informativo neutro. */
    val info: Color,

    /** Velo translúcido sobre la vista de cámara y bajo las hojas. */
    val scrim: Color,
    /** Contenido dibujado sobre [scrim]; constante entre temas. */
    val onScrim: Color,
    /**
     * Acento para lo que se dibuja **sobre el velo**, donde el fondo siempre es
     * oscuro. El [accent] del esquema claro es un verde profundo calculado para
     * contrastar sobre blanco, y sobre la cámara se apaga; este es el mismo
     * verde de marca subido de luminosidad, constante entre temas.
     */
    val accentOnScrim: Color,
    /** Fondo del área de cámara: oscuro en ambos temas, como un visor real. */
    val cameraBackdrop: Color,

    /** Verdadero cuando el esquema activo es el oscuro. */
    val isDark: Boolean,
)

/**
 * Esquema claro. Los tonos semánticos usan las variantes accesibles de la
 * paleta iOS para garantizar contraste AA sobre fondos claros (RNF-010).
 */
internal val LightBotaColors = BotaColorScheme(
    accent = Color(0xFF1B7F3C),
    onAccent = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    groupedBackground = Color(0xFFF2F2F7),
    surfaceElevated = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    // 76 % de opacidad (frente al 60 % de iOS) para garantizar contraste AA
    // de texto normal sobre los fondos claros (RNF-010).
    secondaryLabel = Color(0xC23C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    fill = Color(0x33787880),
    secondaryFill = Color(0x29787880),
    separator = Color(0x4A3C3C43),
    success = Color(0xFF248A3D),
    warning = Color(0xFFC93400),
    error = Color(0xFFD70015),
    info = Color(0xFF0040DD),
    scrim = Color(0x8A000000),
    onScrim = Color(0xFFFFFFFF),
    accentOnScrim = Color(0xFF30D158),
    cameraBackdrop =Color(0xFF0A0A0C),
    isDark = false,
)

/**
 * Esquema oscuro. El acento sube de luminosidad y su contenido pasa a
 * oscuro para conservar el contraste AA (RNF-010).
 */
internal val DarkBotaColors = BotaColorScheme(
    accent = Color(0xFF30D158),
    onAccent = Color(0xFF052E16),
    background = Color(0xFF000000),
    groupedBackground = Color(0xFF000000),
    surfaceElevated = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    fill = Color(0x5C787880),
    secondaryFill = Color(0x52787880),
    separator = Color(0x99545458),
    success = Color(0xFF30D158),
    warning = Color(0xFFFFB340),
    error = Color(0xFFFF6961),
    info = Color(0xFF409CFF),
    scrim = Color(0x8A000000),
    onScrim = Color(0xFFFFFFFF),
    accentOnScrim = Color(0xFF30D158),
    cameraBackdrop =Color(0xFF000000),
    isDark = true,
)

/** Proveedor interno del esquema de color activo. */
internal val LocalBotaColors = staticCompositionLocalOf { LightBotaColors }
