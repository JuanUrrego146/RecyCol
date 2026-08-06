package com.botabien.android.inference.frame

import com.botabien.domain.model.ImageFrame

/**
 * Extensión Android de [ImageFrame] que expone los píxeles del frame.
 *
 * El dominio solo conoce [ImageFrame] (ancho, alto, marca de tiempo); el
 * runtime de inferencia necesita además leer los píxeles. El módulo de cámara
 * (agente CAM) envuelve su frame nativo en una implementación de esta interfaz
 * para que el clasificador pueda consumirlo. Este es el punto de encuentro
 * entre `androidApp/camera/` y `androidApp/inference/`.
 *
 * Invariante de privacidad (RNF-012): las implementaciones viven solo en
 * memoria; no se persisten, no se serializan y no se registran en logs.
 */
interface PixelAccessFrame : ImageFrame {

    /**
     * Devuelve una copia de los píxeles del frame en formato ARGB_8888
     * empaquetado (un `Int` por píxel, orden por filas, tamaño `width * height`).
     */
    fun readArgbPixels(): IntArray
}

/**
 * Exige acceso a píxeles sobre un [ImageFrame] del dominio.
 *
 * @throws IllegalArgumentException si el frame no implementa [PixelAccessFrame];
 *   eso indica un error de integración (el productor del frame no está usando
 *   el wrapper de la plataforma), nunca una condición esperable en ejecución.
 */
internal fun ImageFrame.requirePixelAccess(): PixelAccessFrame =
    this as? PixelAccessFrame
        ?: throw IllegalArgumentException(
            "El frame (${this::class.simpleName}) no implementa PixelAccessFrame: " +
                "el módulo de cámara debe envolver el frame nativo en un PixelAccessFrame."
        )
