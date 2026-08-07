package com.recycol.domain.model

/**
 * Estado de contaminación de un residuo aprovechable.
 *
 * La Resolución 2184 de 2019 exige que lo aprovechable esté «limpio y seco»;
 * este estado es la entrada con la que el motor de reglas decide si degrada
 * la decisión hacia la caneca alternativa declarada en el perfil.
 */
enum class ContaminationState {
    /** El residuo está limpio y seco: conserva su ruta aprovechable. */
    CLEAN,

    /** El residuo presenta contaminación: aplica la caneca alternativa de la regla. */
    CONTAMINATED,

    /** Aún no se ha inspeccionado o la inspección no fue concluyente. */
    UNKNOWN,
}
