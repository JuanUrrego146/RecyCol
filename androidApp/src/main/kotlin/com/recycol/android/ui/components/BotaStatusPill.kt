package com.recycol.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.recycol.android.ui.theme.BotaTheme

/**
 * Tono semántico de la píldora de estado. El tono decide color de tinte y
 * de texto; la información nunca se comunica solo por color (RNF-010): el
 * texto de la píldora es siempre obligatorio.
 */
enum class BotaStatusTone {
    /** Información sin carga semántica. */
    Neutral,

    /** Relacionado con la acción o marca. */
    Accent,

    /** Estado positivo: confianza alta, clasificación resuelta. */
    Success,

    /** Precaución: confianza media, condiciones de captura mejorables. */
    Warning,

    /** Error: clasificación fallida, perfil inválido. */
    Error,
}

/**
 * Píldora de estado del design system: cápsula tintada con texto corto,
 * al estilo de las etiquetas discretas de iOS (RNF-009). Pensada para
 * estados de confianza, disponibilidad de canecas y avisos breves.
 *
 * @param text Etiqueta visible; debe venir de un recurso de cadenas (RNF-011).
 * @param tone Tono semántico del estado.
 * @param leading Icono opcional de 14 dp delante del texto.
 */
@Composable
fun BotaStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: BotaStatusTone = BotaStatusTone.Neutral,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = BotaTheme.colors
    val contentColor = when (tone) {
        BotaStatusTone.Neutral -> colors.secondaryLabel
        BotaStatusTone.Accent -> colors.accent
        BotaStatusTone.Success -> colors.success
        BotaStatusTone.Warning -> colors.warning
        BotaStatusTone.Error -> colors.error
    }
    val containerColor = when (tone) {
        BotaStatusTone.Neutral -> colors.fill
        else -> contentColor.copy(alpha = TINT_ALPHA)
    }

    Row(
        modifier = modifier
            .clip(BotaTheme.shapes.capsule)
            .background(containerColor)
            .padding(
                horizontal = BotaTheme.spacing.md,
                vertical = BotaTheme.spacing.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BotaTheme.spacing.xs),
    ) {
        if (leading != null) {
            Box(modifier = Modifier.size(14.dp)) {
                leading()
            }
        }
        Text(
            text = text,
            style = BotaTheme.typography.footnoteEmphasized,
            color = contentColor,
        )
    }
}

/** Opacidad del tinte de fondo de los tonos semánticos. */
private const val TINT_ALPHA = 0.15f
