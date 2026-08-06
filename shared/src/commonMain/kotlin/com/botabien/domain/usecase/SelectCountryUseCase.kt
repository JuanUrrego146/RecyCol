package com.botabien.domain.usecase

import com.botabien.domain.model.CountryProfile
import com.botabien.domain.port.ProfileRepository

/**
 * Caso de uso de configuración de país y perfil de clasificación (CUS-001).
 *
 * Respalda el onboarding del primer arranque y el cambio de país desde
 * ajustes (RF-001, RF-003): al seleccionar, el perfil activo se recarga y
 * las clasificaciones siguientes usan la normativa nueva.
 */
class SelectCountryUseCase(
    private val profiles: ProfileRepository,
) {

    /** Países disponibles en el catálogo de perfiles, ya validados. */
    suspend fun availableCountries(): List<CountryProfile> = profiles.availableProfiles()

    /** Perfil activo, o `null` si el onboarding aún no se completó. */
    suspend fun activeProfileOrNull(): CountryProfile? = profiles.activeProfileOrNull()

    /**
     * Activa el perfil del país indicado.
     * @throws IllegalArgumentException si el catálogo no lo contiene.
     */
    suspend fun select(isoCode: String) {
        profiles.setActiveProfile(isoCode)
    }
}
