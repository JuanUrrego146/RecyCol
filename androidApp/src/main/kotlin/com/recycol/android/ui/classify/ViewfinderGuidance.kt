package com.recycol.android.ui.classify

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.recycol.android.R
import com.recycol.android.ui.components.BotaGlass
import com.recycol.android.ui.theme.BotaMotion
import com.recycol.android.ui.theme.BotaTheme
import kotlinx.coroutines.delay

/**
 * Orientación de arranque del visor: qué se supone que tiene que hacer quien
 * abre la aplicación por primera vez y se encuentra una cámara encendida.
 *
 * Sin esto la pantalla principal es una vista de cámara sin ninguna llamada a
 * la acción, y no hay forma de adivinar que hay que apuntar a un residuo.
 *
 * Aparece sola y se va sola, según [rememberGuidanceVisible]. Nunca convive con
 * una indicación de captura: si el motor ya está diciendo «acércate» o «más
 * luz», esa instrucción es más concreta y manda (RF-018, política de no
 * saturar la pantalla de avisos).
 */
@Composable
internal fun ViewfinderGuidance(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(BotaMotion.DURATION_BASE_MS, easing = BotaMotion.easeOut)) +
            scaleIn(
                animationSpec = tween(BotaMotion.DURATION_BASE_MS, easing = BotaMotion.easeOut),
                initialScale = ENTER_SCALE,
            ),
        exit = fadeOut(tween(BotaMotion.DURATION_FAST_MS)) +
            scaleOut(
                animationSpec = tween(BotaMotion.DURATION_FAST_MS),
                targetScale = ENTER_SCALE,
            ),
        modifier = modifier,
    ) {
        BotaGlass(shape = BotaTheme.shapes.large) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.guidance_title),
                    style = BotaTheme.typography.headline,
                    color = BotaTheme.colors.onScrim,
                    textAlign = TextAlign.Center,
                    // El lector de pantalla la anuncia al aparecer: quien no ve
                    // la cámara es justo quien más necesita saber qué hacer.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(modifier = Modifier.height(BotaTheme.spacing.xs))
                Text(
                    text = stringResource(R.string.guidance_body),
                    style = BotaTheme.typography.footnote,
                    color = BotaTheme.colors.onScrim.copy(alpha = BODY_ALPHA),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Decide cuándo se ve la orientación, con tres reglas que salen de cómo se usa
 * esto de verdad:
 *
 * 1. **Mientras no hay decisión se ve**, que es exactamente el caso de quien
 *    abre la aplicación y el de quien apunta a algo que la app no reconoce.
 * 2. **Se retira cuando la app decide algo**, pero no de golpe: se queda un
 *    momento para que dé tiempo a leerla antes de que la tape la tarjeta.
 *
 * Una versión anterior la hacía reaparecer tras un rato sin cambios aunque
 * hubiera una decisión en pantalla, y el resultado, visto en el dispositivo,
 * era la aplicación diciendo «apunta a un residuo» justo encima de la caneca
 * que acababa de decidir. Orientar a quien ya está orientado es ruido.
 *
 * @param decisionKey identifica la decisión visible; `null` mientras no haya
 *   ninguna. Cambiar de residuo reinicia el ciclo.
 */
@Composable
internal fun rememberGuidanceVisible(decisionKey: Any?): Boolean {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(decisionKey) {
        if (decisionKey == null) {
            visible = true
            return@LaunchedEffect
        }
        delay(READING_GRACE_MS)
        visible = false
    }
    return visible
}

/** Escala de entrada y salida: la orientación crece un punto al aparecer. */
private const val ENTER_SCALE = 0.92f

/** Opacidad del texto secundario sobre el cristal. */
private const val BODY_ALPHA = 0.82f

/** Margen para leer la orientación antes de que la decisión la sustituya. */
private const val READING_GRACE_MS = 2_500L
