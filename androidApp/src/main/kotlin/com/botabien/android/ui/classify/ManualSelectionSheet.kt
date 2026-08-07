package com.botabien.android.ui.classify

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.botabien.android.R
import com.botabien.android.ui.components.BotaBottomSheet
import com.botabien.android.ui.components.BotaButton
import com.botabien.android.ui.components.BotaButtonStyle
import com.botabien.android.ui.components.BotaStatusPill
import com.botabien.android.ui.components.BotaStatusTone
import com.botabien.android.ui.theme.BotaTheme
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.WasteMaterial

/**
 * Hoja de selección de material (RF-023, RF-024, RF-025 · CUS-006).
 *
 * Con la brecha del modelo frente a residuos reales (~91 % en foto limpia
 * frente a ~42 % en residuo sucio o aplastado), la baja confianza es un flujo
 * frecuente y esta hoja lo trata como protagonista, no como excepción:
 *
 * 1. **Candidatos primero**: si hay hipótesis probables se ofrecen arriba,
 *    con la lista completa a un toque («Es otra cosa…»). Hoy llega la mejor
 *    hipótesis del modelo; cuando el contrato exponga top-K (coordinación
 *    #126) la hoja las muestra sin cambios.
 * 2. **Reintento amable**: «Intentar otra toma» cierra la hoja y la cámara
 *    sigue viva, con las indicaciones de captura guiando la siguiente toma.
 * 3. **Sin culpar al usuario**: la app habla en primera persona («me cuesta
 *    distinguirlo»); nunca se sugiere que la foto «está mal».
 *
 * Para materiales con regla de inspección pregunta por la contaminación
 * antes de resolver (contrato de `ResolveManualDisposalUseCase`, #94).
 *
 * @param candidates hipótesis probables en orden de confianza; vacía para la
 *   entrada manual deliberada (se abre directo en la lista completa).
 * @param onRetry el usuario prefiere otra toma; `null` oculta la opción.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSelectionSheet(
    materialsRequiringInspection: Set<WasteMaterial>,
    onSelect: (WasteMaterial, ContaminationState) -> Unit,
    onDismiss: () -> Unit,
    candidates: List<WasteMaterial> = emptyList(),
    onRetry: (() -> Unit)? = null,
) {
    var step by remember {
        mutableStateOf(if (candidates.isEmpty()) Step.AllMaterials else Step.Candidates)
    }

    fun choose(material: WasteMaterial) {
        if (material in materialsRequiringInspection) {
            step = Step.Inspection(material)
        } else {
            onSelect(material, ContaminationState.UNKNOWN)
        }
    }

    BotaBottomSheet(onDismissRequest = onDismiss) {
        when (val current = step) {
            Step.Candidates -> CandidatesStep(
                candidates = candidates,
                onChoose = ::choose,
                onShowAll = { step = Step.AllMaterials },
                onRetry = onRetry,
            )

            Step.AllMaterials -> AllMaterialsStep(
                onChoose = ::choose,
                onBackToCandidates = if (candidates.isEmpty()) {
                    null
                } else {
                    { step = Step.Candidates }
                },
            )

            is Step.Inspection -> InspectionStep(
                material = current.material,
                onAnswer = { contamination -> onSelect(current.material, contamination) },
                onBack = {
                    step = if (candidates.isEmpty()) Step.AllMaterials else Step.Candidates
                },
            )
        }
    }
}

/** Pasos internos de la hoja. */
private sealed interface Step {
    data object Candidates : Step
    data object AllMaterials : Step
    data class Inspection(val material: WasteMaterial) : Step
}

/** Paso protagonista: hipótesis probables, lista completa y reintento. */
@Composable
private fun CandidatesStep(
    candidates: List<WasteMaterial>,
    onChoose: (WasteMaterial) -> Unit,
    onShowAll: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    Column {
        Text(
            text = stringResource(R.string.disambiguation_title),
            style = BotaTheme.typography.title3,
            color = BotaTheme.colors.label,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.xs))
        Text(
            text = stringResource(R.string.disambiguation_subtitle),
            style = BotaTheme.typography.footnote,
            color = BotaTheme.colors.secondaryLabel,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.lg))

        candidates.forEach { material ->
            CandidateRow(material = material, onClick = { onChoose(material) })
        }

        Spacer(modifier = Modifier.height(BotaTheme.spacing.sm))
        SheetTextRow(
            text = stringResource(R.string.disambiguation_show_all),
            onClick = onShowAll,
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(BotaTheme.spacing.lg))
            BotaButton(
                text = stringResource(R.string.disambiguation_retry),
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                style = BotaButtonStyle.Plain,
            )
        }
    }
}

/** Lista completa de materiales. */
@Composable
private fun AllMaterialsStep(
    onChoose: (WasteMaterial) -> Unit,
    onBackToCandidates: (() -> Unit)?,
) {
    Column {
        Text(
            text = stringResource(R.string.manual_selection_title),
            style = BotaTheme.typography.title3,
            color = BotaTheme.colors.label,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.xs))
        Text(
            text = stringResource(R.string.manual_selection_subtitle),
            style = BotaTheme.typography.footnote,
            color = BotaTheme.colors.secondaryLabel,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.lg))
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            WasteMaterial.entries.forEach { material ->
                SheetTextRow(
                    text = materialLabel(material),
                    onClick = { onChoose(material) },
                )
            }
            if (onBackToCandidates != null) {
                Spacer(modifier = Modifier.height(BotaTheme.spacing.sm))
                BotaButton(
                    text = stringResource(R.string.action_back),
                    onClick = onBackToCandidates,
                    modifier = Modifier.fillMaxWidth(),
                    style = BotaButtonStyle.Plain,
                )
            }
        }
    }
}

/** Pregunta de contaminación para materiales con regla de inspección. */
@Composable
private fun InspectionStep(
    material: WasteMaterial,
    onAnswer: (ContaminationState) -> Unit,
    onBack: () -> Unit,
) {
    Column {
        Text(
            text = materialLabel(material),
            style = BotaTheme.typography.title3,
            color = BotaTheme.colors.label,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.xs))
        Text(
            text = stringResource(R.string.manual_inspection_question),
            style = BotaTheme.typography.footnote,
            color = BotaTheme.colors.secondaryLabel,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.xl))
        BotaButton(
            text = stringResource(R.string.manual_inspection_clean),
            onClick = { onAnswer(ContaminationState.CLEAN) },
            modifier = Modifier.fillMaxWidth(),
            style = BotaButtonStyle.Tinted,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.sm))
        BotaButton(
            text = stringResource(R.string.manual_inspection_contaminated),
            onClick = { onAnswer(ContaminationState.CONTAMINATED) },
            modifier = Modifier.fillMaxWidth(),
            style = BotaButtonStyle.Tinted,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.sm))
        BotaButton(
            text = stringResource(R.string.action_back),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            style = BotaButtonStyle.Plain,
        )
    }
}

/** Fila de candidato: destacada y con su marca de probabilidad. */
@Composable
private fun CandidateRow(
    material: WasteMaterial,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = BotaTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = materialLabel(material),
            style = BotaTheme.typography.headline,
            color = BotaTheme.colors.label,
            modifier = Modifier.weight(1f),
        )
        BotaStatusPill(
            text = stringResource(R.string.disambiguation_probable),
            tone = BotaStatusTone.Accent,
        )
    }
}

/** Fila de texto pulsable, con aire generoso y sin adornos. */
@Composable
private fun SheetTextRow(
    text: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = text,
        style = BotaTheme.typography.body,
        color = BotaTheme.colors.label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = BotaTheme.spacing.md),
    )
}
