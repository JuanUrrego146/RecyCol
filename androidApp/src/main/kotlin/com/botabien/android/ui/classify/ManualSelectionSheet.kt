package com.botabien.android.ui.classify

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.botabien.android.R
import com.botabien.android.ui.components.BotaBottomSheet
import com.botabien.android.ui.components.BotaButton
import com.botabien.android.ui.components.BotaButtonStyle
import com.botabien.android.ui.theme.BotaTheme
import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.WasteMaterial

/**
 * Hoja de selección manual de material (RF-024, RF-025 · CUS-006).
 *
 * El usuario llega aquí por baja confianza (la app no adivina, RF-023) o por
 * decisión propia. Para materiales con regla de inspección, la hoja pregunta
 * por la contaminación antes de resolver — contrato de
 * `ResolveManualDisposalUseCase` (coordinación #94) — de modo que el motor
 * pueda degradar a la caneca alternativa si corresponde.
 *
 * @param materialsRequiringInspection materiales que el perfil activo somete
 *   a inspección; para ellos se muestra la pregunta de contaminación.
 * @param onSelect material elegido y estado de contaminación declarado por el
 *   usuario ([ContaminationState.UNKNOWN] si no aplica la pregunta).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSelectionSheet(
    materialsRequiringInspection: Set<WasteMaterial>,
    onSelect: (WasteMaterial, ContaminationState) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingInspection by remember { mutableStateOf<WasteMaterial?>(null) }

    BotaBottomSheet(onDismissRequest = onDismiss) {
        val pending = pendingInspection
        if (pending == null) {
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                WasteMaterial.entries.forEach { material ->
                    MaterialRow(
                        material = material,
                        onClick = {
                            if (material in materialsRequiringInspection) {
                                pendingInspection = material
                            } else {
                                onSelect(material, ContaminationState.UNKNOWN)
                            }
                        },
                    )
                }
            }
        } else {
            Text(
                text = materialLabel(pending),
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
                onClick = { onSelect(pending, ContaminationState.CLEAN) },
                modifier = Modifier.fillMaxWidth(),
                style = BotaButtonStyle.Tinted,
            )
            Spacer(modifier = Modifier.height(BotaTheme.spacing.sm))
            BotaButton(
                text = stringResource(R.string.manual_inspection_contaminated),
                onClick = { onSelect(pending, ContaminationState.CONTAMINATED) },
                modifier = Modifier.fillMaxWidth(),
                style = BotaButtonStyle.Tinted,
            )
            Spacer(modifier = Modifier.height(BotaTheme.spacing.sm))
            BotaButton(
                text = stringResource(R.string.action_back),
                onClick = { pendingInspection = null },
                modifier = Modifier.fillMaxWidth(),
                style = BotaButtonStyle.Plain,
            )
        }
    }
}

/** Fila de material: aire generoso, sin adornos, pulsable entera. */
@Composable
private fun MaterialRow(
    material: WasteMaterial,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = materialLabel(material),
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
