package com.recycol.android.ui.classify

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.recycol.android.R
import com.recycol.android.ui.AppDependencies
import com.recycol.android.ui.components.BotaButton
import com.recycol.android.ui.components.BotaButtonStyle
import com.recycol.android.ui.components.BotaGlassState
import com.recycol.android.ui.components.BotaRouteGlyph
import com.recycol.android.ui.components.botaGlass
import com.recycol.android.ui.country.countryDisplayName
import com.recycol.android.ui.theme.BotaMotion
import com.recycol.android.ui.theme.BotaTheme
import com.recycol.domain.model.CaptureHint
import com.recycol.domain.model.ClassificationOutcome
import com.recycol.domain.model.ContaminationState
import com.recycol.domain.model.Disposal
import com.recycol.domain.model.FallbackReason
import com.recycol.domain.model.WasteMaterial
import com.recycol.domain.model.ImageFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

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
    // La sesión de seguimiento tiene el ciclo de vida de la pantalla, y el efecto
    // que la arranca se keya por todo lo que participa: keyar el remember sin
    // keyar el efecto dejaría un estado nuevo sin nadie que llame a start() y la
    // pantalla muerta.
    val tracker = remember(dependencies) { dependencies.trackClassification() }
    val state = remember(tracker) { ClassifyScreenState(tracker, scope) }
    var showManualSheet by remember { mutableStateOf(false) }
    var sheetCandidates by remember { mutableStateOf(emptyList<WasteMaterial>()) }
    var inspectionMaterials by remember { mutableStateOf(emptySet<WasteMaterial>()) }
    DisposableEffect(frames, state) {
        state.start(frames)
        onDispose { state.stop() }
    }
    var countryName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dependencies) {
        val profile = dependencies.selectCountry.activeProfileOrNull()
        inspectionMaterials = profile?.inspectionRules?.map { it.material }?.toSet() ?: emptySet()
        countryName = profile?.isoCode?.let(::countryDisplayName)
    }

    // La orientación se rige por la decisión visible: mientras no haya ninguna
    // hay que decirle al usuario qué hacer, y si lleva mucho rato con la misma
    // en pantalla es que se ha quedado parado y conviene recordárselo.
    // La guía se rige por la identidad de la decisión, no por la caneca: PLASTIC
    // y PAPER comparten caneca blanca y no contaban como cambio.
    val guidanceVisible = rememberGuidanceVisible(
        decisionKey = state.decision?.epoch,
    )

    // Puente temporal mientras la detección automática de contaminación no
    // transfiere a suciedad real (ver SoilQuestionCard): para los materiales que
    // el perfil marca con inspección, se pregunta en vez de adivinar. Se
    // recuerda lo respondido para no repetir la pregunta con cada fotograma.
    // Una pregunta por decisión, no por material. Con la ranura única de antes,
    // los dos materiales con inspección del perfil colombiano —que son además los
    // más confundibles entre sí— se borraban la respuesta mutuamente. El epoch es
    // monótono: volver a ver cartón después de haber respondido sobre otra cosa
    // *es* una decisión nueva. Se exige además que haya caneca: con una papeleta
    // de duda sobre un material de inspección aparecerían a la vez la pregunta de
    // suciedad y el aviso de baja confianza en la misma ranura inferior.
    var soilAnsweredEpoch by remember(tracker) { mutableStateOf(NO_EPOCH) }
    val detectedMaterial = state.outcome?.classification?.material
    val needsSoilAnswer = detectedMaterial != null &&
        detectedMaterial in inspectionMaterials &&
        state.outcome?.disposal != null &&
        // Nunca sobre una decisión provisional: preguntar «¿está sucio?» por algo
        // que la app todavía no ha decidido acababa cambiando la pregunta debajo
        // del usuario mientras la contestaba.
        state.decision?.provisional == false &&
        soilAnsweredEpoch != state.decision?.epoch &&
        state.outcome?.manualSelection != true

    // Qué ocupa la ranura inferior. Sellado y excluyente a propósito: es lo que
    // garantiza que nunca haya dos superficies de cristal a la vez (#161).
    val visible = state.outcome
    val soilMaterial = detectedMaterial?.takeIf { needsSoilAnswer }
    val decided = visible?.disposal
    val bottomSlot: DecisionSlot = when {
        soilMaterial != null -> DecisionSlot.Soil(soilMaterial)
        decided != null -> DecisionSlot.Decided(
            disposal = decided,
            material = detectedMaterial,
            epoch = state.decision?.epoch,
        )

        visible?.needsUserDecision == true -> DecisionSlot.Unsure
        else -> DecisionSlot.Empty
    }

    fun resolveManual(material: WasteMaterial, contamination: ContaminationState) {
        scope.launch {
            val result = dependencies.resolveManualDisposal.resolve(material, contamination)
            state.applyManualOutcome(result)
            onOpenResultDetail(result)
        }
    }

    /**
     * Respuesta a la pregunta de suciedad. A diferencia de la selección manual,
     * **no navega al detalle**: el usuario está sosteniendo el objeto delante de
     * la cámara y lo que quiere es ver la caneca, no leer una ficha.
     */
    fun answerSoil(material: WasteMaterial, contamination: ContaminationState) {
        scope.launch {
            val result = dependencies.resolveManualDisposal.resolve(material, contamination)
            // El epoch se toma de la decisión ya fijada, nunca de un estado que
            // todavía no ha mutado: fijarla lo incrementa, así que leerlo antes
            // garantiza que nunca coincidan y deja este guard como código muerto.
            soilAnsweredEpoch = state.applyManualOutcome(result).epoch
        }
    }

    Box(modifier = modifier.fillMaxSize().background(BotaTheme.colors.cameraBackdrop)) {
        viewfinder(Modifier.fillMaxSize())

        val hasDecision = state.outcome?.disposal != null
        val decidedBin = state.outcome?.disposal?.bin

        // El color entra por aquí: la pantalla se tiñe del color de la caneca
        // decidida. Es el lenguaje visual del producto —blanco, negro, verde—
        // así que además de dar color enseña. El tinte sale del perfil
        // normativo, sigue siendo dato y no diseño.
        BinTintWash(
            color = decidedBin?.colorHex?.let(::binColor),
            modifier = Modifier.fillMaxSize(),
        )

        GuideFrame(
            // Mientras no haya decisión visible, la app sigue mirando: el marco
            // respira. En cuanto decide, se apaga y deja el sitio a la caneca.
            analyzing = !hasDecision,
            dimmed = hasDecision,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(GUIDE_WIDTH_FRACTION)
                .aspectRatio(1f),
        )

        // Todo el cromo superior en una sola columna: la barra manda y lo demás
        // cuelga de ella. Antes eran dos cápsulas sueltas en las esquinas, que
        // no daban ninguna sensación de estructura sobre el vídeo.
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(BotaTheme.spacing.screenMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ClassifyTopBanner(
                countryName = countryName,
                onOpenSettings = onOpenSettings,
            )
            // La entrada manual solo mientras no hay decisión: en cuanto la hay,
            // la acción equivalente vive dentro de la tarjeta («No es esto»), y
            // mantener las dos a la vez era parte de lo que amontonaba.
            AnimatedVisibility(
                visible = !hasDecision,
                enter = fadeIn(tween(BotaMotion.DURATION_FAST_MS)),
                exit = fadeOut(tween(BotaMotion.DURATION_FAST_MS)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OverlayAction(
                        text = stringResource(R.string.manual_entry_action),
                        onClick = {
                            sheetCandidates = emptyList()
                            showManualSheet = true
                        },
                        modifier = Modifier.padding(top = BotaTheme.spacing.sm),
                    )
                }
            }
            HintOverlay(
                // Las indicaciones de captura solo mientras se busca. Con la
                // pregunta en pantalla sobran —lo contesta el usuario, no la
                // cámara— y con una decisión ya tomada son ruido: se quedaba un
                // «apunta hacia adentro del recipiente» encima de la caneca ya
                // resuelta.
                hint = if (needsSoilAnswer || hasDecision) null else state.hints.visible,
                modifier = Modifier.padding(top = BotaTheme.spacing.sm),
            )
        }

        // La orientación se calla cuando hay una indicación de captura: esa es
        // más concreta y no conviene apilar avisos (RF-018).
        ViewfinderGuidance(
            // Nunca por detrás de una decisión ni de la pregunta de suciedad:
            // se colaba difuminada bajo la tarjeta y se leía como un fantasma.
            visible = guidanceVisible &&
                state.hints.visible == null &&
                !hasDecision &&
                !needsSoilAnswer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = BotaTheme.spacing.screenMargin,
                    end = BotaTheme.spacing.screenMargin,
                    bottom = GUIDANCE_BOTTOM_INSET,
                ),
        )

        // Una sola superficie viva en la ranura inferior (#161). Antes eran tres
        // visibilidades independientes en la misma posición: al cambiar de
        // estado, la saliente seguía compuesta mientras la entrante ya estaba
        // dentro, las dos capas de atenuación del cristal se sumaban y quedaba
        // un rectángulo más oscuro con bordes duros y el texto de la tarjeta
        // anterior asomando por debajo. El cristal no estaba mal: se apilaba.
        AnimatedContent(
            targetState = bottomSlot,
            transitionSpec = {
                // La saliente se apaga **antes** de que entre la siguiente. Sin
                // ese retardo las dos coexisten translúcidas y el material se
                // suma consigo mismo, que es justo el artefacto.
                (
                    fadeIn(
                        tween(
                            BotaMotion.DURATION_BASE_MS,
                            delayMillis = BotaMotion.DURATION_FAST_MS,
                        ),
                    ) + slideInVertically(BotaMotion.surfaceSpring()) { it / 2 }
                    ) togetherWith fadeOut(tween(BotaMotion.DURATION_FAST_MS))
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = BotaTheme.spacing.screenMargin,
                    end = BotaTheme.spacing.screenMargin,
                    bottom = BotaTheme.spacing.xxl,
                ),
            label = "decisionSlot",
        ) { slot ->
            when (slot) {
                DecisionSlot.Empty -> Spacer(modifier = Modifier)

                // Mientras la pregunta de suciedad está en pantalla, la decisión
                // se calla: todavía no está decidida, depende de lo que conteste.
                is DecisionSlot.Soil -> SoilQuestionCard(
                    material = slot.material,
                    onAnswer = { contamination -> answerSoil(slot.material, contamination) },
                )

                is DecisionSlot.Decided -> ResultOverlay(
                    disposal = slot.disposal,
                    material = slot.material,
                    decisionEpoch = slot.epoch,
                    onClick = { state.outcome?.let(onOpenResultDetail) },
                    onCorrect = {
                        // Desmentir la decisión arranca por las hipótesis
                        // probables, igual que el camino de baja confianza.
                        sheetCandidates = state.candidates()
                        showManualSheet = true
                    },
                )

                DecisionSlot.Unsure -> LowConfidencePrompt(
                    onChooseManually = {
                        // La desambiguación arranca con las hipótesis probables
                        // del modelo: hoy la mejor (top-1); top-K cuando llegue #126.
                        sheetCandidates = state.candidates()
                        showManualSheet = true
                    },
                )
            }
        }

        // La recompensa: la flor del logo florece sobre lo que se acaba de
        // clasificar. Solo cuando la decisión es firme — ver BloomBurst.
        val disposal = state.outcome?.disposal
        BloomBurst(
            // Una vez por decisión: el epoch avanza también cuando el usuario
            // contesta la pregunta de suciedad, que es el momento que más lo
            // merece, y no avanza porque otro fotograma repita lo mismo.
            trigger = state.decision?.takeIf { it.outcome?.disposal != null }?.epoch,
            celebrate = disposal != null &&
                !needsSoilAnswer &&
                state.outcome?.needsUserDecision != true &&
                !disposal.degradedByContamination &&
                // `fallbackReason` no es nullable: comparar con null era siempre
                // falso y la flor no llegaba a dibujarse nunca.
                disposal.fallbackReason == FallbackReason.NONE,
            modifier = Modifier.align(Alignment.Center),
        )

        if (showManualSheet) {
            ManualSelectionSheet(
                materialsRequiringInspection = inspectionMaterials,
                onSelect = { material, contamination ->
                    showManualSheet = false
                    resolveManual(material, contamination)
                },
                onDismiss = { showManualSheet = false },
                candidates = sheetCandidates,
                onRetry = if (sheetCandidates.isEmpty()) {
                    null
                } else {
                    { showManualSheet = false }
                },
            )
        }
    }
}

/**
 * Qué ocupa la ranura inferior de la pantalla, que es una sola: decisión,
 * pregunta de suciedad, aviso de duda o nada. Sellado para que el compilador
 * garantice la exclusión mutua — cuando eran tres visibilidades sueltas nada
 * impedía que se pintaran a la vez (#161).
 */
private sealed interface DecisionSlot {
    data object Empty : DecisionSlot
    data class Soil(val material: WasteMaterial) : DecisionSlot
    data class Decided(
        val disposal: Disposal,
        val material: WasteMaterial?,
        val epoch: Int?,
    ) : DecisionSlot

    data object Unsure : DecisionSlot
}

/**
 * Aviso de baja confianza (RF-023): la app no adivina; ofrece otra toma o
 * la selección manual. Discreto, sin bloquear la vista en vivo.
 */
@Composable
private fun LowConfidencePrompt(
    onChooseManually: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // El material se empaña: la app no lo tiene claro y el cristal
                // lo dice antes que el texto. Es el flujo protagonista de la
                // app, no un error, así que se ve dudando, no fallando.
                .botaGlass(
                    shape = BotaTheme.shapes.large,
                    state = BotaGlassState.Uncertain,
                )
                .padding(BotaTheme.spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.low_confidence_message),
                style = BotaTheme.typography.subheadline,
                color = BotaTheme.colors.onScrim,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(modifier = Modifier.height(BotaTheme.spacing.sm))
            BotaButton(
                text = stringResource(R.string.low_confidence_action),
                onClick = onChooseManually,
                style = BotaButtonStyle.Tinted,
                compact = true,
            )
        }
    }
}

/**
 * Lavado de color de la caneca decidida, subiendo desde el borde inferior.
 *
 * El cristal es translúcido y neutro por naturaleza, así que el color tiene que
 * venir de otro sitio. Viene de donde ya significa algo: **los colores de
 * caneca**, que son el lenguaje del producto. La pantalla se tiñe de blanco, de
 * negro o de verde según dónde vaya el residuo, y eso enseña además de decorar.
 *
 * Emana del borde inferior, que es donde está la tarjeta de decisión, y muere
 * antes de la mitad de la pantalla: el texto de arriba conserva su fondo y su
 * contraste intactos (RNF-010). El color se comunica siempre junto al nombre de
 * la caneca y su glifo, nunca solo (RNF-010).
 */
@Composable
private fun BinTintWash(color: Color?, modifier: Modifier = Modifier) {
    val target = color ?: BotaTheme.colors.onScrim.copy(alpha = 0f)
    val wash by animateColorAsState(
        targetValue = if (color == null) target else target.copy(alpha = TINT_WASH_ALPHA),
        animationSpec = tween(BotaMotion.DURATION_SLOW_MS, easing = BotaMotion.easeInOut),
        label = "binTintWash",
    )
    val transparent = BotaTheme.colors.onScrim.copy(alpha = 0f)
    Box(
        modifier = modifier.background(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to transparent,
                    TINT_WASH_START to transparent,
                    1f to wash,
                ),
            ),
        ),
    )
}

/**
 * Marco de encuadre: cuatro esquinas, no una caja.
 *
 * Antes era un rectángulo completo que ocupaba media pantalla y competía con
 * todo lo demás. Su trabajo es **orientar la mirada**, no protagonizar, y para
 * eso bastan las esquinas — es además el lenguaje que cualquiera reconoce de
 * un escáner. Ocupa menos, pesa mucho menos y encuadra igual.
 *
 * Se apaga casi del todo cuando ya hay una decisión: en ese momento no hay nada
 * que encuadrar y el ojo tiene que irse a la caneca.
 */
@Composable
private fun GuideFrame(
    analyzing: Boolean,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val presence by animateFloatAsState(
        targetValue = if (dimmed) GUIDE_DIMMED_ALPHA else 1f,
        animationSpec = tween(BotaMotion.DURATION_BASE_MS, easing = BotaMotion.easeInOut),
        label = "guideFramePresence",
    )
    val breath by rememberInfiniteTransition(label = "guideBreath").animateFloat(
        initialValue = if (analyzing) GUIDE_BREATH_MIN else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(GUIDE_BREATH_PERIOD_MS, easing = BotaMotion.easeInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "guideBreathValue",
    )

    val color = BotaTheme.colors.onScrim
    val stroke = GUIDE_STROKE
    val arm = GUIDE_ARM
    val radius = GUIDE_RADIUS

    Canvas(modifier = modifier) {
        val strokePx = stroke.toPx()
        val armPx = arm.toPx()
        val radiusPx = radius.toPx()
        val alpha = presence * (if (analyzing) breath else 1f)

        // Una esquina por cuadrante, dibujada como dos brazos unidos por un
        // cuarto de curva del mismo radio que las tarjetas del sistema.
        val corners = listOf(
            Triple(Offset(0f, 0f), 1f, 1f),
            Triple(Offset(size.width, 0f), -1f, 1f),
            Triple(Offset(0f, size.height), 1f, -1f),
            Triple(Offset(size.width, size.height), -1f, -1f),
        )
        corners.forEach { (origin, dirX, dirY) ->
            val path = Path().apply {
                moveTo(origin.x + dirX * armPx, origin.y)
                lineTo(origin.x + dirX * radiusPx, origin.y)
                quadraticBezierTo(
                    origin.x, origin.y,
                    origin.x, origin.y + dirY * radiusPx,
                )
                lineTo(origin.x, origin.y + dirY * armPx)
            }
            drawPath(
                path = path,
                color = color,
                alpha = alpha,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    }
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
                    .botaGlass(shape = BotaTheme.shapes.capsule)
                    .padding(
                        horizontal = BotaTheme.spacing.lg,
                        vertical = BotaTheme.spacing.sm,
                    ),
            ) {
                Text(
                    text = hintLabel(current),
                    style = BotaTheme.typography.footnoteEmphasized,
                    color = BotaTheme.colors.onScrim,
                    // Región viva: el lector de pantalla anuncia cada indicación
                    // nueva sin que el usuario mueva el foco (RNF-010).
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
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
    disposal: Disposal,
    material: WasteMaterial?,
    decisionEpoch: Int?,
    onClick: () -> Unit,
    onCorrect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) BotaMotion.PRESSED_SCALE else 1f,
            animationSpec = BotaMotion.pressSpring(),
            label = "resultOverlayScale",
        )
        val openDetailLabel = stringResource(R.string.result_open_detail_action)

        // Una decisión nueva se confirma también con el tacto: un golpe seco y
        // corto, el equivalente a que algo encaje en su sitio.
        // Por identidad de decisión, no por caneca: dos materiales distintos que
        // comparten caneca son decisiones distintas, y la misma caneca reafirmada
        // por otro fotograma no lo es.
        val haptics = LocalHapticFeedback.current
        LaunchedEffect(decisionEpoch) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }

        disposal.let { current ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    // El cristal deja pasar el color de la caneca como un
                    // lavado: la decisión se reconoce de un vistazo sin que el
                    // color tape el texto que la explica (RNF-010).
                    .botaGlass(
                        shape = BotaTheme.shapes.large,
                        tint = binColor(current.bin.colorHex),
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = openDetailLabel,
                        onClick = onClick,
                    )
                    .padding(BotaTheme.spacing.xl),
            ) {
                // El material va encima y pequeño, y la caneca debajo y grande:
                // lo que hay que hacer con el residuo importa más que lo que el
                // modelo cree que es. Antes iban al revés y en el mismo cuerpo.
                if (material != null) {
                    Text(
                        text = materialLabel(material),
                        style = BotaTheme.typography.footnoteEmphasized,
                        color = BotaTheme.colors.onScrim.copy(alpha = SECONDARY_ON_SCRIM_ALPHA),
                    )
                    Spacer(modifier = Modifier.height(BotaTheme.spacing.xs))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BinSwatch(colorHex = current.bin.colorHex)
                    Spacer(modifier = Modifier.width(BotaTheme.spacing.md))
                    BotaRouteGlyph(
                        route = current.route,
                        color = BotaTheme.colors.onScrim,
                    )
                    Spacer(modifier = Modifier.width(BotaTheme.spacing.sm))
                    Text(
                        text = current.bin.displayName,
                        style = BotaTheme.typography.title2,
                        color = BotaTheme.colors.onScrim,
                    )
                }
                Spacer(modifier = Modifier.height(BotaTheme.spacing.sm))
                Text(
                    text = current.justification,
                    style = BotaTheme.typography.footnote,
                    color = BotaTheme.colors.onScrim.copy(alpha = TERTIARY_ON_SCRIM_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (current.degradedByContamination) {
                    Spacer(modifier = Modifier.height(BotaTheme.spacing.sm))
                    DegradedNotice(stringResource(R.string.result_degraded_by_contamination))
                }
                current.unavailableBinNotice?.let { notice ->
                    Spacer(modifier = Modifier.height(BotaTheme.spacing.sm))
                    DegradedNotice(notice)
                }

                // La corrección vive aquí, no en una cápsula suelta arriba:
                // cuando hay una decisión en pantalla, la acción que alguien
                // quiere es desmentirla, y tiene que estar donde está mirando.
                Spacer(modifier = Modifier.height(BotaTheme.spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.result_open_detail_hint),
                        style = BotaTheme.typography.caption1,
                        color = BotaTheme.colors.onScrim.copy(alpha = TERTIARY_ON_SCRIM_ALPHA),
                    )
                    // Acción sobre cristal, no un BotaButton: el estilo Plain
                    // pinta el contenido con el verde de marca, que sobre el
                    // velo oscuro se queda sin contraste (RNF-010). Aquí manda
                    // el color de contenido del velo.
                    OnScrimAction(
                        text = stringResource(R.string.result_not_this_action),
                        onClick = onCorrect,
                    )
                }
            }
        }
    }
}

/**
 * Acción textual sobre una superficie de cristal. Existe porque los estilos de
 * `BotaButton` tiñen el contenido con el verde de marca, pensado para fondos
 * claros: sobre el velo oscuro de la cámara ese verde pierde el contraste que
 * exige RNF-010. Aquí el contenido va en el color del velo.
 */
@Composable
private fun OnScrimAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (pressed) BotaMotion.PRESSED_ALPHA else 1f,
        animationSpec = BotaMotion.pressSpring(),
        label = "onScrimActionAlpha",
    )
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = MIN_TOUCH_TARGET)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = BotaTheme.typography.headline,
            color = BotaTheme.colors.onScrim.copy(alpha = alpha),
        )
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

/**
 * Aviso de degradación de la decisión (contaminación o caneca ausente); el
 * texto porta la información, el punto de color solo refuerza (RNF-010).
 */
@Composable
private fun DegradedNotice(text: String, modifier: Modifier = Modifier) {
    val dotColor = BotaTheme.colors.warning
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(BotaTheme.spacing.sm)) {
            drawCircle(color = dotColor)
        }
        Spacer(modifier = Modifier.width(BotaTheme.spacing.xs))
        Text(
            text = text,
            style = BotaTheme.typography.footnoteEmphasized,
            color = BotaTheme.colors.onScrim,
        )
    }
}

/**
 * Control que flota sobre el visor: una cápsula de cristal que se encoge al
 * pulsarla, con la respuesta táctil de iOS en vez del ripple de Material.
 */
@Composable
private fun OverlayAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) BotaMotion.PRESSED_SCALE else 1f,
        animationSpec = BotaMotion.pressSpring(),
        label = "overlayActionScale",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .defaultMinSize(minHeight = MIN_TOUCH_TARGET)
            .botaGlass(shape = BotaTheme.shapes.capsule)
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
        contentAlignment = Alignment.Center,
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

/**
 * Altura reservada bajo la orientación. Ya no tiene que esquivar la tarjeta de
 * decisión —no coinciden nunca—, solo separarse del borde inferior.
 */
private val GUIDANCE_BOTTOM_INSET = 120.dp

/**
 * Epoch imposible: el estabilizador empieza en cero y solo crece, así que este
 * valor significa «el usuario aún no ha contestado a nada en esta sesión».
 */
private const val NO_EPOCH = -1

/**
 * Ancho del marco de encuadre. Bajó de 0,72 a 0,62: encuadra igual y deja
 * respirar la pantalla, que era la queja.
 */
private const val GUIDE_WIDTH_FRACTION = 0.62f

/**
 * Opacidad del lavado de color en el borde inferior. Suficiente para que la
 * pantalla se sienta teñida, lejos de tapar la escena.
 */
private const val TINT_WASH_ALPHA = 0.30f

/** Altura a la que arranca el lavado: por debajo, el contraste no se toca. */
private const val TINT_WASH_START = 0.5f

/** Grosor de las esquinas del marco. */
private val GUIDE_STROKE = 3.dp

/** Longitud de cada brazo de esquina. */
private val GUIDE_ARM = 30.dp

/** Radio de la curva de esquina; el mismo lenguaje que las tarjetas. */
private val GUIDE_RADIUS = 18.dp

/** Presencia del marco cuando ya hay una decisión: casi apagado. */
private const val GUIDE_DIMMED_ALPHA = 0.18f

/** Extremo tenue del pulso del marco mientras se analiza. */
private const val GUIDE_BREATH_MIN = 0.45f

/** Periodo del pulso del marco. */
private const val GUIDE_BREATH_PERIOD_MS = 1_400
private const val GUIDE_ALPHA = 0.45f
private const val SECONDARY_ON_SCRIM_ALPHA = 0.8f
private const val TERTIARY_ON_SCRIM_ALPHA = 0.7f

/** Diámetro de la muestra de color de caneca, como los tamaños fijos de control del DS. */
private val BIN_SWATCH_SIZE = 40.dp

/** Área táctil mínima de los controles sobre el visor (RNF-010). */
private val MIN_TOUCH_TARGET = 44.dp
