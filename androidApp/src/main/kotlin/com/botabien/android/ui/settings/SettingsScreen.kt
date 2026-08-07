package com.botabien.android.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.botabien.android.R
import com.botabien.android.ui.AppDependencies
import com.botabien.android.ui.components.BotaBottomSheet
import com.botabien.android.ui.components.BotaButton
import com.botabien.android.ui.components.BotaButtonStyle
import com.botabien.android.ui.components.BotaCard
import com.botabien.android.ui.components.BotaSelectionMark
import com.botabien.android.ui.country.countryDisplayName
import com.botabien.android.ui.theme.BotaTheme
import com.botabien.domain.model.DeviceTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Estado de la pantalla de ajustes (RF-003, RF-031, RF-034). Orquesta los
 * seams del contrato; ninguna política vive aquí: la preferencia manual de
 * rendimiento la interpreta la política de gama del agente EDGE y el borrado
 * del historial lo ejecuta el puerto del contrato.
 */
@Stable
class SettingsState(
    private val dependencies: AppDependencies,
    private val scope: CoroutineScope,
) {

    /** Nombre localizado del país activo; `null` mientras carga. */
    var activeCountry: String? by mutableStateOf(null)
        private set

    /** Nivel manual de rendimiento, o `null` en modo automático. */
    var performanceTier: DeviceTier? by mutableStateOf(null)
        private set

    /** Número de clasificaciones guardadas en el historial local. */
    var historyCount: Int by mutableStateOf(0)
        private set

    /** Carga país activo, preferencia de rendimiento y tamaño del historial. */
    fun load() {
        performanceTier = dependencies.performance.read()
        scope.launch {
            activeCountry = dependencies.selectCountry.activeProfileOrNull()
                ?.let { countryDisplayName(it.isoCode) }
            historyCount = dependencies.history.history().size
        }
    }

    /** Fija el nivel manual de rendimiento; `null` vuelve al automático. */
    fun setPerformance(tier: DeviceTier?) {
        dependencies.performance.write(tier)
        performanceTier = tier
    }

    /** Borra el historial local; solo se invoca tras confirmación explícita. */
    fun clearHistory() {
        scope.launch {
            dependencies.history.clear()
            historyCount = 0
        }
    }
}

/**
 * Pantalla de ajustes (RF-003, RF-031, RF-034 · CUS-001, CUS-008, CUS-009):
 * país activo, nivel de rendimiento con advertencia de su efecto y gestión
 * del historial con confirmación explícita.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    dependencies: AppDependencies,
    onChangeCountry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state = remember { SettingsState(dependencies, scope) }
    LaunchedEffect(state) {
        state.load()
    }
    var showPerformanceSheet by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BotaTheme.colors.groupedBackground)
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = BotaTheme.spacing.screenMargin,
                vertical = BotaTheme.spacing.lg,
            ),
    ) {
        Row {
            BotaButton(
                text = stringResource(R.string.action_back),
                onClick = onBack,
                style = BotaButtonStyle.Plain,
                compact = true,
            )
        }
        Spacer(modifier = Modifier.height(BotaTheme.spacing.sm))
        Text(
            text = stringResource(R.string.settings_title),
            style = BotaTheme.typography.largeTitle,
            color = BotaTheme.colors.label,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.xxl))

        BotaCard(onClick = onChangeCountry, modifier = Modifier.fillMaxWidth()) {
            SettingsRow(
                label = stringResource(R.string.settings_country_label),
                value = state.activeCountry,
            )
        }
        Spacer(modifier = Modifier.height(BotaTheme.spacing.md))

        BotaCard(
            onClick = { showPerformanceSheet = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            SettingsRow(
                label = stringResource(R.string.settings_performance_label),
                value = performanceLabel(state.performanceTier),
            )
        }
        Spacer(modifier = Modifier.height(BotaTheme.spacing.xs))
        Text(
            text = stringResource(R.string.performance_effect_warning),
            style = BotaTheme.typography.footnote,
            color = BotaTheme.colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = BotaTheme.spacing.lg),
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.xxl))

        BotaCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = pluralStringResource(
                    R.plurals.settings_history_count,
                    state.historyCount,
                    state.historyCount,
                ),
                style = BotaTheme.typography.body,
                color = BotaTheme.colors.label,
            )
            Spacer(modifier = Modifier.height(BotaTheme.spacing.md))
            ClearHistoryRow(
                enabled = state.historyCount > 0,
                onClick = { showClearConfirmation = true },
            )
        }
    }

    if (showPerformanceSheet) {
        PerformanceSheet(
            current = state.performanceTier,
            onSelect = { tier ->
                state.setPerformance(tier)
                showPerformanceSheet = false
            },
            onDismiss = { showPerformanceSheet = false },
        )
    }

    if (showClearConfirmation) {
        ClearHistorySheet(
            onConfirm = {
                state.clearHistory()
                showClearConfirmation = false
            },
            onDismiss = { showClearConfirmation = false },
        )
    }
}

/** Fila estándar de ajustes: etiqueta, valor actual y chevron de navegación. */
@Composable
private fun SettingsRow(
    label: String,
    value: String?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = BotaTheme.typography.body,
            color = BotaTheme.colors.label,
            modifier = Modifier.weight(1f),
        )
        value?.let {
            Text(
                text = it,
                style = BotaTheme.typography.body,
                color = BotaTheme.colors.secondaryLabel,
            )
        }
        Spacer(modifier = Modifier.width(BotaTheme.spacing.sm))
        DisclosureChevron(color = BotaTheme.colors.tertiaryLabel)
    }
}

/** Acción de borrado del historial; deshabilitada cuando no hay entradas. */
@Composable
private fun ClearHistoryRow(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = stringResource(R.string.settings_history_clear),
        style = BotaTheme.typography.body,
        color = if (enabled) BotaTheme.colors.error else BotaTheme.colors.tertiaryLabel,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    )
}

/** Selector del nivel de rendimiento (RF-031) con su advertencia de efecto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PerformanceSheet(
    current: DeviceTier?,
    onSelect: (DeviceTier?) -> Unit,
    onDismiss: () -> Unit,
) {
    BotaBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.settings_performance_label),
            style = BotaTheme.typography.title3,
            color = BotaTheme.colors.label,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.xs))
        Text(
            text = stringResource(R.string.performance_effect_warning),
            style = BotaTheme.typography.footnote,
            color = BotaTheme.colors.secondaryLabel,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.lg))
        val selectedDescription = stringResource(R.string.country_selected_description)
        PERFORMANCE_OPTIONS.forEach { option ->
            PerformanceRow(
                label = performanceLabel(option),
                selected = option == current,
                selectedDescription = selectedDescription,
                onClick = { onSelect(option) },
            )
        }
    }
}

/** Fila de opción de rendimiento con marca de selección. */
@Composable
private fun PerformanceRow(
    label: String,
    selected: Boolean,
    selectedDescription: String,
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
            text = label,
            style = BotaTheme.typography.body,
            color = BotaTheme.colors.label,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            BotaSelectionMark(contentDescription = selectedDescription)
        }
    }
}

/** Confirmación explícita del borrado del historial (RF-034). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearHistorySheet(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    BotaBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.settings_history_confirm_title),
            style = BotaTheme.typography.title3,
            color = BotaTheme.colors.label,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.xs))
        Text(
            text = stringResource(R.string.settings_history_confirm_message),
            style = BotaTheme.typography.footnote,
            color = BotaTheme.colors.secondaryLabel,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.xl))
        BotaButton(
            text = stringResource(R.string.settings_history_clear),
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            style = BotaButtonStyle.Tinted,
            destructive = true,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.sm))
        BotaButton(
            text = stringResource(R.string.action_cancel),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            style = BotaButtonStyle.Plain,
        )
    }
}

/** Chevron de navegación al estilo de las celdas de iOS. */
@Composable
private fun DisclosureChevron(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(12.dp)) {
        val stroke = size.minDimension * 0.16f
        drawLine(
            color = color,
            start = Offset(size.width * 0.35f, size.height * 0.12f),
            end = Offset(size.width * 0.75f, size.height * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.75f, size.height * 0.50f),
            end = Offset(size.width * 0.35f, size.height * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** Texto visible de cada nivel de rendimiento (RNF-011). */
@Composable
private fun performanceLabel(tier: DeviceTier?): String = stringResource(
    when (tier) {
        null -> R.string.performance_auto
        DeviceTier.LOW -> R.string.performance_low
        DeviceTier.MID -> R.string.performance_mid
        DeviceTier.HIGH -> R.string.performance_high
    }
)

/** Orden de presentación del selector: automático primero. */
private val PERFORMANCE_OPTIONS: List<DeviceTier?> =
    listOf(null, DeviceTier.LOW, DeviceTier.MID, DeviceTier.HIGH)
