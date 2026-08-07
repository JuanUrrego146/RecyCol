package com.recycol.domain.port

import com.recycol.domain.model.DetectedBin
import com.recycol.domain.model.ImageFrame

/**
 * Puerto de detección de canecas en el entorno (CUS-002).
 *
 * Lo implementa el agente BINS. El detector reporta colores con confianza;
 * el emparejamiento con las canecas del perfil activo ocurre fuera de él.
 * Contrato inmutable desde M0.
 */
interface BinDetector {

    /** Detecta las canecas visibles en el frame, con su color y confianza. */
    suspend fun detectBins(frame: ImageFrame): List<DetectedBin>
}
