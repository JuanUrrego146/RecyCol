package com.botabien.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.botabien.android.ui.theme.BotaTheme

/**
 * Marca de verificación del design system, al estilo del check de las listas
 * de iOS. Señala la opción elegida en listas de selección única.
 *
 * @param contentDescription descripción para lectores de pantalla; el
 *   componente marca estado, así que normalmente es «Seleccionado» (RNF-010).
 */
@Composable
fun BotaSelectionMark(
    modifier: Modifier = Modifier,
    color: Color = BotaTheme.colors.accent,
    size: Dp = 18.dp,
    contentDescription: String? = null,
) {
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Canvas(modifier = modifier.size(size).then(semantics)) {
        val stroke = this.size.minDimension * STROKE_FRACTION
        drawLine(
            color = color,
            start = Offset(this.size.width * 0.12f, this.size.height * 0.55f),
            end = Offset(this.size.width * 0.40f, this.size.height * 0.82f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(this.size.width * 0.40f, this.size.height * 0.82f),
            end = Offset(this.size.width * 0.88f, this.size.height * 0.20f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private const val STROKE_FRACTION = 0.14f
