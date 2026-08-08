package com.recycol.data.profile

import com.recycol.domain.model.CountryProfile
import com.recycol.rules.profile.ProfileCatalog

/**
 * Adapta el catálogo de perfiles de RULES ([ProfileCatalog]) al puerto que
 * consume DATA ([ProfileCatalogSource]) — la costura de la issue #48.
 *
 * Un perfil inválido o un archivo ausente se descarta (`Result.failure`
 * individual de `ProfileCatalog.load`) en vez de tumbar la carga completa
 * del catálogo: el resto de países disponibles siguen siendo utilizables.
 */
class CatalogProfileSource(
    private val catalog: ProfileCatalog,
) : ProfileCatalogSource {

    override suspend fun profiles(): List<CountryProfile> {
        val descriptors = catalog.descriptors().getOrThrow()
        return descriptors.mapNotNull { descriptor -> catalog.load(descriptor.id).getOrNull() }
    }
}
