package com.recycol.rules.bins

import com.recycol.domain.model.BinDefinition
import com.recycol.domain.model.CountryProfile
import com.recycol.domain.model.DetectedBin
import com.recycol.domain.model.DisposalRoute

/**
 * Resultado del emparejamiento entre los colores detectados por cámara y las
 * canecas del perfil activo (RF-006, RF-007, CUS-002).
 *
 * @property matches canecas del perfil reconocidas, en orden de confianza
 *   descendente y como máximo una por caneca.
 * @property unmatched detecciones descartadas con su motivo, para que la UI
 *   las informe (el texto sale de recursos de cadenas, RNF-011).
 */
data class BinScanResult(
    val matches: List<BinMatch>,
    val unmatched: List<UnmatchedDetection>,
)

/**
 * Emparejamiento de una detección con una caneca del perfil.
 *
 * @property detected detección de cámara que produjo el emparejamiento.
 * @property bin caneca del perfil reconocida.
 * @property colorDistance distancia de color entre ambas, en `[0, 1]`.
 */
data class BinMatch(
    val detected: DetectedBin,
    val bin: BinDefinition,
    val colorDistance: Float,
)

/**
 * Detección descartada y su motivo.
 *
 * @property detected detección de cámara descartada.
 * @property reason motivo del descarte; la UI lo traduce a texto visible.
 */
data class UnmatchedDetection(
    val detected: DetectedBin,
    val reason: UnmatchedReason,
)

/** Motivos de descarte de una detección durante el escaneo. */
enum class UnmatchedReason {
    /** El color no corresponde a ninguna caneca del estándar del perfil activo. */
    COLOR_NOT_IN_PROFILE,

    /** La detección no alcanza la confianza mínima para proponerse. */
    LOW_CONFIDENCE,

    /** La caneca ya fue reconocida por otra detección de mayor confianza. */
    ALREADY_MATCHED,
}

/**
 * Empareja los colores detectados por el [BinDetector][com.recycol.domain.port.BinDetector]
 * con las [BinDefinition] del perfil normativo activo (RF-006).
 *
 * La comparación ocurre en HSV: el matiz domina, así que el emparejamiento
 * tolera los cambios de brillo de la iluminación real. Los colores acromáticos
 * (blanco, gris, negro) se comparan por brillo entre sí y nunca contra colores
 * cromáticos. Todo lo que no pertenezca al estándar del perfil se descarta y
 * se informa (RF-006): el reconocimiento propone, el usuario decide (RF-007).
 *
 * @param chromaticTolerance distancia máxima para aceptar un emparejamiento
 *   entre colores cromáticos.
 * @param achromaticTolerance distancia máxima entre acromáticos; más generosa,
 *   porque el brillo absorbe casi toda la variación de iluminación.
 * @param minConfidence confianza mínima de una detección para considerarla.
 */
class BinColorMatcher(
    private val chromaticTolerance: Float = 0.25f,
    private val achromaticTolerance: Float = 0.35f,
    private val minConfidence: Float = 0.35f,
) {

    /** Empareja [detections] contra las canecas de [profile]. */
    fun match(detections: List<DetectedBin>, profile: CountryProfile): BinScanResult {
        val matches = mutableListOf<BinMatch>()
        val unmatched = mutableListOf<UnmatchedDetection>()
        val matchedBins = mutableSetOf<BinDefinition>()

        // Solo canecas físicas del entorno: un destino de recolección especial
        // (punto posconsumo, punto limpio) no es escaneable ni debe proponerse
        // como caneca disponible (#54).
        val scannableBins = profile.bins.filterNot { it.route == DisposalRoute.SPECIAL_COLLECTION }

        detections.sortedByDescending { it.confidence }.forEach { detected ->
            if (detected.confidence < minConfidence) {
                unmatched += UnmatchedDetection(detected, UnmatchedReason.LOW_CONFIDENCE)
                return@forEach
            }

            val detectedHsv = ColorSpace.fromHex(detected.colorHex)
            val nearest = scannableBins
                .map { bin -> bin to ColorSpace.distance(detectedHsv, ColorSpace.fromHex(bin.colorHex)) }
                .minByOrNull { (_, distance) -> distance }

            val (bin, distance) = nearest ?: return@forEach
            val tolerance = toleranceFor(detectedHsv, ColorSpace.fromHex(bin.colorHex))

            when {
                distance > tolerance ->
                    unmatched += UnmatchedDetection(detected, UnmatchedReason.COLOR_NOT_IN_PROFILE)

                bin in matchedBins ->
                    unmatched += UnmatchedDetection(detected, UnmatchedReason.ALREADY_MATCHED)

                else -> {
                    matches += BinMatch(detected, bin, distance)
                    matchedBins += bin
                }
            }
        }

        return BinScanResult(matches = matches, unmatched = unmatched)
    }

    private fun toleranceFor(a: Hsv, b: Hsv): Float =
        if (ColorSpace.isAchromatic(a) && ColorSpace.isAchromatic(b)) {
            achromaticTolerance
        } else {
            chromaticTolerance
        }
}

/**
 * Detecciones canónicas del resultado del emparejamiento: una por caneca
 * reconocida, con el **color exacto que declara el perfil** en lugar del
 * color crudo observado. Es lo que el detector emite hacia `ScanBinsUseCase`
 * (#49), que empareja por hex exacto: la tolerancia a iluminación ya quedó
 * resuelta aquí.
 */
fun BinScanResult.toCanonicalDetections(): List<DetectedBin> =
    matches.map { match ->
        DetectedBin(colorHex = match.bin.colorHex, confidence = match.detected.confidence)
    }
