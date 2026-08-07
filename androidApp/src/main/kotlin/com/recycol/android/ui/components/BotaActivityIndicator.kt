package com.recycol.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.recycol.android.ui.theme.BotaTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * Indicador de actividad del design system: la rueda de radios que se
 * desvanecen de iOS, en versión discreta (RNF-009). Se usa para esperas
 * cortas e indeterminadas; no bloquea ni oscurece el contenido.
 *
 * @param size Diámetro del indicador; 20 dp por defecto.
 * @param tint Color de los radios; por defecto la etiqueta secundaria.
 * @param contentDescription Descripción para lectores de pantalla. `null`
 *   lo marca como decorativo; la pantalla anfitriona debe anunciar la espera.
 */
@Composable
fun BotaActivityIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = BotaTheme.colors.secondaryLabel,
    contentDescription: String? = null,
) {
    val transition = rememberInfiniteTransition(label = "botaActivityIndicator")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = SPOKE_COUNT.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ROTATION_MS, easing = LinearEasing),
        ),
        label = "botaActivityIndicatorPhase",
    )

    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.size(size).then(semantics)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val outerRadius = this.size.minDimension / 2f
        val innerRadius = outerRadius * INNER_RADIUS_FRACTION
        val strokeWidth = outerRadius * STROKE_FRACTION

        repeat(SPOKE_COUNT) { index ->
            val angle = Math.toRadians((index * 360.0 / SPOKE_COUNT) - 90.0)
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            // Cada radio se apaga progresivamente según su distancia a la fase actual.
            val distance = (index - phase + SPOKE_COUNT) % SPOKE_COUNT
            val alpha = MIN_SPOKE_ALPHA + (1f - MIN_SPOKE_ALPHA) * (1f - distance / SPOKE_COUNT)

            drawLine(
                color = tint.copy(alpha = tint.alpha * alpha),
                start = center + direction * innerRadius,
                end = center + direction * outerRadius,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

private const val SPOKE_COUNT = 8
private const val ROTATION_MS = 800
private const val INNER_RADIUS_FRACTION = 0.5f
private const val STROKE_FRACTION = 0.22f
private const val MIN_SPOKE_ALPHA = 0.15f
