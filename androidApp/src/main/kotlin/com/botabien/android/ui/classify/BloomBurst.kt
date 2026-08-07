package com.botabien.android.ui.classify

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.botabien.android.ui.components.BotaLogo
import com.botabien.android.ui.theme.BotaMotion
import com.botabien.android.ui.theme.BotaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * La flor florece cuando la aplicación acierta a clasificar algo.
 *
 * Es el mismo motivo del logo —el brote que sale del bote— aparecido en el
 * momento de recompensa del producto: el residuo, bien clasificado, se
 * convierte en vida. Aquí sale sin el recipiente, porque el recipiente ya es la
 * escena real que hay detrás.
 *
 * **Solo celebra lo que merece celebrarse.** Con baja confianza, con una
 * decisión degradada por contaminación o cuando la caneca ideal no está
 * disponible, no florece: celebrar una duda sería mentir al usuario y, peor,
 * enseñarle a confiar cuando no debe.
 *
 * Dura menos de un segundo y no bloquea nada. Quien clasifica veinte cosas
 * seguidas la ve de reojo; quien clasifica una la disfruta.
 *
 * @param trigger cambia con cada decisión nueva; es lo que dispara la floración.
 *   `null` no dispara nada.
 * @param celebrate `false` deja pasar la decisión sin flor.
 */
@Composable
internal fun BloomBurst(
    trigger: Any?,
    celebrate: Boolean,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val growth = remember { Animatable(0f) }
    val scale = remember { Animatable(INITIAL_SCALE) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(trigger, celebrate) {
        if (trigger == null || !celebrate) {
            alpha.snapTo(0f)
            return@LaunchedEffect
        }
        growth.snapTo(0f)
        scale.snapTo(INITIAL_SCALE)
        alpha.snapTo(0f)

        if (reducedMotion) {
            // Sin movimiento sigue habiendo recompensa: la flor aparece hecha,
            // se sostiene y se va. Lo que se elimina es el gesto, no el premio.
            alpha.animateTo(1f, tween(BotaMotion.DURATION_FAST_MS))
            growth.snapTo(1f)
            scale.snapTo(1f)
            delay(REDUCED_MOTION_HOLD_MS)
            alpha.animateTo(0f, tween(BotaMotion.DURATION_FAST_MS))
            return@LaunchedEffect
        }

        launch { alpha.animateTo(1f, tween(BotaMotion.DURATION_FAST_MS)) }
        // La escala va con muelle y el crecimiento con curva: el tallo sube
        // sin rebotar —una planta no rebota— pero el conjunto entra con la
        // elasticidad que hace que se sienta vivo y no interpolado.
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = BLOOM_DAMPING,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
        growth.animateTo(
            targetValue = 1f,
            animationSpec = tween(BLOOM_GROWTH_MS, easing = BotaMotion.growth),
        )
        delay(BLOOM_HOLD_MS)
        launch {
            scale.animateTo(
                targetValue = EXIT_SCALE,
                animationSpec = tween(BLOOM_FADE_MS, easing = BotaMotion.easeOut),
            )
        }
        alpha.animateTo(0f, tween(BLOOM_FADE_MS, easing = BotaMotion.easeOut))
    }

    if (alpha.value > 0f) {
        BotaLogo(
            modifier = modifier
                .size(BLOOM_SIZE)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                    // Crece desde su base, como algo que sale del suelo.
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                },
            color = BotaTheme.colors.accentOnScrim,
            growth = growth.value,
            showVessel = false,
        )
    }
}

/**
 * Verdadero si el sistema tiene las animaciones desactivadas: el ajuste con el
 * que Android expone «reducir movimiento».
 */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            DEFAULT_ANIMATOR_SCALE,
        ) == 0f
    }
}

/** Tamaño de la floración; visible sin taparlo todo. */
private val BLOOM_SIZE = 132.dp

/** Escala de la que parte, encogida contra su base. */
private const val INITIAL_SCALE = 0.55f

/** Escala a la que se retira, como si se alejara. */
private const val EXIT_SCALE = 1.12f

/** Amortiguación del muelle: elástico pero sin rebote de juguete. */
private const val BLOOM_DAMPING = 0.55f

/** Duración del brote. */
private const val BLOOM_GROWTH_MS = 520

/** Tiempo que la flor se queda abierta antes de irse. */
private const val BLOOM_HOLD_MS = 260L

/** Duración de la retirada. */
private const val BLOOM_FADE_MS = 320

/** Reposo de la flor cuando el movimiento está reducido. */
private const val REDUCED_MOTION_HOLD_MS = 420L

/** Escala de animación del sistema cuando el ajuste no está definido. */
private const val DEFAULT_ANIMATOR_SCALE = 1f
