package com.recycol.rules.bins

import com.recycol.domain.model.BinDefinition
import com.recycol.domain.model.BinId
import com.recycol.domain.model.CountryProfile
import com.recycol.domain.model.DisposalRoute
import com.recycol.domain.usecase.RecognizedBin

/**
 * Selección de canecas del entorno durante la confirmación del escaneo
 * (RF-007, CUS-002): el reconocimiento propone y el usuario decide.
 *
 * Valor inmutable: cada operación devuelve una copia, pensado para exponerse
 * como estado de un `ViewModel`. Las canecas siempre provienen del perfil
 * activo; un identificador ajeno al perfil se ignora en silencio porque la UI
 * solo puede ofrecer lo que [addable] enumera. Los destinos de recolección
 * especial (punto posconsumo, punto limpio) no son canecas del entorno y
 * quedan fuera de la selección (#54): el motor los recomienda siempre, estén
 * o no registradas las canecas físicas.
 *
 * La persistencia de la selección confirmada no ocurre aquí: la UI pasa
 * [selected] a `ScanBinsUseCase.confirm` (#49), nunca directamente al
 * repositorio (invariante 4 de la arquitectura).
 *
 * @property profile perfil normativo activo.
 * @property selected identificadores de las canecas seleccionadas.
 */
data class BinSelection(
    val profile: CountryProfile,
    val selected: Set<BinId>,
) {

    /** Canecas físicas del perfil: excluye los destinos de recolección especial. */
    private val physicalBins: List<BinDefinition>
        get() = profile.bins.filterNot { it.route == DisposalRoute.SPECIAL_COLLECTION }

    /** Canecas seleccionadas, en el orden de declaración del perfil. */
    val selectedBins: List<BinDefinition>
        get() = physicalBins.filter { it.id in selected }

    /** Canecas del perfil que aún pueden añadirse manualmente. */
    val addable: List<BinDefinition>
        get() = physicalBins.filterNot { it.id in selected }

    /**
     * `false` si no hay ninguna caneca seleccionada — no se reconoció ninguna
     * y el usuario no añadió manualmente—. En ese caso la UI ofrece reintentar
     * el escaneo, añadir a mano u [omitir][allOf]; nunca confirma un conjunto
     * vacío: para el repositorio de disponibilidad el vacío significa «sin
     * restricción», que es lo contrario de lo que el usuario está viendo.
     */
    val canConfirm: Boolean
        get() = selected.isNotEmpty()

    /** Añade una caneca física del perfil; un id ajeno o de recolección especial se ignora. */
    fun add(id: BinId): BinSelection =
        if (physicalBins.any { it.id == id }) copy(selected = selected + id) else this

    /** Elimina una caneca de la selección. */
    fun remove(id: BinId): BinSelection = copy(selected = selected - id)

    companion object {

        /**
         * Propuesta inicial a partir de las canecas reconocidas por
         * `ScanBinsUseCase.scan` (#49); los destinos de recolección especial
         * se filtran por si un detector ajeno los emitiera.
         */
        fun fromRecognized(recognized: List<RecognizedBin>, profile: CountryProfile): BinSelection =
            BinSelection(
                profile,
                recognized.map { it.definition }
                    .filterNot { it.route == DisposalRoute.SPECIAL_COLLECTION }
                    .map { it.id }
                    .toSet(),
            )

        /** Propuesta inicial a partir del resultado del emparejamiento por color. */
        fun fromScan(scan: BinScanResult, profile: CountryProfile): BinSelection =
            BinSelection(
                profile,
                scan.matches.map { it.bin }
                    .filterNot { it.route == DisposalRoute.SPECIAL_COLLECTION }
                    .map { it.id }
                    .toSet(),
            )

        /** Omitir el escaneo asume todas las canecas físicas del perfil (criterio de S35). */
        fun allOf(profile: CountryProfile): BinSelection =
            BinSelection(
                profile,
                profile.bins
                    .filterNot { it.route == DisposalRoute.SPECIAL_COLLECTION }
                    .map { it.id }
                    .toSet(),
            )
    }
}
