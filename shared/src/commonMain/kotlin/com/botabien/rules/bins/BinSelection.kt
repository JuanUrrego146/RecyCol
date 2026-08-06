package com.botabien.rules.bins

import com.botabien.domain.model.BinDefinition
import com.botabien.domain.model.BinId
import com.botabien.domain.model.CountryProfile
import com.botabien.domain.port.BinAvailabilityRepository

/**
 * Selección de canecas del entorno durante la confirmación del escaneo
 * (RF-007, CUS-002): el reconocimiento propone y el usuario decide.
 *
 * Valor inmutable: cada operación devuelve una copia, pensado para exponerse
 * como estado de un `ViewModel`. Las canecas siempre provienen del perfil
 * activo; un identificador ajeno al perfil se ignora en silencio porque la UI
 * solo puede ofrecer lo que [addable] enumera.
 *
 * @property profile perfil normativo activo.
 * @property selected identificadores de las canecas seleccionadas.
 */
data class BinSelection(
    val profile: CountryProfile,
    val selected: Set<BinId>,
) {

    /** Canecas seleccionadas, en el orden de declaración del perfil. */
    val selectedBins: List<BinDefinition>
        get() = profile.bins.filter { it.id in selected }

    /** Canecas del perfil que aún pueden añadirse manualmente. */
    val addable: List<BinDefinition>
        get() = profile.bins.filterNot { it.id in selected }

    /**
     * `false` si no hay ninguna caneca seleccionada — no se reconoció ninguna
     * y el usuario no añadió manualmente—. En ese caso la UI ofrece reintentar
     * el escaneo, añadir a mano u [omitir][allOf]; nunca confirma un conjunto
     * vacío, porque para el repositorio el vacío significa «sin restricción».
     */
    val canConfirm: Boolean
        get() = selected.isNotEmpty()

    /** Añade una caneca del perfil a la selección; un id ajeno al perfil se ignora. */
    fun add(id: BinId): BinSelection =
        if (profile.bins.any { it.id == id }) copy(selected = selected + id) else this

    /** Elimina una caneca de la selección. */
    fun remove(id: BinId): BinSelection = copy(selected = selected - id)

    companion object {

        /** Propuesta inicial a partir de las canecas reconocidas por el escaneo. */
        fun fromScan(scan: BinScanResult, profile: CountryProfile): BinSelection =
            BinSelection(profile, scan.matches.map { it.bin.id }.toSet())

        /** Omitir el escaneo asume todas las canecas del perfil (criterio de S35). */
        fun allOf(profile: CountryProfile): BinSelection =
            BinSelection(profile, profile.bins.map { it.id }.toSet())
    }
}

/**
 * Persiste la selección confirmada en el repositorio de disponibilidad
 * (implementación del agente DATA). Exige una selección no vacía: el conjunto
 * vacío está reservado en el contrato del repositorio para «sin restricción»
 * y confirmarlo sería silenciosamente lo contrario de lo que el usuario ve.
 */
suspend fun BinSelection.persistTo(repository: BinAvailabilityRepository) {
    require(canConfirm) {
        "No se puede confirmar una selección vacía de canecas: reintenta el escaneo, " +
            "añade manualmente u omite el escaneo para asumir todas las del perfil"
    }
    repository.saveAvailableBins(selected)
}
