package com.recycol.android.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.recycol.android.R
import com.recycol.android.ui.AppDependencies
import com.recycol.android.ui.classify.materialLabel
import com.recycol.android.ui.components.BotaButton
import com.recycol.android.ui.components.BotaButtonStyle
import com.recycol.android.ui.components.BotaCard
import com.recycol.android.ui.components.BotaRouteGlyph
import com.recycol.android.ui.components.BotaStatusPill
import com.recycol.android.ui.components.BotaStatusTone
import com.recycol.android.ui.theme.BotaTheme
import com.recycol.domain.model.ClassificationOutcome
import com.recycol.domain.model.CountryProfile
import com.recycol.domain.model.DisposalRoute
import com.recycol.domain.model.FallbackReason
import kotlin.math.roundToInt

/**
 * Detalle de la decisión con su justificación normativa (RF-026, RF-027,
 * RF-028 · CUS-007): caneca con su color, material con su confianza, regla
 * aplicada, norma citada del perfil activo y aviso orientativo visible.
 *
 * @param outcome resultado a explicar; el detalle solo presenta datos que ya
 *   decidió el motor de reglas (invariante 2). El origen manual viene en el
 *   propio resultado (`manualSelection`, coordinación #94) y se marca de
 *   forma explícita.
 */
@Composable
fun ResultDetailScreen(
    dependencies: AppDependencies,
    outcome: ClassificationOutcome,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isManualSelection = outcome.manualSelection
    var profile by remember { mutableStateOf<CountryProfile?>(null) }
    LaunchedEffect(dependencies) {
        profile = dependencies.selectCountry.activeProfileOrNull()
    }

    val disposal = outcome.disposal

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
        // Barra de navegación al estilo de iOS: solo la vuelta, alineada al
        // margen y con su área táctil. El título de la pantalla lo pone la
        // propia caneca — un «Detalle» genérico encima solo restaba jerarquía a
        // lo único que el usuario ha venido a leer.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NAV_BAR_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotaButton(
                text = stringResource(R.string.action_back),
                onClick = onBack,
                style = BotaButtonStyle.Plain,
                compact = true,
            )
        }
        Spacer(modifier = Modifier.height(BotaTheme.spacing.lg))

        if (disposal != null) {
            // Cabecera protagonista: el color de la caneca en grande y su
            // nombre como titular de la pantalla.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(HEADER_SWATCH_SIZE)
                        .clip(BotaTheme.shapes.capsule)
                        .background(binColor(disposal.bin.colorHex))
                        .border(
                            width = BotaTheme.spacing.xxs,
                            color = BotaTheme.colors.separator,
                            shape = BotaTheme.shapes.capsule,
                        ),
                )
                Spacer(modifier = Modifier.width(BotaTheme.spacing.lg))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BotaRouteGlyph(
                            route = disposal.route,
                            color = BotaTheme.colors.secondaryLabel,
                        )
                        Spacer(modifier = Modifier.width(BotaTheme.spacing.xs))
                        Text(
                            text = routeLabel(disposal.route),
                            style = BotaTheme.typography.footnoteEmphasized,
                            color = BotaTheme.colors.secondaryLabel,
                        )
                    }
                    Spacer(modifier = Modifier.height(BotaTheme.spacing.xxs))
                    Text(
                        text = disposal.bin.displayName,
                        style = BotaTheme.typography.title1,
                        color = BotaTheme.colors.label,
                    )
                }
            }
            Spacer(modifier = Modifier.height(BotaTheme.spacing.xl))

            Row(horizontalArrangement = Arrangement.spacedBy(BotaTheme.spacing.sm)) {
                if (isManualSelection) {
                    BotaStatusPill(
                        text = stringResource(R.string.result_manual_selection),
                        tone = BotaStatusTone.Accent,
                    )
                }
                if (disposal.degradedByContamination ||
                    disposal.fallbackReason == FallbackReason.CONTAMINATION
                ) {
                    BotaStatusPill(
                        text = stringResource(R.string.result_degraded_by_contamination),
                        tone = BotaStatusTone.Warning,
                    )
                }
                if (disposal.fallbackReason == FallbackReason.UNAVAILABLE_BIN) {
                    BotaStatusPill(
                        text = stringResource(R.string.result_unavailable_bin),
                        tone = BotaStatusTone.Warning,
                    )
                }
            }

            disposal.unavailableBinNotice?.let { notice ->
                Spacer(modifier = Modifier.height(BotaTheme.spacing.md))
                UnavailableBinNotice(text = notice)
            }
            Spacer(modifier = Modifier.height(BotaTheme.spacing.xxl))

            outcome.classification?.let { classification ->
                SectionCard(title = stringResource(R.string.result_section_material)) {
                    Text(
                        text = materialLabel(classification.material),
                        style = BotaTheme.typography.body,
                        color = BotaTheme.colors.label,
                    )
                    Spacer(modifier = Modifier.height(BotaTheme.spacing.sm))
                    ConfidenceBar(fraction = classification.confidence)
                    Spacer(modifier = Modifier.height(BotaTheme.spacing.xs))
                    Text(
                        text = stringResource(
                            R.string.result_confidence_format,
                            (classification.confidence * 100).roundToInt(),
                        ),
                        style = BotaTheme.typography.footnote,
                        color = BotaTheme.colors.secondaryLabel,
                    )
                }
                Spacer(modifier = Modifier.height(BotaTheme.spacing.md))
            }

            SectionCard(title = stringResource(R.string.result_section_rule)) {
                Text(
                    text = disposal.justification,
                    style = BotaTheme.typography.body,
                    color = BotaTheme.colors.label,
                )
            }
            Spacer(modifier = Modifier.height(BotaTheme.spacing.md))

            profile?.let { active ->
                SectionCard(title = stringResource(R.string.result_section_regulation)) {
                    Text(
                        text = active.regulationName,
                        style = BotaTheme.typography.headline,
                        color = BotaTheme.colors.label,
                    )
                    Spacer(modifier = Modifier.height(BotaTheme.spacing.xxs))
                    Text(
                        text = active.regulationReference,
                        style = BotaTheme.typography.footnote,
                        color = BotaTheme.colors.secondaryLabel,
                    )
                }
                Spacer(modifier = Modifier.height(BotaTheme.spacing.md))
            }
        }

        OrientativeNotice()
        Spacer(modifier = Modifier.height(BotaTheme.spacing.xxl))
    }
}

/**
 * Confianza de la clasificación como barra.
 *
 * El porcentaje solo no dice gran cosa: hay que leerlo y compararlo mentalmente
 * con algo. La barra se entiende de un vistazo y el número se queda al lado para
 * quien quiera el dato exacto — el texto sigue portando la información, la barra
 * la refuerza (RNF-010).
 *
 * No traduce el valor a «alta» o «baja»: dónde están esos cortes lo decide
 * `ConfidenceThresholds` en el dominio, y su calibración es trabajo de QA. La
 * interfaz no se inventa umbrales.
 */
@Composable
private fun ConfidenceBar(fraction: Float, modifier: Modifier = Modifier) {
    val safeFraction = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CONFIDENCE_BAR_HEIGHT)
            .clip(BotaTheme.shapes.capsule)
            .background(BotaTheme.colors.fill),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(safeFraction)
                .height(CONFIDENCE_BAR_HEIGHT)
                .clip(BotaTheme.shapes.capsule)
                .background(BotaTheme.colors.accent),
        )
    }
}

/** Tarjeta de sección con título discreto, al estilo de las listas agrupadas. */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            text = title,
            style = BotaTheme.typography.footnote,
            color = BotaTheme.colors.secondaryLabel,
            modifier = Modifier.padding(
                start = BotaTheme.spacing.lg,
                bottom = BotaTheme.spacing.xs,
            ),
        )
        BotaCard(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * Aviso de caneca ideal no disponible (RF-027). El texto llega listo desde el
 * motor de reglas: la plantilla vive en el perfil normativo como dato y el
 * copy fue aprobado por Juan (coordinación #61); la UI solo lo presenta.
 */
@Composable
private fun UnavailableBinNotice(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(BotaTheme.shapes.large)
            .background(BotaTheme.colors.warning.copy(alpha = NOTICE_TINT_ALPHA))
            .padding(BotaTheme.spacing.lg),
    ) {
        Text(
            text = text,
            style = BotaTheme.typography.callout,
            color = BotaTheme.colors.label,
        )
    }
}

/**
 * Aviso de carácter orientativo, siempre visible en el detalle (RF-028).
 * El tinte informativo refuerza; el texto porta la información (RNF-010).
 */
@Composable
private fun OrientativeNotice(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(BotaTheme.shapes.large)
            .background(BotaTheme.colors.info.copy(alpha = NOTICE_TINT_ALPHA))
            .padding(BotaTheme.spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.result_orientative_notice),
            style = BotaTheme.typography.footnote,
            color = BotaTheme.colors.label,
        )
    }
}

/** Traduce el color de caneca del perfil (`#RRGGBB`) a color de UI. */
private fun binColor(colorHex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(colorHex))
}.getOrDefault(Color.Transparent)

/** Texto visible de cada ruta de disposición (RNF-011). */
@Composable
private fun routeLabel(route: DisposalRoute): String = stringResource(
    when (route) {
        DisposalRoute.RECYCLABLE -> R.string.route_recyclable
        DisposalRoute.NON_RECYCLABLE -> R.string.route_non_recyclable
        DisposalRoute.ORGANIC -> R.string.route_organic
        DisposalRoute.HAZARDOUS -> R.string.route_hazardous
        DisposalRoute.SPECIAL_COLLECTION -> R.string.route_special_collection
    }
)

/** Diámetro de la muestra de color en la cabecera del detalle. */
private val HEADER_SWATCH_SIZE = 56.dp

/** Alto de la barra de navegación, el de iOS. */
private val NAV_BAR_HEIGHT = 44.dp

/** Grosor de la barra de confianza. */
private val CONFIDENCE_BAR_HEIGHT = 6.dp

/** Opacidad del tinte del aviso orientativo. */
private const val NOTICE_TINT_ALPHA = 0.10f
