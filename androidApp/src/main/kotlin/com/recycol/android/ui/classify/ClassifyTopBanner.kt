package com.recycol.android.ui.classify

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.recycol.android.R
import com.recycol.android.ui.components.BotaLogo
import com.recycol.android.ui.components.botaGlass
import com.recycol.android.ui.theme.BotaMotion
import com.recycol.android.ui.theme.BotaTheme

/**
 * Barra superior de la pantalla de cámara: quién eres y bajo qué norma estás
 * clasificando, flotando sobre el vídeo en vivo.
 *
 * Es la pieza donde mejor se ve el material de cristal —una superficie ancha
 * sobre imagen en movimiento— y por eso lleva el cromo de navegación entero:
 * antes había dos cápsulas sueltas en las esquinas que no daban esa lectura.
 *
 * El país no es decoración: es lo que determina a qué caneca va cada residuo,
 * así que va siempre visible y a un toque de poder cambiarse.
 *
 * @param countryName país activo ya traducido a nombre visible; `null` mientras
 *   el perfil todavía no ha cargado, en cuyo caso no se reserva hueco.
 */
@Composable
internal fun ClassifyTopBanner(
    countryName: String?,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .botaGlass(shape = BotaTheme.shapes.large)
            .padding(
                horizontal = BotaTheme.spacing.lg,
                vertical = BotaTheme.spacing.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotaLogo(
                modifier = Modifier.size(BANNER_LOGO_SIZE),
                color = BotaTheme.colors.onScrim,
            )
            Spacer(modifier = Modifier.width(BotaTheme.spacing.sm))
            Text(
                text = stringResource(R.string.app_name),
                style = BotaTheme.typography.headline,
                color = BotaTheme.colors.onScrim,
            )
        }

        if (countryName != null) {
            CountryChip(name = countryName, onClick = onOpenSettings)
        }
    }
}

/**
 * País activo como cápsula pulsable. Lleva a ajustes, que es donde se cambia:
 * tocar el país para cambiarlo es lo que cualquiera intentaría primero.
 */
@Composable
private fun CountryChip(
    name: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) BotaMotion.PRESSED_SCALE else 1f,
        animationSpec = BotaMotion.pressSpring(),
        label = "countryChipScale",
    )
    val label = stringResource(R.string.banner_change_country_action)

    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .botaGlass(shape = BotaTheme.shapes.capsule)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .padding(
                horizontal = BotaTheme.spacing.md,
                vertical = BotaTheme.spacing.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = BotaTheme.typography.footnoteEmphasized,
            color = BotaTheme.colors.onScrim,
        )
    }
}

/** Tamaño del logo dentro de la barra: presente sin robar protagonismo. */
private val BANNER_LOGO_SIZE = 22.dp
