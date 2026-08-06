package com.botabien.android.camera

import kotlin.random.Random

/**
 * Conjunto de frames sintéticos de prueba para calibrar y verificar las
 * heurísticas de calidad (S11/S12). Deterministas: semilla fija.
 */
object SyntheticFrames {

    const val WIDTH = 320
    const val HEIGHT = 240

    fun frame(luma: ByteArray, width: Int = WIDTH, height: Int = HEIGHT): LumaImageFrame =
        LumaImageFrame(
            width = width,
            height = height,
            timestampMillis = 0L,
            luma = luma,
            rotationDegrees = 0,
        )

    /** Gris uniforme: escena plana sin detalle. */
    fun flat(level: Int, width: Int = WIDTH, height: Int = HEIGHT): ByteArray =
        ByteArray(width * height) { level.toByte() }

    /** Textura de ajedrez de alto contraste: frame nítido con detalle en todas partes. */
    fun checkerboard(
        cell: Int = 4,
        dark: Int = 30,
        bright: Int = 220,
        width: Int = WIDTH,
        height: Int = HEIGHT,
    ): ByteArray {
        val luma = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val isDark = ((x / cell) + (y / cell)) % 2 == 0
                luma[y * width + x] = (if (isDark) dark else bright).toByte()
            }
        }
        return luma
    }

    /** Ruido uniforme con semilla fija: textura fina nítida. */
    fun noise(seed: Int = 7, width: Int = WIDTH, height: Int = HEIGHT): ByteArray {
        val random = Random(seed)
        return ByteArray(width * height) { random.nextInt(256).toByte() }
    }

    /**
     * Desenfoque por caja aplicado [passes] veces: simula la pérdida de bordes
     * de un frame fuera de foco sin depender de ninguna librería de imagen.
     */
    fun boxBlur(
        source: ByteArray,
        radius: Int = 2,
        passes: Int = 3,
        width: Int = WIDTH,
        height: Int = HEIGHT,
    ): ByteArray {
        var current = source.copyOf()
        repeat(passes) {
            val next = ByteArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    var sum = 0
                    var count = 0
                    for (dy in -radius..radius) {
                        val ny = y + dy
                        if (ny < 0 || ny >= height) continue
                        for (dx in -radius..radius) {
                            val nx = x + dx
                            if (nx < 0 || nx >= width) continue
                            sum += current[ny * width + nx].toInt() and 0xFF
                            count++
                        }
                    }
                    next[y * width + x] = (sum / count).toByte()
                }
            }
            current = next
        }
        return current
    }

    /**
     * Objeto texturizado sobre fondo liso. El rectángulo del objeto se centra
     * en ([centerXFraction], [centerYFraction]) del frame, con lado
     * [sizeFraction] del menor de ancho y alto.
     */
    fun texturedObject(
        centerXFraction: Float,
        centerYFraction: Float,
        sizeFraction: Float = 0.3f,
        background: Int = 128,
        width: Int = WIDTH,
        height: Int = HEIGHT,
    ): ByteArray {
        val luma = flat(background, width, height)
        val side = (minOf(width, height) * sizeFraction).toInt()
        val left = ((width * centerXFraction).toInt() - side / 2).coerceIn(0, width - side)
        val top = ((height * centerYFraction).toInt() - side / 2).coerceIn(0, height - side)
        for (y in top until top + side) {
            for (x in left until left + side) {
                val isDark = ((x / 3) + (y / 3)) % 2 == 0
                luma[y * width + x] = (if (isDark) 20 else 235).toByte()
            }
        }
        return luma
    }

    /**
     * Superpone una mancha oscura elíptica y difusa, como la que deja un dedo
     * en el lente: atenúa la luminancia local en lugar de sustituirla.
     */
    fun withSmudge(
        source: ByteArray,
        centerXFraction: Float = 0.35f,
        centerYFraction: Float = 0.4f,
        radiusFraction: Float = 0.12f,
        strength: Float = 0.75f,
        width: Int = WIDTH,
        height: Int = HEIGHT,
    ): ByteArray {
        val result = source.copyOf()
        val cx = width * centerXFraction
        val cy = height * centerYFraction
        val radius = minOf(width, height) * radiusFraction
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = (x - cx) / radius
                val dy = (y - cy) / radius
                val distance = dx * dx + dy * dy
                if (distance < 1f) {
                    val attenuation = 1f - strength * (1f - distance)
                    val original = result[y * width + x].toInt() and 0xFF
                    result[y * width + x] = (original * attenuation).toInt().coerceIn(0, 255).toByte()
                }
            }
        }
        return result
    }

    /**
     * Desplaza el contenido del frame [shiftX], [shiftY] píxeles, rellenando el
     * borde descubierto con [fill]: simula el movimiento de la cámara sobre la
     * escena.
     */
    fun shifted(
        source: ByteArray,
        shiftX: Int,
        shiftY: Int,
        fill: Int = 128,
        width: Int = WIDTH,
        height: Int = HEIGHT,
    ): ByteArray {
        val result = ByteArray(width * height) { fill.toByte() }
        for (y in 0 until height) {
            val sourceY = y - shiftY
            if (sourceY < 0 || sourceY >= height) continue
            for (x in 0 until width) {
                val sourceX = x - shiftX
                if (sourceX < 0 || sourceX >= width) continue
                result[y * width + x] = source[sourceY * width + sourceX]
            }
        }
        return result
    }
}
