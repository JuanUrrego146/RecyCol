package com.recycol.domain.port

import com.recycol.domain.model.CountryProfile

/**
 * Puerto de acceso al catálogo de perfiles normativos (CUS-001).
 *
 * La carga y validación del catálogo la implementa el agente RULES (S30) y la
 * persistencia de la selección el agente DATA (S36). Contrato del M0 ampliado
 * por la issue #48.
 */
interface ProfileRepository {

    /** Perfiles disponibles en el catálogo, ya validados contra el esquema. */
    suspend fun availableProfiles(): List<CountryProfile>

    /**
     * Perfil normativo activo, o `null` si el usuario aún no seleccionó país
     * (primer arranque, antes del onboarding).
     */
    suspend fun activeProfileOrNull(): CountryProfile?

    /**
     * Activa el perfil del país indicado y persiste la selección.
     * @param isoCode código ISO 3166-1 alfa-2 en minúsculas, por ejemplo `"co"`.
     * @throws IllegalArgumentException si el catálogo no contiene ese país.
     */
    suspend fun setActiveProfile(isoCode: String)
}
