package com.botabien.android.ui.classify

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.botabien.android.R
import com.botabien.android.ui.components.BotaGlass
import com.botabien.android.ui.components.botaGlass
import com.botabien.android.ui.theme.BotaMotion
import com.botabien.android.ui.theme.BotaTheme
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.WasteMaterial

/**
 * Pregunta de suciedad para fibra: papel y cartón (RF-020 · CUS-005).
 *
 * ## Por qué existe — puente temporal
 *
 * La etapa de contaminación del modelo se entrenó con suciedad sintética y **no
 * transfiere a suciedad real**: marca como limpio el 98,75 % de las fotos
 * reales sucias. Como era el diferenciador del producto, se activó el plan B ya
 * previsto: en vez de detectar la suciedad, **se le pregunta al usuario**.
 *
 * **Esto es un puente, no la solución final.** Cuando la detección automática
 * funcione, esta pregunta desaparece y el flujo vuelve a
 * `ClassifyWasteUseCase.resolveContamination`. Quien lo retome: no hace falta
 * tocar el motor de reglas, solo dejar de mostrar esto.
 *
 * ## Por qué solo fibra
 *
 * Decisión de producto de Juan, y es la correcta: una botella o una lata se
 * enjuagan y se reciclan igual, pero la fibra **absorbe** la grasa y el líquido
 * y se arruina sin remedio. Preguntar por todo sería cansar al usuario con algo
 * que no cambia la respuesta.
 *
 * Qué materiales lo exigen **no se decide aquí**: sale de las reglas de
 * inspección del perfil normativo activo (`CountryProfile.inspectionRules`), que
 * es donde vive todo lo específico de un país. Esta pantalla solo pregunta por
 * lo que el perfil diga.
 *
 * ## Tono
 *
 * No se disculpa ni sugiere que la app falle: pregunta lo que un humano
 * preguntaría, porque **desde fuera no se puede saber** si un vaso lleva café
 * dentro. Dos respuestas, ambas grandes y del mismo peso: ninguna es «la
 * correcta».
 */
@Composable
internal fun SoilQuestionCard(
    material: WasteMaterial,
    onAnswer: (ContaminationState) -> Unit,
    modifier: Modifier = Modifier,
) {
    BotaGlass(
        modifier = modifier.fillMaxWidth(),
        shape = BotaTheme.shapes.large,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(BotaTheme.spacing.xl),
    ) {
        Column {
            Text(
                text = materialLabel(material),
                style = BotaTheme.typography.footnoteEmphasized,
                color = BotaTheme.colors.onScrim.copy(alpha = ANTETITLE_ALPHA),
            )
            Spacer(modifier = Modifier.height(BotaTheme.spacing.xs))
            Text(
                text = stringResource(R.string.manual_inspection_question),
                style = BotaTheme.typography.title3,
                color = BotaTheme.colors.onScrim,
            )
            Spacer(modifier = Modifier.height(BotaTheme.spacing.lg))
            // Altura intrínseca común: una respuesta ocupa dos líneas y la otra
            // una, y sin esto quedaban de tamaños distintos, como si una pesara
            // más que la otra. Ninguna es «la correcta».
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(BotaTheme.spacing.sm),
            ) {
                SoilChoice(
                    text = stringResource(R.string.manual_inspection_clean),
                    onClick = { onAnswer(ContaminationState.CLEAN) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                SoilChoice(
                    text = stringResource(R.string.manual_inspection_contaminated),
                    onClick = { onAnswer(ContaminationState.CONTAMINATED) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

/**
 * Respuesta de la pregunta: cápsula de cristal, grande y fácil de acertar con
 * el pulgar mientras se sostiene el objeto con la otra mano.
 *
 * No usa `BotaButton` porque sus estilos tiñen el contenido con el verde de
 * marca, calculado para fondos claros, y sobre el velo pierde contraste
 * (RNF-010).
 */
@Composable
private fun SoilChoice(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) BotaMotion.PRESSED_SCALE else 1f,
        animationSpec = BotaMotion.pressSpring(),
        label = "soilChoiceScale",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .defaultMinSize(minHeight = CHOICE_MIN_HEIGHT)
            .botaGlass(shape = BotaTheme.shapes.medium)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(
                horizontal = BotaTheme.spacing.md,
                vertical = BotaTheme.spacing.md,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = BotaTheme.typography.headline,
            color = BotaTheme.colors.onScrim,
            textAlign = TextAlign.Center,
        )
    }
}

/** Opacidad del material como antetítulo de la pregunta. */
private const val ANTETITLE_ALPHA = 0.8f

/** Alto mínimo de cada respuesta: cómodo de acertar con una mano. */
private val CHOICE_MIN_HEIGHT = 56.dp
