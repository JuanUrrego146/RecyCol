package com.botabien.android.inference.model

import com.botabien.domain.model.ContaminationState
import com.botabien.domain.model.WasteMaterial

/**
 * Orden de las clases en los tensores de salida de los modelos.
 *
 * Es la otra mitad del contrato con `ml/`: el índice `i` del tensor de salida
 * corresponde a la posición `i` de estas listas. El pipeline de entrenamiento
 * exporta las clases en este orden exacto (documentado también en
 * `ml/taxonomy/label_mapping.yaml`). Si los tamaños no cuadran en ejecución,
 * el clasificador falla con error explícito en vez de mapear mal en silencio.
 */
object ModelOutputOrder {

    /**
     * Etapa 1 — material: el orden de salida es el orden de declaración del
     * enumerado [WasteMaterial], que es el vocabulario cerrado del proyecto.
     */
    val MATERIALS: List<WasteMaterial> = WasteMaterial.entries.toList()

    /**
     * Etapa 2 — contaminación: clasificador binario. [ContaminationState.UNKNOWN]
     * nunca sale del modelo; lo produce el runtime cuando no puede inspeccionar.
     */
    val CONTAMINATION: List<ContaminationState> =
        listOf(ContaminationState.CLEAN, ContaminationState.CONTAMINATED)
}
