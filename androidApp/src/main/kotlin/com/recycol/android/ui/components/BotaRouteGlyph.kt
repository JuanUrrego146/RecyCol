package com.recycol.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.recycol.domain.model.DisposalRoute

/**
 * Glifo geométrico por ruta de disposición (RNF-010): la caneca se comunica
 * por color, **texto e icono**, nunca solo por color. Cada ruta tiene una
 * forma distinta y estable, dibujada en el color del contenido circundante
 * para garantizar el contraste en cualquier superficie:
 *
 * - Aprovechable: triángulo (eco del símbolo de reciclaje).
 * - No aprovechable: cuadrado.
 * - Orgánico: círculo.
 * - Peligroso: rombo (señal de riesgo).
 * - Recolección especial: estrella de cuatro puntas.
 *
 * El glifo es refuerzo visual: el nombre de la ruta va siempre en texto al
 * lado, así que se marca decorativo (sin semántica propia).
 */
@Composable
fun BotaRouteGlyph(
    route: DisposalRoute,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(
            width = this.size.minDimension * STROKE_FRACTION,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (route) {
            DisposalRoute.RECYCLABLE -> drawPath(trianglePath(), color, style = stroke)
            DisposalRoute.NON_RECYCLABLE -> drawPath(squarePath(), color, style = stroke)
            DisposalRoute.ORGANIC -> drawCircle(
                color = color,
                radius = this.size.minDimension * CIRCLE_RADIUS_FRACTION,
                style = stroke,
            )
            DisposalRoute.HAZARDOUS -> drawPath(diamondPath(), color, style = stroke)
            DisposalRoute.SPECIAL_COLLECTION -> drawPath(starPath(), color, style = stroke)
        }
    }
}

private fun DrawScope.trianglePath(): Path = Path().apply {
    moveTo(size.width * 0.5f, size.height * 0.12f)
    lineTo(size.width * 0.90f, size.height * 0.85f)
    lineTo(size.width * 0.10f, size.height * 0.85f)
    close()
}

private fun DrawScope.squarePath(): Path = Path().apply {
    moveTo(size.width * 0.15f, size.height * 0.15f)
    lineTo(size.width * 0.85f, size.height * 0.15f)
    lineTo(size.width * 0.85f, size.height * 0.85f)
    lineTo(size.width * 0.15f, size.height * 0.85f)
    close()
}

private fun DrawScope.diamondPath(): Path = Path().apply {
    moveTo(size.width * 0.5f, size.height * 0.08f)
    lineTo(size.width * 0.92f, size.height * 0.5f)
    lineTo(size.width * 0.5f, size.height * 0.92f)
    lineTo(size.width * 0.08f, size.height * 0.5f)
    close()
}

private fun DrawScope.starPath(): Path = Path().apply {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val outer = size.minDimension * 0.44f
    val inner = size.minDimension * 0.16f
    moveTo(cx, cy - outer)
    lineTo(cx + inner, cy - inner)
    lineTo(cx + outer, cy)
    lineTo(cx + inner, cy + inner)
    lineTo(cx, cy + outer)
    lineTo(cx - inner, cy + inner)
    lineTo(cx - outer, cy)
    lineTo(cx - inner, cy - inner)
    close()
}

private const val STROKE_FRACTION = 0.12f
private const val CIRCLE_RADIUS_FRACTION = 0.38f
