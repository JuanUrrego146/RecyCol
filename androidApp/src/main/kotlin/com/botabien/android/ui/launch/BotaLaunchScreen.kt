package com.botabien.android.ui.launch

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.botabien.android.R
import com.botabien.android.ui.components.BotaLogo
import com.botabien.android.ui.theme.BotaMotion
import com.botabien.android.ui.theme.BotaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Envuelve la aplicación con la entrada de marca: el bote se posa, la flor
 * brota de la tierra y el velo se retira dejando ver el contenido.
 *
 * El [content] se compone **desde el primer fotograma**, por debajo del velo:
 * la animación tapa el arranque, no lo alarga. Lo que la app tuviera que
 * inicializar ocurre mientras la flor crece, así que en gama baja el efecto es
 * que el arranque parece más corto, no más largo.
 *
 * Se muestra una sola vez por instancia de la actividad: al rotar la pantalla
 * no vuelve a aparecer.
 */
@Composable
fun BotaLaunchScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var finished by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        content()
        if (!finished) {
            LaunchVeil(onFinished = { finished = true })
        }
    }
}

/**
 * Velo opaco con el logo animado. Consume los toques mientras está visible:
 * sería desconcertante pulsar algo que aún no se ve.
 */
@Composable
private fun LaunchVeil(onFinished: () -> Unit) {
    val reducedMotion = rememberReducedMotion()

    // Con el movimiento reducido las tres animaciones nacen ya terminadas: el
    // logo aparece quieto, se sostiene un instante y desaparece. Se conserva la
    // marca y se elimina el movimiento, que es justo lo que pide el ajuste.
    val settle = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val growth = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val veil = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        if (reducedMotion) {
            delay(REDUCED_MOTION_HOLD_MS)
            veil.animateTo(
                targetValue = 0f,
                animationSpec = tween(BotaMotion.DURATION_FAST_MS, easing = BotaMotion.easeInOut),
            )
        } else {
            launch {
                settle.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        BotaMotion.DURATION_LAUNCH_SETTLE_MS,
                        easing = BotaMotion.easeOut,
                    ),
                )
            }
            growth.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    BotaMotion.DURATION_LAUNCH_GROWTH_MS,
                    easing = BotaMotion.growth,
                ),
            )
            delay(BotaMotion.DURATION_LAUNCH_HOLD_MS.toLong())
            veil.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    BotaMotion.DURATION_LAUNCH_EXIT_MS,
                    easing = BotaMotion.easeInOut,
                ),
            )
        }
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = veil.value }
            .background(BotaTheme.colors.background)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BotaLogo(
                modifier = Modifier
                    .size(LOGO_SIZE)
                    .graphicsLayer {
                        // Al posarse crece hasta su tamaño; al retirarse el velo
                        // se adelanta un punto hacia quien mira, de modo que la
                        // salida se lea como avanzar y no como desvanecerse.
                        val scale = SETTLE_SCALE +
                            (1f - SETTLE_SCALE) * settle.value +
                            (1f - veil.value) * EXIT_SCALE_GAIN
                        scaleX = scale
                        scaleY = scale
                        alpha = settle.value
                    },
                growth = growth.value,
            )
            Spacer(modifier = Modifier.height(BotaTheme.spacing.xl))
            Text(
                text = stringResource(R.string.app_name),
                style = BotaTheme.typography.title3,
                color = BotaTheme.colors.secondaryLabel,
                modifier = Modifier.graphicsLayer {
                    // El nombre entra cuando la flor ya se está abriendo: primero
                    // se entiende el símbolo, después se lee la marca.
                    alpha = ((growth.value - NAME_START) / (1f - NAME_START)).coerceIn(0f, 1f)
                },
            )
        }
    }
}

/**
 * Verdadero si el sistema tiene las animaciones desactivadas. Es el ajuste que
 * Android expone para «reducir movimiento», y lo activan tanto quien lo
 * necesita por accesibilidad como quien busca ahorrar batería en gama baja.
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

/** Tamaño del logo en la entrada; generoso sin llegar a llenar la pantalla. */
private val LOGO_SIZE = 108.dp

/** Escala del logo antes de posarse. */
private const val SETTLE_SCALE = 0.9f

/** Cuánto se adelanta el logo mientras el velo se retira. */
private const val EXIT_SCALE_GAIN = 0.06f

/** Progreso del brote a partir del cual empieza a leerse el nombre. */
private const val NAME_START = 0.5f

/** Escala de animación del sistema cuando el ajuste no está definido. */
private const val DEFAULT_ANIMATOR_SCALE = 1f

/** Reposo del logo estático cuando el movimiento está reducido. */
private const val REDUCED_MOTION_HOLD_MS = 220L
