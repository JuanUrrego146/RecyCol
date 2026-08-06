package com.botabien.android.ui.classify

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.botabien.android.R
import com.botabien.android.ui.AppDependencies
import com.botabien.android.ui.theme.BotaMotion
import com.botabien.android.ui.theme.BotaTheme
import com.botabien.domain.model.CaptureHint
import com.botabien.domain.model.ClassificationOutcome
import com.botabien.domain.model.Disposal
import com.botabien.domain.model.WasteMaterial
import kotlinx.coroutines.flow.Flow
import com.botabien.domain.model.ImageFrame

/**
 * Pantalla principal: la vista en vivo con superposiciones (RF-009, RF-013,
 * RF-017 · CUS-003, CUS-004). La cámara real es del agente CAM: esta pantalla
 * recibe el visor como slot y los frames como flujo, de modo que la
 * integración con `CameraFrameSource` (S10) sea solo cableado.
 *
 * Las superposiciones flotan sobre el visor sin desplazar contenido: marco
 * guía centrado, indicación única arriba y decisión abajo.
 */
@Composable
fun ClassifyScreen(
    dependencies: AppDependencies,
    frames: Flow<ImageFrame>,
    viewfinder: @Composable (Modifier) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenResultDetail: (ClassificationOutcome) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val state = remember { ClassifyScreenState(dependencies.classifyWaste, scope) }
    DisposableEffect(frames) {
        state.start(frames)
        onDispose { state.stop() }
    }

    Box(modifier = modifier.fillMaxSize().background(BotaTheme.colors.cameraBackdrop)) {
        viewfinder(Modifier.fillMaxSize())

        GuideFrame(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(GUIDE_WIDTH_FRACTION)
                .aspectRatio(1f),
        )

        OverlayAction(
            text = stringResource(R.string.settings_title),
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(BotaTheme.spacing.screenMargin),
        )

        HintOverlay(
            hint = state.hints.visible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = BotaTheme.spacing.xxxl * 2),
        )

        ResultOverlay(
            disposal = state.outcome?.disposal,
            material = state.outcome?.classification?.material,
            onClick = { state.outcome?.let(onOpenResultDetail) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = BotaTheme.spacing.screenMargin,
                    end = BotaTheme.spacing.screenMargin,
                    bottom = BotaTheme.spacing.xxl,
                ),
        )
    }
}

/** Marco guía de encuadre; mera ayuda visual, nunca bloquea la vista. */
@Composable
private fun GuideFrame(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.border(
            width = BotaTheme.spacing.xxs,
            color = BotaTheme.colors.onScrim.copy(alpha = GUIDE_ALPHA),
            shape = BotaTheme.shapes.large,
        ),
    )
}

/** Indicación de captura única, discreta, arriba y sin desplazar contenido. */
@Composable
private fun HintOverlay(hint: CaptureHint?, modifier: Modifier = Modifier) {
    Crossfade(
        targetState = hint,
        animationSpec = tween(BotaMotion.DURATION_BASE_MS, easing = BotaMotion.easeInOut),
        modifier = modifier,
        label = "hintOverlay",
    ) { current ->
        if (current != null) {
            Box(
                modifier = Modifier
                    .clip(BotaTheme.shapes.capsule)
                    .background(BotaTheme.colors.scrim)
                    .padding(
                        horizontal = BotaTheme.spacing.lg,
                        vertical = BotaTheme.spacing.sm,
                    ),
            ) {
                Text(
                    text = hintLabel(current),
                    style = BotaTheme.typography.footnoteEmphasized,
                    color = BotaTheme.colors.onScrim,
                )
            }
        }
    }
}

/**
 * Tarjeta de decisión: caneca, color y categoría sobre velo translúcido.
 * Pulsarla abre el detalle con la justificación normativa (CUS-007).
 */
@Composable
private fun ResultOverlay(
    disposal: Disposal?,
    material: WasteMaterial?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = disposal != null,
        enter = slideInVertically(BotaMotion.surfaceSpring()) { it / 2 } + fadeIn(),
        exit = slideOutVertically(tween(BotaMotion.DURATION_FAST_MS)) { it / 2 } + fadeOut(),
        modifier = modifier,
    ) {
        var lastDisposal by remember { mutableStateOf(disposal) }
        if (disposal != null) lastDisposal = disposal

        val interactionSource = remember { MutableInteractionSource() }
        lastDisposal?.let { current ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(BotaTheme.shapes.large)
                    .background(BotaTheme.colors.scrim)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .padding(BotaTheme.spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BinSwatch(colorHex = current.bin.colorHex)
                Spacer(modifier = Modifier.width(BotaTheme.spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current.bin.displayName,
                        style = BotaTheme.typography.headline,
                        color = BotaTheme.colors.onScrim,
                    )
                    if (material != null) {
                        Spacer(modifier = Modifier.height(BotaTheme.spacing.xxs))
                        Text(
                            text = materialLabel(material),
                            style = BotaTheme.typography.subheadline,
                            color = BotaTheme.colors.onScrim.copy(alpha = SECONDARY_ON_SCRIM_ALPHA),
                        )
                    }
                    Spacer(modifier = Modifier.height(BotaTheme.spacing.xxs))
                    Text(
                        text = current.justification,
                        style = BotaTheme.typography.footnote,
                        color = BotaTheme.colors.onScrim.copy(alpha = TERTIARY_ON_SCRIM_ALPHA),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (current.degradedByContamination) {
                        Spacer(modifier = Modifier.height(BotaTheme.spacing.xs))
                        DegradedNotice()
                    }
                }
            }
        }
    }
}

/** Muestra el color de la caneca del perfil activo; el color es dato (RNF-004). */
@Composable
private fun BinSwatch(colorHex: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(BIN_SWATCH_SIZE)
            .clip(BotaTheme.shapes.capsule)
            .background(binColor(colorHex))
            .border(
                width = BotaTheme.spacing.xxs,
                color = BotaTheme.colors.onScrim.copy(alpha = GUIDE_ALPHA),
                shape = BotaTheme.shapes.capsule,
            ),
    )
}

/** Aviso de degradación por contaminación; el texto porta la información (RNF-010). */
@Composable
private fun DegradedNotice(modifier: Modifier = Modifier) {
    val dotColor = BotaTheme.colors.warning
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(BotaTheme.spacing.sm)) {
            drawCircle(color = dotColor)
        }
        Spacer(modifier = Modifier.width(BotaTheme.spacing.xs))
        Text(
            text = stringResource(R.string.result_degraded_by_contamination),
            style = BotaTheme.typography.footnoteEmphasized,
            color = BotaTheme.colors.onScrim,
        )
    }
}

/** Botón discreto sobre el visor, construido con los tokens del velo. */
@Composable
private fun OverlayAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(BotaTheme.shapes.capsule)
            .background(BotaTheme.colors.scrim)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(
                horizontal = BotaTheme.spacing.lg,
                vertical = BotaTheme.spacing.sm,
            ),
    ) {
        Text(
            text = text,
            style = BotaTheme.typography.footnoteEmphasized,
            color = BotaTheme.colors.onScrim,
        )
    }
}

/** Traduce el color de caneca del perfil (`#RRGGBB`) a color de UI. */
private fun binColor(colorHex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(colorHex))
}.getOrDefault(Color.Transparent)

/** Texto visible de cada indicación (RNF-011). */
@Composable
private fun hintLabel(hint: CaptureHint): String = stringResource(
    when (hint) {
        CaptureHint.MOVE_CLOSER -> R.string.hint_move_closer
        CaptureHint.MORE_LIGHT -> R.string.hint_more_light
        CaptureHint.CLEAN_LENS -> R.string.hint_clean_lens
        CaptureHint.CENTER_OBJECT -> R.string.hint_center_object
        CaptureHint.POINT_INSIDE -> R.string.hint_point_inside
    }
)

/** Texto visible de cada material (RNF-011). */
@Composable
internal fun materialLabel(material: WasteMaterial): String = stringResource(
    when (material) {
        WasteMaterial.PLASTIC -> R.string.material_plastic
        WasteMaterial.PAPER -> R.string.material_paper
        WasteMaterial.CARDBOARD -> R.string.material_cardboard
        WasteMaterial.BEVERAGE_CARTON -> R.string.material_beverage_carton
        WasteMaterial.GLASS -> R.string.material_glass
        WasteMaterial.METAL -> R.string.material_metal
        WasteMaterial.ORGANIC -> R.string.material_organic
        WasteMaterial.TEXTILE -> R.string.material_textile
        WasteMaterial.BATTERY -> R.string.material_battery
        WasteMaterial.ELECTRONIC -> R.string.material_electronic
        WasteMaterial.RESIDUAL -> R.string.material_residual
    }
)

private const val GUIDE_WIDTH_FRACTION = 0.72f
private const val GUIDE_ALPHA = 0.45f
private const val SECONDARY_ON_SCRIM_ALPHA = 0.8f
private const val TERTIARY_ON_SCRIM_ALPHA = 0.7f

/** Diámetro de la muestra de color de caneca, como los tamaños fijos de control del DS. */
private val BIN_SWATCH_SIZE = 40.dp
