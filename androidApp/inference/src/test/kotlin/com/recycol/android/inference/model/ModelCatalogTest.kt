package com.recycol.android.inference.model

import com.recycol.domain.model.DeviceTier
import com.recycol.domain.model.WasteMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelCatalogTest {

    @Test
    fun `cada gama tiene su variante de la matriz de gamas`() {
        assertEquals("material_low.tflite", ModelCatalog.materialSpecFor(DeviceTier.LOW).assetFileName)
        assertEquals("material_mid.tflite", ModelCatalog.materialSpecFor(DeviceTier.MID).assetFileName)
        assertEquals("material_high.tflite", ModelCatalog.materialSpecFor(DeviceTier.HIGH).assetFileName)
    }

    @Test
    fun `todas las variantes de material cubren la taxonomia completa`() {
        DeviceTier.entries.forEach { tier ->
            val spec = ModelCatalog.materialSpecFor(tier)
            assertEquals(WasteMaterial.entries.size, spec.outputClasses, "clases de $tier")
            assertTrue(spec.quantizedInput, "las variantes de gama son INT8 con entrada UINT8")
        }
    }

    @Test
    fun `la etapa de contaminacion es binaria`() {
        assertEquals(2, ModelCatalog.CONTAMINATION.outputClasses)
        assertEquals(ModelOutputOrder.CONTAMINATION.size, ModelCatalog.CONTAMINATION.outputClasses)
    }

    @Test
    fun `el orden de materiales es el orden de declaracion del enumerado`() {
        assertEquals(WasteMaterial.entries.toList(), ModelOutputOrder.MATERIALS)
    }
}
