package com.recycol.android.camera

/**
 * Detector de suciedad persistente del lente (RF-016).
 *
 * Es acumulativo: cada frame actualiza el estado interno y devuelve el
 * veredicto vigente. La implementación real por diferencia entre frames llega
 * en S12; [None] permite componer el analizador de calidad sin ella.
 */
interface LensSoilingDetector {

    /**
     * Procesa el siguiente frame y devuelve `true` si hay evidencia de
     * suciedad fija en el lente.
     */
    fun update(frame: LumaImageFrame): Boolean

    /** Detector nulo: nunca acusa suciedad. */
    object None : LensSoilingDetector {
        override fun update(frame: LumaImageFrame): Boolean = false
    }
}
