package com.recycol.domain.port

import com.recycol.domain.model.ClassificationResult
import com.recycol.domain.model.ContaminationResult
import com.recycol.domain.model.ImageFrame

/**
 * Puerto del pipeline de inferencia de dos etapas.
 *
 * Lo implementa el agente EDGE en `androidApp/inference/` (LiteRT); lo consume
 * el agente FRONT a través de los casos de uso. Contrato inmutable desde M0:
 * cambiarlo requiere issue de coordinación.
 *
 * Invariantes: corre íntegramente en el dispositivo, sin red (RNF-002) y sin
 * persistir el frame (RNF-012). Devuelve siempre un material, nunca una caneca.
 */
interface WasteClassifier {

    /** Primera etapa: clasifica el material del residuo presente en el frame. */
    suspend fun classify(frame: ImageFrame): ClassificationResult

    /**
     * Segunda etapa: inspecciona contaminación sobre una toma dirigida
     * (por ejemplo, el interior de un vaso). Se invoca solo cuando la regla
     * del material lo pide o cuando la gama del dispositivo lo permite.
     */
    suspend fun inspectContamination(frame: ImageFrame): ContaminationResult
}
