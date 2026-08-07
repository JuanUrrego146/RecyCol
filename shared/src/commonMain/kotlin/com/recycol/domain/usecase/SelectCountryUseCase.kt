package com.recycol.domain.usecase

import com.recycol.domain.model.CountryProfile
import com.recycol.domain.port.BinAvailabilityRepository
import com.recycol.domain.port.ProfileRepository

/**
 * Caso de uso de configuración de país y perfil de clasificación (CUS-001).
 *
 * Respalda el onboarding del primer arranque y el cambio de país desde
 * ajustes (RF-001, RF-003): al seleccionar, el perfil activo se recarga y
 * las clasificaciones siguientes usan la normativa nueva. Cambiar de país
 * reinicia el conjunto de canecas confirmadas (coordinación #65): las canecas
 * de un perfil no significan nada bajo la normativa de otro.
 */
class SelectCountryUseCase(
    private val profiles: ProfileRepository,
    private val binAvailability: BinAvailabilityRepository,
) {

    /** Países disponibles en el catálogo de perfiles, ya validados. */
    suspend fun availableCountries(): List<CountryProfile> = profiles.availableProfiles()

    /** Perfil activo, o `null` si el onboarding aún no se completó. */
    suspend fun activeProfileOrNull(): CountryProfile? = profiles.activeProfileOrNull()

    /**
     * Activa el perfil del país indicado. Si el país cambia respecto al
     * activo, el conjunto de canecas confirmadas vuelve a «sin restricción»
     * y el usuario deberá escanear o confirmar canecas de nuevo (CUS-002).
     * Reseleccionar el mismo país no toca la selección de canecas.
     * @throws IllegalArgumentException si el catálogo no lo contiene.
     */
    suspend fun select(isoCode: String) {
        val previous = profiles.activeProfileOrNull()
        profiles.setActiveProfile(isoCode)
        if (previous != null && previous.isoCode != isoCode) {
            binAvailability.saveAvailableBins(emptySet())
        }
    }
}
