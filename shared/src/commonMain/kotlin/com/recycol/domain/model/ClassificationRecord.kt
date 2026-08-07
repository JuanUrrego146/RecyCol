package com.recycol.domain.model

/**
 * Entrada del historial local de clasificaciones (CUS-009, RF-032).
 *
 * Invariante de privacidad (RNF-012): el historial guarda únicamente el
 * resultado; el frame de cámara jamás se persiste.
 *
 * @property id identificador único de la entrada.
 * @property material material clasificado.
 * @property binId caneca destino decidida por el motor de reglas.
 * @property timestampMillis momento de la clasificación, en milisegundos desde época Unix.
 */
data class ClassificationRecord(
    val id: String,
    val material: WasteMaterial,
    val binId: BinId,
    val timestampMillis: Long,
)
