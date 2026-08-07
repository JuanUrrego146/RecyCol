package com.botabien.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.botabien.android.ui.theme.BotaTheme
import kotlin.math.min

/**
 * Logo de BotaBien: un bote de basura lleno de tierra del que brota una flor.
 * La metáfora del producto — el residuo bien clasificado se convierte en vida —
 * con el brote dominando la composición: lo primero que se lee es la flor, no
 * la basura.
 *
 * Se dibuja por trazados en vez de consumir un recurso vectorial porque el
 * arranque necesita animar el brote pieza a pieza ([growth]), y un
 * `VectorDrawable` solo se puede escalar o teñir entero.
 *
 * Un solo color, el de marca: la profundidad sale de la opacidad de la tierra y
 * de la segunda hoja, nunca de un segundo tono. Eso es lo que permite que la
 * variante monocroma del icono de lanzador sea el mismo dibujo sin retocar.
 *
 * @param color tinta del logo; por defecto el acento del tema activo.
 * @param growth progreso del brote, de 0 (solo el bote con su tierra) a 1 (logo
 *   completo). Los valores intermedios los usa la animación de arranque; el
 *   logo estático se dibuja siempre con 1.
 * @param showVessel `false` dibuja solo el brote —tallo, hojas y flor— sin bote
 *   ni tierra. Lo usa la floración que celebra una clasificación acertada, que
 *   es el mismo motivo del logo sin repetir el recipiente.
 * @param contentDescription texto para lectores de pantalla; `null` lo declara
 *   decorativo, que es lo correcto cuando el nombre de la app ya está al lado.
 */
@Composable
fun BotaLogo(
    modifier: Modifier = Modifier,
    color: Color = BotaTheme.colors.accent,
    growth: Float = 1f,
    showVessel: Boolean = true,
    contentDescription: String? = null,
) {
    val shapes = remember { BotaLogoShapes() }
    val semanticsModifier = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }

    Canvas(modifier = semanticsModifier) {
        // El motivo vive en una rejilla cuadrada de 96; se centra dentro del
        // espacio disponible sin deformarse, sea cual sea la caja que reciba.
        val side = min(size.width, size.height)
        val factor = side / LOGO_VIEWPORT
        translate(left = (size.width - side) / 2f, top = (size.height - side) / 2f) {
            scale(scale = factor, pivot = Offset.Zero) {
                drawLogo(
                    shapes = shapes,
                    color = color,
                    growth = growth.coerceIn(0f, 1f),
                    showVessel = showVessel,
                )
            }
        }
    }
}

/**
 * Dibuja el motivo en coordenadas de la rejilla de 96. El orden importa: el
 * bote se pinta después de la tierra para que su trazo la recorte, y la flor
 * al final para que se superponga al tallo.
 */
private fun DrawScope.drawLogo(
    shapes: BotaLogoShapes,
    color: Color,
    growth: Float,
    showVessel: Boolean,
) {
    if (showVessel) {
        drawPath(path = shapes.soil, color = color, alpha = SOIL_ALPHA)
        drawPath(
            path = shapes.rim,
            color = color,
            style = Stroke(width = BODY_STROKE, cap = StrokeCap.Round),
        )
        drawPath(
            path = shapes.body,
            color = color,
            style = Stroke(width = BODY_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    // El tallo se dibuja parcialmente midiendo su longitud: crece de la tierra
    // hacia arriba porque el trazado arranca en su base.
    val stemGrowth = stagger(growth, STEM_START, STEM_END)
    if (stemGrowth > 0f) {
        drawPath(
            path = shapes.stemUpTo(stemGrowth),
            color = color,
            style = Stroke(width = STEM_STROKE, cap = StrokeCap.Round),
        )
    }

    // Hojas y flor abren desde su punto de nacimiento, no desde el centro del
    // lienzo: así el gesto se lee como despliegue y no como un zoom.
    val firstLeaf = stagger(growth, FIRST_LEAF_START, FIRST_LEAF_END)
    if (firstLeaf > 0f) {
        scale(scale = firstLeaf, pivot = FIRST_LEAF_ORIGIN) {
            drawPath(path = shapes.firstLeaf, color = color)
        }
    }
    val secondLeaf = stagger(growth, SECOND_LEAF_START, SECOND_LEAF_END)
    if (secondLeaf > 0f) {
        scale(scale = secondLeaf, pivot = SECOND_LEAF_ORIGIN) {
            drawPath(path = shapes.secondLeaf, color = color, alpha = SECOND_LEAF_ALPHA)
        }
    }
    val bloom = stagger(growth, BLOOM_START, BLOOM_END)
    if (bloom > 0f) {
        scale(scale = bloom, pivot = BLOOM_ORIGIN) {
            drawPath(path = shapes.flower, color = color)
        }
    }
}

/**
 * Reparte un progreso global entre [start] y [end], de modo que las piezas
 * broten escalonadas con un solo parámetro de entrada.
 */
private fun stagger(progress: Float, start: Float, end: Float): Float =
    ((progress - start) / (end - start)).coerceIn(0f, 1f)

/**
 * Trazados del logo ya convertidos a [Path], listos para dibujar. Se construyen
 * una vez y se recuerdan: convertir cadenas en cada fotograma sería gratuito en
 * gama alta y caro donde importa.
 */
private class BotaLogoShapes {
    val soil: Path = BotaLogoPaths.SOIL.toPath()
    val rim: Path = BotaLogoPaths.RIM.toPath()
    val body: Path = BotaLogoPaths.BODY.toPath()
    val firstLeaf: Path = BotaLogoPaths.FIRST_LEAF.toPath()
    val secondLeaf: Path = BotaLogoPaths.SECOND_LEAF.toPath()
    val flower: Path = BotaLogoPaths.FLOWER.toPath()

    private val stem: Path = BotaLogoPaths.STEM.toPath()
    private val stemMeasure = PathMeasure().apply { setPath(stem, false) }
    private val stemSegment = Path()

    /** Devuelve el tallo recortado a la fracción [fraction] de su longitud. */
    fun stemUpTo(fraction: Float): Path {
        if (fraction >= 1f) return stem
        stemSegment.reset()
        stemMeasure.getSegment(0f, stemMeasure.length * fraction, stemSegment, true)
        return stemSegment
    }
}

private fun String.toPath(): Path = PathParser().parsePathString(this).toPath()

/**
 * Trazados del logo en la rejilla de 96, en coordenadas absolutas. **Son el
 * origen de verdad del logo**: las capas del icono de lanzador y el SVG maestro
 * de `docs/brand/` repiten estas mismas cadenas, y `BotaLogoResourcesTest`
 * comprueba que no divergen.
 */
internal object BotaLogoPaths {

    /** Tierra que llena el bote hasta el borde. */
    const val SOIL =
        "M33.5,67 L34.6,81.5 Q34.9,84.5 38.2,84.5 L57.8,84.5 " +
            "Q61.1,84.5 61.4,81.5 L62.5,67 Q55,70 48,68 Q41,66 33.5,67 Z"

    /** Borde superior del bote. */
    const val RIM = "M25,58 L71,58"

    /** Cuerpo del bote: base compacta, apenas un tercio del alto. */
    const val BODY = "M30.5,61 L34,81 Q34.5,85 38.8,85 L57.2,85 Q61.5,85 62,81 L65.5,61"

    /** Tallo, de la tierra hacia arriba; la curvatura mínima evita el aire de regla. */
    const val STEM = "M48,62 C47,52 47.5,44 48,36"

    /** Hoja izquierda. */
    const val FIRST_LEAF = "M48,52 C41,52.5 36,48.5 34.4,42.4 C42,40.8 47,45.2 48,52 Z"

    /** Hoja derecha, atenuada, que aporta la profundidad. */
    const val SECOND_LEAF = "M48,45 C55,45.5 60,41.5 61.6,35.4 C54,33.8 49,38.2 48,45 Z"

    /**
     * Flor de cinco pétalos con el centro perforado. Los pétalos se trazan en
     * sentido horario y el centro en sentido antihorario: con relleno nonZero
     * eso resta el hueco sin máscaras, que el formato vectorial de Android no
     * admite. El hueco es lo que mantiene la flor legible a 24 dp.
     */
    const val FLOWER =
        "M41.4,13.3 a6.6,6.6 0 1,1 13.2,0 a6.6,6.6 0 1,1 -13.2,0 Z " +
            "M49.7,19.3 a6.6,6.6 0 1,1 13.2,0 a6.6,6.6 0 1,1 -13.2,0 Z " +
            "M46.5,29.1 a6.6,6.6 0 1,1 13.2,0 a6.6,6.6 0 1,1 -13.2,0 Z " +
            "M36.3,29.1 a6.6,6.6 0 1,1 13.2,0 a6.6,6.6 0 1,1 -13.2,0 Z " +
            "M33.1,19.3 a6.6,6.6 0 1,1 13.2,0 a6.6,6.6 0 1,1 -13.2,0 Z " +
            "M44,22 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0 Z"

    /** Lado de la rejilla en la que están expresados todos los trazados. */
    const val VIEWPORT = 96f
}

private const val LOGO_VIEWPORT = BotaLogoPaths.VIEWPORT

/** Grosor del trazo del bote, en unidades de la rejilla. */
private const val BODY_STROKE = 6f

/** Grosor del trazo del tallo, algo más fino que el del bote. */
private const val STEM_STROKE = 5.5f

/** Opacidad de la tierra: presente pero sin competir con el brote. */
private const val SOIL_ALPHA = 0.33f

/** Opacidad de la segunda hoja, que da profundidad sin un segundo color. */
private const val SECOND_LEAF_ALPHA = 0.55f

// Tramos del brote dentro del progreso global: se solapan a propósito para que
// el gesto sea continuo en vez de tres pasos encadenados.
private const val STEM_START = 0f
private const val STEM_END = 0.55f
private const val FIRST_LEAF_START = 0.32f
private const val FIRST_LEAF_END = 0.68f
private const val SECOND_LEAF_START = 0.46f
private const val SECOND_LEAF_END = 0.82f
private const val BLOOM_START = 0.6f
private const val BLOOM_END = 1f

/** Puntos de nacimiento de cada pieza, en coordenadas de la rejilla. */
private val FIRST_LEAF_ORIGIN = Offset(48f, 52f)
private val SECOND_LEAF_ORIGIN = Offset(48f, 45f)
private val BLOOM_ORIGIN = Offset(48f, 22f)
