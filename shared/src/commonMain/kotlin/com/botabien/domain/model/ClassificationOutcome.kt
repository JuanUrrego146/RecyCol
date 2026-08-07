package com.botabien.domain.model

/**
 * Resultado de una pasada del caso de uso de clasificación (CUS-003).
 *
 * Exactamente uno de estos estados es cierto:
 * - hay [hints]: la toma no sirve todavía (calidad insuficiente o falta la
 *   vista interior) y [disposal] puede ser preliminar o nulo;
 * - [needsUserDecision] es `true`: la confianza quedó bajo el umbral y la app
 *   no adivina (RF-023): pide otra toma o selección manual;
 * - hay [disposal] definitivo sin indicaciones pendientes.
 *
 * @property classification material y confianza de la predicción, si la hubo;
 *   necesario para el historial y la pantalla de resultado.
 * @property disposal decisión de caneca; `null` mientras no haya una toma útil.
 * @property hints indicaciones de captura pendientes para el usuario.
 * @property needsUserDecision `true` si la app requiere decisión manual del usuario.
 * @property manualSelection `true` si el resultado proviene de una selección
 *   manual del usuario (RF-024/RF-025, coordinación #94) y no del clasificador.
 *   El historial y la pantalla de resultado pueden así distinguir el origen.
 */
data class ClassificationOutcome(
    val classification: ClassificationResult?,
    val disposal: Disposal?,
    val hints: List<CaptureHint>,
    val needsUserDecision: Boolean,
    val manualSelection: Boolean = false,
)
