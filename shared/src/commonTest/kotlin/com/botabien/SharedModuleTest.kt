package com.botabien

import kotlin.test.Test
import kotlin.test.assertEquals

/** Prueba de sanidad del cableado de pruebas multiplataforma (S01). */
class SharedModuleTest {

    @Test
    fun elModuloCompartidoExponeSuNombre() {
        assertEquals("shared", SharedModule.NAME)
    }
}
