package com.recycol.testing

import com.recycol.domain.model.ImageFrame

/**
 * Frame de prueba sin píxeles: solo metadatos deterministas.
 * Suficiente para ejercitar los puertos del contrato, que jamás leen el búfer
 * en el dominio.
 */
data class StubImageFrame(
    override val width: Int = 640,
    override val height: Int = 480,
    override val timestampMillis: Long = 0L,
) : ImageFrame
