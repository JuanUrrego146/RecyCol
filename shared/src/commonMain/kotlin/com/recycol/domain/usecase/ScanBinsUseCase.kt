package com.recycol.domain.usecase

import com.recycol.domain.model.BinDefinition
import com.recycol.domain.model.BinId
import com.recycol.domain.model.CountryProfile
import com.recycol.domain.model.ImageFrame
import com.recycol.domain.port.BinAvailabilityRepository
import com.recycol.domain.port.BinDetector
import com.recycol.domain.port.ProfileRepository

/**
 * Caneca del perfil activo reconocida durante el escaneo del entorno.
 *
 * @property definition caneca del perfil con la que se emparejó la detección.
 * @property confidence confianza de la detección original.
 */
data class RecognizedBin(
    val definition: BinDefinition,
    val confidence: Float,
)

/**
 * Caso de uso de escaneo de canecas disponibles (CUS-002).
 *
 * El detector reporta colores; aquí —y no en el ViewModel— se emparejan con
 * las canecas del perfil activo. El reconocimiento propone y el usuario
 * decide: la selección solo se persiste al confirmar (RF-007).
 */
class ScanBinsUseCase(
    private val detector: BinDetector,
    private val profiles: ProfileRepository,
    private val binAvailability: BinAvailabilityRepository,
) {

    /**
     * Detecta canecas en el frame y las empareja con el perfil activo por
     * color exacto (`#RRGGBB`, sin distinguir mayúsculas). El emparejamiento
     * tolerante a iluminación variable es responsabilidad del detector del
     * agente BINS (S34), que emite el color canónico de la caneca reconocida.
     * Detecciones repetidas de una misma caneca se reducen a la primera.
     */
    suspend fun scan(frame: ImageFrame): List<RecognizedBin> {
        val profile = activeProfile()
        return detector.detectBins(frame)
            .mapNotNull { detection ->
                profile.bins
                    .firstOrNull { it.colorHex.equals(detection.colorHex, ignoreCase = true) }
                    ?.let { RecognizedBin(definition = it, confidence = detection.confidence) }
            }
            .distinctBy { it.definition.id }
    }

    /**
     * Persiste el conjunto confirmado por el usuario (añadidos y eliminados
     * manuales incluidos). Solo admite canecas declaradas en el perfil activo.
     */
    suspend fun confirm(bins: Set<BinId>) {
        val profile = activeProfile()
        require(bins.all { id -> profile.bins.any { it.id == id } }) {
            "La selección incluye canecas que el perfil activo no declara"
        }
        binAvailability.saveAvailableBins(bins)
    }

    private suspend fun activeProfile(): CountryProfile =
        checkNotNull(profiles.activeProfileOrNull()) {
            "No hay perfil normativo activo: el onboarding de selección de país no se completó"
        }
}
