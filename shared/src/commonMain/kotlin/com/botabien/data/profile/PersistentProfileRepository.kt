package com.botabien.data.profile

import com.botabien.data.storage.KeyValueStore
import com.botabien.domain.model.CountryProfile
import com.botabien.domain.port.ProfileRepository

/**
 * Implementación persistente de `ProfileRepository` (S36; CUS-001, RF-001, RF-003).
 *
 * El catálogo proviene de [ProfileCatalogSource] (agente RULES, S30); aquí solo
 * se persiste la selección de país en [KeyValueStore], de modo que sobreviva al
 * cierre de la aplicación (RNF-014). Si el código guardado deja de existir en
 * el catálogo —por ejemplo, tras retirar un perfil— el perfil activo vuelve a
 * ser `null` y la app regresa al onboarding en lugar de fallar.
 */
class PersistentProfileRepository(
    private val catalogSource: ProfileCatalogSource,
    private val store: KeyValueStore,
) : ProfileRepository {

    override suspend fun availableProfiles(): List<CountryProfile> = catalogSource.profiles()

    override suspend fun activeProfileOrNull(): CountryProfile? {
        val isoCode = store.read(KEY_ACTIVE_COUNTRY_ISO) ?: return null
        return catalogSource.profiles().firstOrNull { it.isoCode == isoCode }
    }

    override suspend fun setActiveProfile(isoCode: String) {
        require(catalogSource.profiles().any { it.isoCode == isoCode }) {
            "El catálogo de perfiles no contiene el país '$isoCode'"
        }
        store.write(KEY_ACTIVE_COUNTRY_ISO, isoCode)
    }

    private companion object {
        /** Clave de la selección de país en el almacén de preferencias. */
        const val KEY_ACTIVE_COUNTRY_ISO = "active_country_iso"
    }
}
