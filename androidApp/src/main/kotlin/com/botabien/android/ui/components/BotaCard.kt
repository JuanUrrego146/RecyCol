package com.botabien.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import com.botabien.android.ui.theme.BotaMotion
import com.botabien.android.ui.theme.BotaTheme

/**
 * Tarjeta del design system: superficie plana y elevada al estilo de las
 * listas agrupadas de iOS. Sin sombra ni borde; el contraste lo da el fondo
 * agrupado de la pantalla ([BotaTheme.colors.groupedBackground]) contra la
 * superficie de la tarjeta (RNF-009).
 *
 * @param onClick Si se aporta, la tarjeta entera es pulsable con la misma
 *   respuesta táctil que [BotaButton].
 * @param contentPadding Relleno interior; por defecto el estándar de tarjeta.
 */
@Composable
fun BotaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(BotaTheme.spacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) BotaMotion.PRESSED_SCALE else 1f,
        animationSpec = BotaMotion.pressSpring(),
        label = "botaCardScale",
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clip(BotaTheme.shapes.large)
            .background(BotaTheme.colors.surfaceElevated)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        content = content,
    )
}
