package com.botabien.domain.port

import com.botabien.domain.model.FrameQuality
import com.botabien.domain.model.ImageFrame

/**
 * Puerto de análisis de calidad de imagen (CUS-004).
 *
 * Lo implementa el agente CAM con heurísticas deterministas (varianza del
 * Laplaciano, luminancia media, diferencia entre frames), sin ML: el
 * presupuesto de latencia pertenece a la clasificación. Contrato inmutable
 * desde M0.
 */
interface FrameQualityAnalyzer {

    /** Calcula las métricas de calidad del frame. Síncrono y barato por diseño. */
    fun analyze(frame: ImageFrame): FrameQuality
}
