package com.recycol.data.profile

import com.recycol.domain.model.CountryProfile

/**
 * Fuente del catálogo de perfiles normativos ya validados.
 *
 * El puerto `ProfileRepository` reparte responsabilidades entre dos agentes
 * (issue #48): la carga y validación del catálogo la implementa RULES (S30) y
 * la persistencia de la selección, DATA (S36). Esta interfaz es la costura:
 * [PersistentProfileRepository] delega aquí el catálogo y solo persiste la
 * selección. RULES registra su implementación en la inyección de dependencias.
 */
fun interface ProfileCatalogSource {

    /** Perfiles disponibles, ya validados contra el esquema del catálogo. */
    suspend fun profiles(): List<CountryProfile>
}
