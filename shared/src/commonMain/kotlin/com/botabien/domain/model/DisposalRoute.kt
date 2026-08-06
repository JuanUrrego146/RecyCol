package com.botabien.domain.model

/**
 * Ruta de disposición final, independiente del color de caneca.
 *
 * El color y el nombre visible de la caneca son específicos de cada país y
 * viven en [BinDefinition]; la ruta es el concepto normativo portable entre
 * países. La métrica de exactitud que manda (RNF-008) se mide sobre la ruta.
 */
enum class DisposalRoute {
    /** Aprovechable: material reciclable limpio y seco. */
    RECYCLABLE,

    /** No aprovechable: residuo ordinario que va a disposición final. */
    NON_RECYCLABLE,

    /** Orgánico aprovechable: compostaje o aprovechamiento biológico. */
    ORGANIC,

    /** Peligroso: requiere manejo especial por riesgo químico o biológico. */
    HAZARDOUS,

    /** Recolección especial o posconsumo: puntos de entrega dedicados. */
    SPECIAL_COLLECTION,
}
