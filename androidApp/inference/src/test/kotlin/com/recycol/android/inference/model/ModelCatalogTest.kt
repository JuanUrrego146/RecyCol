package com.recycol.android.inference.model

import com.recycol.domain.model.DeviceTier
import com.recycol.domain.model.WasteMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelCatalogTest {

    @Test
    fun `gama alta y media comparten el modelo ganador contra control`() {
        assertEquals("material_low.tflite", ModelCatalog.materialSpecFor(DeviceTier.LOW).assetFileName)
        assertEquals("material_mid.tflite", ModelCatalog.materialSpecFor(DeviceTier.MID).assetFileName)
        // Reasignado (ml/REPORTE_METRICAS.md): mid domina a high en ruta contra
        // control y en tamaño, así que high también lo usa. MATERIAL_HIGH se
        // conserva en el catálogo pero ninguna gama lo recibe hoy.
        assertEquals("material_mid.tflite", ModelCatalog.materialSpecFor(DeviceTier.HIGH).assetFileName)
    }

    @Test
    fun `todas las variantes de material cubren la taxonomia completa`() {
        DeviceTier.entries.forEach { tier ->
            val spec = ModelCatalog.materialSpecFor(tier)
            assertEquals(WasteMaterial.entries.size, spec.outputClasses, "clases de $tier")
            assertTrue(spec.inputLayout == InputLayout.CHW, "los artefactos de M4 declaran CHW, no HWC")
            assertEquals(
                0.018649335950613022f,
                spec.normalizedInt8Quantization?.scale,
                "escala de cuantización medida sobre el artefacto real",
            )
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
