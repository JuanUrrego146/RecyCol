package com.botabien.android.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.botabien.android.ui.theme.BotaGlassMaterial
import com.botabien.android.ui.theme.BotaMotion
import com.botabien.android.ui.theme.BotaTheme
import com.botabien.android.ui.theme.LocalGlassMaterial

/**
 * Qué está haciendo la app, contado por el propio material. El estado deja de
 * ser solo una línea de texto: el cristal respira mientras se analiza y se
 * empaña cuando la app duda, que es exactamente lo que le pasa por dentro.
 */
enum class BotaGlassState {

    /** En reposo, con una decisión tomada o sin nada que decir. */
    Settled,

    /** Analizando: el borde recorre un pulso de luz. */
    Analyzing,

    /**
     * Baja confianza: el material se difumina y pierde definición en el borde.
     * Es el flujo protagonista de la app, no un error, y el material lo dice
     * sin culpar a nadie.
     */
    Uncertain,
}

/**
 * Superficie de cristal del design system: el material translúcido que flota
 * sobre el contenido.
 *
 * Se construye con cuatro capas, y **las cuatro hacen falta** para que se lea
 * como cristal y no como un velo gris:
 *
 * 1. **Atenuación**, que sostiene el contraste del texto sobre vídeo en vivo.
 * 2. **Luz cenital**, que hace que el material parezca iluminado desde arriba.
 * 3. **Canto biselado**: sendas bandas de luz pegadas al borde superior e
 *    inferior. Es lo que da sensación de *grosor* — sin ellas la superficie
 *    parece pintada sobre la pantalla en vez de flotar por encima.
 * 4. **Brillo especular** en el contorno, más intenso donde daría la luz.
 *
 * Más una sombra exterior que la despega del fondo. La primera versión de este
 * componente se quedó corta justamente en el bisel y en la sombra, y el
 * resultado fue que el material no se distinguía del velo plano anterior.
 *
 * @param tint tinte de la caneca decidida. Sigue siendo dato del perfil
 *   normativo (`BinDefinition.colorHex`), no una decisión de diseño: el cristal
 *   solo lo deja pasar como un lavado, nunca como color de fondo.
 * @param state estado que el material comunica; ver [BotaGlassState].
 */
@Composable
fun BotaGlass(
    modifier: Modifier = Modifier,
    shape: Shape = BotaTheme.shapes.large,
    tint: Color? = null,
    state: BotaGlassState = BotaGlassState.Settled,
    contentPadding: PaddingValues = PaddingValues(BotaTheme.spacing.lg),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .botaGlass(shape = shape, tint = tint, state = state)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * El material como modificador, para lo que no es un contenedor: el marco de
 * encuadre, una cápsula de indicación o cualquier superficie que ya tenga su
 * propia disposición.
 */
@Composable
fun Modifier.botaGlass(
    shape: Shape = BotaTheme.shapes.large,
    tint: Color? = null,
    state: BotaGlassState = BotaGlassState.Settled,
    rimWidth: Dp = RIM_WIDTH,
    filled: Boolean = true,
): Modifier {
    val material = LocalGlassMaterial.current
    val colors = BotaTheme.colors

    // El empañado de la duda es una transición, no un salto: el material se
    // difumina a la vez que la app reconoce que no lo tiene claro.
    val haze by animateFloatAsState(
        targetValue = if (state == BotaGlassState.Uncertain) 1f else 0f,
        animationSpec = tween(BotaMotion.DURATION_BASE_MS, easing = BotaMotion.easeInOut),
        label = "glassHaze",
    )
    val breath = rememberBreath(active = state == BotaGlassState.Analyzing)

    val opaque = material == BotaGlassMaterial.Veil
    val dimAlpha = if (opaque) VEIL_DIM_ALPHA else CLEAR_DIM_ALPHA
    val lightTop = (if (opaque) VEIL_LIGHT_ALPHA else CLEAR_LIGHT_ALPHA) +
        haze * HAZE_LIGHT_GAIN
    val bevelAlpha = (if (opaque) VEIL_BEVEL_ALPHA else CLEAR_BEVEL_ALPHA) *
        (1f - haze * HAZE_BEVEL_LOSS)
    val rimAlpha = (if (opaque) VEIL_RIM_ALPHA else CLEAR_RIM_ALPHA) *
        (1f - haze * HAZE_RIM_LOSS) * breath
    val transparent = colors.onScrim.copy(alpha = 0f)

    return this
        .then(
            if (opaque || !filled) {
                Modifier
            } else {
                // Sombra exterior: sin ella la superficie parece pintada sobre
                // la pantalla; con ella flota por encima del contenido.
                Modifier.shadow(elevation = GLASS_ELEVATION, shape = shape, clip = false)
            },
        )
        .clip(shape)
        .then(
            if (!filled) {
                // Solo el brillo del borde: es lo que necesita el marco de
                // encuadre, que no puede atenuar lo que el usuario está
                // intentando enfocar.
                Modifier
            } else {
                Modifier
                    // 1 · Atenuación. Va primero y no se salta ni en el material
                    // más transparente: es lo único que sostiene el contraste
                    // del texto cuando detrás hay vídeo en vivo (RNF-010).
                    .background(color = colors.scrim.copy(alpha = dimAlpha), shape = shape)
                    // 2 · Luz cenital.
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                colors.onScrim.copy(alpha = lightTop),
                                colors.onScrim.copy(alpha = lightTop * LIGHT_BOTTOM_RATIO),
                            ),
                        ),
                        shape = shape,
                    )
                    // 3 · Canto biselado: la luz se concentra en una banda
                    // pegada al borde, como en el canto de un vidrio grueso.
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to colors.onScrim.copy(alpha = bevelAlpha),
                                BEVEL_STOP to transparent,
                                1f - BEVEL_STOP to transparent,
                                1f to colors.onScrim.copy(alpha = bevelAlpha * BEVEL_BOTTOM_RATIO),
                            ),
                        ),
                        shape = shape,
                    )
                    .then(
                        if (tint == null) {
                            Modifier
                        } else {
                            // El color de caneca entra como lavado, no como
                            // relleno: informa sin competir con el texto.
                            Modifier.background(
                                color = tint.copy(alpha = TINT_ALPHA),
                                shape = shape,
                            )
                        },
                    )
            },
        )
        // 4 · Brillo especular del contorno, más intenso arriba a la izquierda,
        // donde daría la luz, con un rebote tenue en el canto opuesto.
        .border(
            width = rimWidth,
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to colors.onScrim.copy(alpha = rimAlpha),
                    RIM_SHADE_STOP to colors.onScrim.copy(alpha = rimAlpha * RIM_SHADE_RATIO),
                    1f to colors.onScrim.copy(alpha = rimAlpha * RIM_BOUNCE_RATIO),
                ),
                start = Offset.Zero,
                end = Offset.Infinite,
            ),
            shape = shape,
        )
}

/**
 * Pulso lento para el estado de análisis. Devuelve 1 cuando no está activo, de
 * modo que el material en reposo no paga ninguna animación.
 */
@Composable
private fun rememberBreath(active: Boolean): Float {
    if (!active) return 1f
    val transition = rememberInfiniteTransition(label = "glassBreath")
    val breath by transition.animateFloat(
        initialValue = BREATH_MIN,
        targetValue = BREATH_MAX,
        animationSpec = infiniteRepeatable(
            animation = tween(BREATH_PERIOD_MS, easing = BotaMotion.easeInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glassBreathValue",
    )
    return breath
}

/** Grosor del brillo del borde. */
private val RIM_WIDTH = 1.5.dp

/** Elevación de la sombra que despega el cristal del fondo. */
private val GLASS_ELEVATION = 12.dp

/** Atenuación del material transparente: deja ver, pero sostiene el contraste. */
private const val CLEAR_DIM_ALPHA = 0.42f

/** Atenuación del material opaco. */
private const val VEIL_DIM_ALPHA = 0.90f

/** Lavado de luz del material transparente. */
private const val CLEAR_LIGHT_ALPHA = 0.26f

/** Lavado de luz del material opaco, apenas insinuado. */
private const val VEIL_LIGHT_ALPHA = 0.07f

/** Proporción de luz que queda en el canto inferior. */
private const val LIGHT_BOTTOM_RATIO = 0.22f

/** Intensidad de la banda de canto en el material transparente. */
private const val CLEAR_BEVEL_ALPHA = 0.45f

/** Intensidad de la banda de canto en el material opaco. */
private const val VEIL_BEVEL_ALPHA = 0.10f

/** Grosor relativo de la banda de canto respecto al alto de la superficie. */
private const val BEVEL_STOP = 0.045f

/** Proporción de luz que recoge el canto inferior. */
private const val BEVEL_BOTTOM_RATIO = 0.55f

/** Intensidad del brillo del contorno en el material transparente. */
private const val CLEAR_RIM_ALPHA = 0.85f

/** Intensidad del brillo del contorno en el material opaco. */
private const val VEIL_RIM_ALPHA = 0.18f

/** Punto del contorno donde la luz decae antes de rebotar. */
private const val RIM_SHADE_STOP = 0.55f

/** Cuánta luz queda en el tramo sombreado del contorno. */
private const val RIM_SHADE_RATIO = 0.20f

/** Rebote de luz en el canto opuesto al foco. */
private const val RIM_BOUNCE_RATIO = 0.5f

/** Opacidad con la que el color de caneca atraviesa el cristal. */
private const val TINT_ALPHA = 0.22f

/** Luz añadida cuando el material se empaña por la duda. */
private const val HAZE_LIGHT_GAIN = 0.14f

/** Definición que pierde el canto cuando el material se empaña. */
private const val HAZE_BEVEL_LOSS = 0.7f

/** Definición que pierde el contorno cuando el material se empaña. */
private const val HAZE_RIM_LOSS = 0.55f

/** Extremos del pulso del contorno mientras se analiza. */
private const val BREATH_MIN = 0.3f
private const val BREATH_MAX = 1f

/** Periodo del pulso: lento, para que acompañe sin distraer. */
private const val BREATH_PERIOD_MS = 1_100
