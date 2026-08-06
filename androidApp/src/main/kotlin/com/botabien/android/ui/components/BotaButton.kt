package com.botabien.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.botabien.android.ui.theme.BotaMotion
import com.botabien.android.ui.theme.BotaTheme

/**
 * Variantes del botón del design system, en orden de énfasis decreciente.
 */
enum class BotaButtonStyle {
    /** Relleno con el color de acento: la acción principal de la pantalla. */
    Filled,

    /** Tinte translúcido del acento: acciones secundarias. */
    Tinted,

    /** Solo texto: acciones terciarias y de navegación. */
    Plain,
}

/**
 * Botón estándar del design system, de estética iOS: esquinas amplias,
 * sin ripple de Material y con respuesta táctil de escala y opacidad
 * animada por muelle (RNF-009).
 *
 * El ancho lo decide quien llama: la acción principal de una pantalla se
 * extiende con `Modifier.fillMaxWidth()`; un botón inline (barras, filas)
 * ocupa solo su contenido.
 *
 * @param text Etiqueta visible; debe venir de un recurso de cadenas (RNF-011).
 * @param onClick Acción al pulsar.
 * @param style Variante de énfasis; por defecto [BotaButtonStyle.Filled].
 * @param enabled Deshabilita la interacción y atenúa el control.
 * @param compact Altura reducida para contextos densos (44 dp frente a 50 dp).
 */
@Composable
fun BotaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BotaButtonStyle = BotaButtonStyle.Filled,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val colors = BotaTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) BotaMotion.PRESSED_SCALE else 1f,
        animationSpec = BotaMotion.pressSpring(),
        label = "botaButtonScale",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (pressed) BotaMotion.PRESSED_ALPHA else 1f,
        animationSpec = BotaMotion.pressSpring(),
        label = "botaButtonAlpha",
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> colors.secondaryFill
            style == BotaButtonStyle.Filled -> colors.accent
            style == BotaButtonStyle.Tinted -> colors.accent.copy(alpha = TINT_ALPHA)
            else -> Color.Transparent
        },
        animationSpec = BotaMotion.pressSpring(),
        label = "botaButtonContainer",
    )
    val contentColor = when {
        !enabled -> colors.tertiaryLabel
        style == BotaButtonStyle.Filled -> colors.onAccent
        else -> colors.accent
    }

    Box(
        modifier = modifier
            .scale(scale)
            .height(if (compact) 44.dp else 50.dp)
            .clip(BotaTheme.shapes.medium)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(PaddingValues(horizontal = BotaTheme.spacing.lg)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = BotaTheme.typography.headline,
            color = contentColor,
            modifier = Modifier.alpha(contentAlpha),
        )
    }
}

/** Opacidad del fondo tintado de [BotaButtonStyle.Tinted]. */
private const val TINT_ALPHA = 0.15f
