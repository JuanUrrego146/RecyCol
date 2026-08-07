package com.botabien.android.inference.roi

import com.botabien.android.inference.frame.PixelAccessFrame

/**
 * Estrategia de aislamiento del objeto antes de clasificar (RF-010, CUS-003).
 *
 * La elección de estrategia la hace la fábrica consultando la política de
 * gama (invariante 5): detector en gama media y alta, marco guía fijo en
 * gama baja. Ninguna estrategia puede fallar la clasificación: si no se
 * encuentra objeto, se devuelve una región razonable y se sigue.
 */
interface RoiStrategy {

    /** Región del frame que se recorta y se entrega al clasificador. */
    suspend fun findRegion(frame: PixelAccessFrame): CropRegion
}

/**
 * Marco guía fijo: la alternativa sin detector de gama baja (RF-010).
 *
 * La región es un cuadrado centrado con [GUIDE_FRACTION] del lado menor del
 * frame. La pantalla de cámara (agente FRONT) debe dibujar el marco guía con
 * esta misma geometría para que lo que ve el usuario sea lo que se clasifica;
 * la constante es pública precisamente para eso.
 */
class GuideFrameRoi : RoiStrategy {

    override suspend fun findRegion(frame: PixelAccessFrame): CropRegion =
        CropRegion.centeredFraction(frame.width, frame.height, GUIDE_FRACTION)

    companion object {
        /** Fracción del lado menor del frame que ocupa el marco guía. */
        const val GUIDE_FRACTION = 0.80f
    }
}
