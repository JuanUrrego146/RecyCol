package com.botabien.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/**
 * Punto de acceso a los tokens del design system. Toda la capa de interfaz
 * lee de aquí: `BotaTheme.colors`, `BotaTheme.typography`, `BotaTheme.spacing`
 * y `BotaTheme.shapes` (RNF-009).
 */
object BotaTheme {

    val colors: BotaColorScheme
        @Composable @ReadOnlyComposable get() = LocalBotaColors.current

    val typography: BotaTypography
        @Composable @ReadOnlyComposable get() = LocalBotaTypography.current

    val spacing: BotaSpacing
        @Composable @ReadOnlyComposable get() = LocalBotaSpacing.current

    val shapes: BotaShapes
        @Composable @ReadOnlyComposable get() = LocalBotaShapes.current

    /**
     * Grado de material de cristal que este dispositivo puede permitirse. Las
     * superficies de cristal lo consultan solas; una pantalla solo necesita
     * leerlo si va a degradarlo todavía más en un subárbol concreto.
     */
    val glass: BotaGlassMaterial
        @Composable @ReadOnlyComposable get() = LocalGlassMaterial.current
}

/**
 * Tema raíz de la aplicación. Publica los tokens del design system y, por
 * debajo, configura un [MaterialTheme] equivalente para que los componentes
 * de Material 3 que el sistema usa internamente (hojas modales, superficies)
 * hereden la misma apariencia y ningún color ajeno se cuele en pantalla.
 */
@Composable
fun BotaBienTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkBotaColors else LightBotaColors
    val typography = DefaultBotaTypography
    val spacing = BotaSpacing()
    val shapes = BotaShapes()
    // Se resuelve una vez en la raíz: el material de cristal es un token más,
    // no una decisión que cada pantalla vuelva a tomar por su cuenta.
    val glass = rememberGlassMaterial()

    CompositionLocalProvider(
        LocalBotaColors provides colors,
        LocalBotaTypography provides typography,
        LocalBotaSpacing provides spacing,
        LocalBotaShapes provides shapes,
        LocalGlassMaterial provides glass,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = typography.toMaterialTypography(),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(10.dp),
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(14.dp),
                large = RoundedCornerShape(20.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}

/**
 * Traduce el esquema propio al vocabulario de Material 3. Solo lo consume
 * [BotaBienTheme]; las pantallas nunca leen `MaterialTheme.colorScheme`.
 */
private fun BotaColorScheme.toMaterialColorScheme() = if (isDark) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        secondary = accent,
        onSecondary = onAccent,
        background = background,
        onBackground = label,
        surface = surfaceElevated,
        onSurface = label,
        surfaceVariant = surfaceElevated,
        onSurfaceVariant = secondaryLabel,
        error = error,
        onError = onAccent,
        outline = separator,
        outlineVariant = separator,
        scrim = scrim,
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        secondary = accent,
        onSecondary = onAccent,
        background = background,
        onBackground = label,
        surface = surfaceElevated,
        onSurface = label,
        surfaceVariant = surfaceElevated,
        onSurfaceVariant = secondaryLabel,
        error = error,
        onError = onAccent,
        outline = separator,
        outlineVariant = separator,
        scrim = scrim,
    )
}

/**
 * Traduce la escala tipográfica propia a la de Material 3 para los
 * componentes internos de la librería. Las pantallas usan siempre
 * `BotaTheme.typography`.
 */
private fun BotaTypography.toMaterialTypography() = Typography(
    displayLarge = largeTitle,
    displayMedium = title1,
    displaySmall = title2,
    headlineLarge = title1,
    headlineMedium = title2,
    headlineSmall = title3,
    titleLarge = title2,
    titleMedium = headline,
    titleSmall = subheadline,
    bodyLarge = body,
    bodyMedium = callout,
    bodySmall = footnote,
    labelLarge = headline,
    labelMedium = footnoteEmphasized,
    labelSmall = caption2,
)
