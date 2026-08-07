package com.recycol.android.camera

import com.recycol.domain.model.ImageFrame

/**
 * Implementación Android de [ImageFrame]: metadatos del frame más su plano de
 * luminancia (Y de YUV_420_888), que es lo único que necesitan las heurísticas
 * de calidad (nitidez, luminancia, suciedad del lente).
 *
 * Propiedad del búfer: `luma` pertenece a un anillo de búferes reutilizados por
 * [CameraXFrameSource]. El frame es válido solo durante el procesamiento
 * síncrono del colector; no debe retenerse ni copiarse fuera de ese ciclo.
 *
 * Invariante de privacidad (RNF-012): este frame no se persiste, no se
 * serializa y no se registra en logs. `toString` no expone píxeles.
 *
 * @property luma plano Y compactado, un byte por píxel, `width * height` bytes.
 * @property rotationDegrees rotación que hay que aplicar para ver el frame derecho.
 */
class LumaImageFrame(
    override val width: Int,
    override val height: Int,
    override val timestampMillis: Long,
    val luma: ByteArray,
    val rotationDegrees: Int,
) : ImageFrame {

    override fun toString(): String =
        "LumaImageFrame(${width}x$height, t=$timestampMillis, rot=$rotationDegrees)"
}
