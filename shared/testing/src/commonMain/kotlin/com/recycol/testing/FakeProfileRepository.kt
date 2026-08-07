package com.recycol.testing

import com.recycol.domain.model.CountryProfile
import com.recycol.domain.port.ProfileRepository

/**
 * Fake determinista de `ProfileRepository` (implementación real: agentes
 * RULES/S30 y DATA/S36).
 *
 * Catálogo en memoria con [TestProfiles.threeBins] por defecto y el perfil
 * activo preseleccionado, de modo que el flujo feliz de los consumidores no
 * dependa del onboarding. Para simular el primer arranque, construir con
 * `initiallyActive = null`.
 */
class FakeProfileRepository(
    private val catalog: List<CountryProfile> = listOf(TestProfiles.threeBins),
    initiallyActive: CountryProfile? = catalog.firstOrNull(),
) : ProfileRepository {

    private var active: CountryProfile? = initiallyActive

    override suspend fun availableProfiles(): List<CountryProfile> = catalog

    override suspend fun activeProfileOrNull(): CountryProfile? = active

    override suspend fun setActiveProfile(isoCode: String) {
        active = requireNotNull(catalog.firstOrNull { it.isoCode == isoCode }) {
            "El catálogo de prueba no contiene el país '$isoCode'"
        }
    }
}
