package com.botabien.android.ui.components

import androidx.compose.animation.core.InfiniteRepeatableSpec
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

    /** Analizando: el borde respira despacio, sin llamar la atención. */
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
 * sobre el contenido, con su atenuación para sostener la legibilidad y el brillo
 * especular que remata el borde.
 *
 * El grado de material lo decide [LocalGlassMaterial], no el llamador: en gama
 * baja, con ahorro de energía o con las animaciones desactivadas, la misma
 * llamada produce una superficie opaca que cuesta lo mismo que un fondo plano.
 * **La jerarquía y la legibilidad no cambian entre grados**; solo cambia el
 * material.
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
    val rimAlpha = (if (opaque) VEIL_RIM_ALPHA else CLEAR_RIM_ALPHA) *
        (1f - haze * HAZE_RIM_LOSS) * breath

    return this
        .clip(shape)
        .then(
            if (!filled) {
                // Solo el brillo del borde: es lo que necesita el marco de
                // encuadre, que no puede atenuar lo que el usuario está
                // intentando enfocar.
                Modifier
            } else {
                Modifier
                    // Atenuación: es lo que sostiene el contraste del texto
                    // cuando detrás hay vídeo en vivo. Va primero y no se salta
                    // ni en el material más transparente (RNF-010).
                    .background(color = colors.scrim.copy(alpha = dimAlpha), shape = shape)
                    // Lavado de luz: el material parece iluminado desde arriba
                    // en vez de ser una capa gris uniforme. Es lo que separa el
                    // cristal del velo plano de toda la vida.
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                colors.onScrim.copy(alpha = lightTop),
                                colors.onScrim.copy(alpha = lightTop * LIGHT_BOTTOM_RATIO),
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
        // Brillo especular del borde: más intenso arriba a la izquierda, donde
        // daría la luz, y con un rebote tenue en el canto inferior.
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
        animationSpec = InfiniteRepeatableSpec(
            animation = tween(BREATH_PERIOD_MS, easing = BotaMotion.easeInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glassBreathValue",
    )
    return breath
}

/** Grosor del brillo del borde; un cabello, como en iOS. */
private val RIM_WIDTH = 1.dp

/** Atenuación del material transparente: deja ver, pero sostiene el contraste. */
private const val CLEAR_DIM_ALPHA = 0.40f

/** Atenuación del material opaco de gama baja. */
private const val VEIL_DIM_ALPHA = 0.88f

/** Lavado de luz del material transparente. */
private const val CLEAR_LIGHT_ALPHA = 0.20f

/** Lavado de luz del material opaco, apenas insinuado. */
private const val VEIL_LIGHT_ALPHA = 0.06f

/** Proporción de luz que queda en el canto inferior. */
private const val LIGHT_BOTTOM_RATIO = 0.25f

/** Intensidad del brillo del borde en el material transparente. */
private const val CLEAR_RIM_ALPHA = 0.60f

/** Intensidad del brillo del borde en el material opaco. */
private const val VEIL_RIM_ALPHA = 0.16f

/** Punto del borde donde la luz decae antes de rebotar. */
private const val RIM_SHADE_STOP = 0.55f

/** Cuánta luz queda en el tramo sombreado del borde. */
private const val RIM_SHADE_RATIO = 0.22f

/** Rebote de luz en el canto opuesto al foco. */
private const val RIM_BOUNCE_RATIO = 0.5f

/** Opacidad con la que el color de caneca atraviesa el cristal. */
private const val TINT_ALPHA = 0.16f

/** Luz añadida cuando el material se empaña por la duda. */
private const val HAZE_LIGHT_GAIN = 0.12f

/** Definición que pierde el borde cuando el material se empaña. */
private const val HAZE_RIM_LOSS = 0.55f

/** Extremos del pulso del borde mientras se analiza. */
private const val BREATH_MIN = 0.45f
private const val BREATH_MAX = 1f

/** Periodo del pulso: lento, para que acompañe sin distraer. */
private const val BREATH_PERIOD_MS = 1_100
