package com.recycol.domain.model

/**
 * Vocabulario cerrado de materiales de residuo.
 *
 * Es el único punto de acoplamiento entre el modelo entrenado y el motor de
 * reglas: la red predice siempre un valor de este enumerado y el RuleEngine lo
 * traduce a caneca según el perfil normativo activo. Añadir un material implica
 * añadirlo aquí, en la taxonomía de `ml/taxonomy/label_mapping.yaml` y en los
 * perfiles que quieran contemplarlo; nunca se añade en un solo lado.
 *
 * El modelo predice materiales, no canecas: ningún consumidor de este enumerado
 * puede decidir un color de caneca por su cuenta.
 */
enum class WasteMaterial {
    /** Plásticos rígidos y flexibles: botellas, envases, bolsas. */
    PLASTIC,

    /** Papel limpio y seco: hojas, periódico, revistas. */
    PAPER,

    /** Cartón corrugado y plegadizo limpio y seco. */
    CARDBOARD,

    /**
     * Cartón para bebidas con recubrimiento interior: vasos de café,
     * envases tipo Tetra Pak. Su destino depende críticamente de si está
     * contaminado, por lo que suele llevar regla de inspección asociada.
     */
    BEVERAGE_CARTON,

    /** Vidrio de envases: botellas y frascos de cualquier color. */
    GLASS,

    /** Metales de envases: latas de aluminio y hojalata. */
    METAL,

    /** Residuos orgánicos aprovechables: restos de comida y de poda. */
    ORGANIC,

    /** Textiles: ropa y trapos. */
    TEXTILE,

    /** Pilas y baterías: corriente de manejo peligroso o posconsumo. */
    BATTERY,

    /** Aparatos eléctricos y electrónicos pequeños. */
    ELECTRONIC,

    /** Residuo ordinario no aprovechable que no encaja en las demás clases. */
    RESIDUAL,
}
