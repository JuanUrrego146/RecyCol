package com.recycol.android.inference

import com.recycol.domain.model.ClassificationResult
import com.recycol.domain.model.ContaminationResult
import com.recycol.domain.model.ContaminationState
import com.recycol.domain.model.DeviceTier
import com.recycol.domain.model.Feature
import com.recycol.domain.model.ImageFrame
import com.recycol.domain.model.WasteMaterial
import com.recycol.domain.port.DeviceTierPolicy
import com.recycol.domain.port.WasteClassifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Pruebas del recambio por gama (coordinación #102): el singleton de Koin no
 * congela la gama; el clasificador concreto sigue a la política.
 */
class TierAwareWasteClassifierTest {

    private class MutablePolicy(var current: DeviceTier) : DeviceTierPolicy {
        override val tier: DeviceTier get() = current
        override fun isEnabled(feature: Feature) = false
    }

    private class CloseableFakeClassifier(val builtFor: DeviceTier) : WasteClassifier, AutoCloseable {
        var closed = false

        override suspend fun classify(frame: ImageFrame) =
            ClassificationResult(WasteMaterial.PLASTIC, 0.9f)

        override suspend fun inspectContamination(frame: ImageFrame) =
            ContaminationResult(ContaminationState.CLEAN, 0.9f)

        override fun close() {
            closed = true
        }
    }

    private val frame = FakePixelFrame.solid(width = 8, height = 8, argb = 0xFF808080.toInt())

    @Test
    fun `una resolucion tardia de gama recambia el clasificador en la siguiente llamada`() = runTest {
        val policy = MutablePolicy(DeviceTier.LOW)
        val built = mutableListOf<CloseableFakeClassifier>()
        val adapter = TierAwareWasteClassifier(policy) { tier ->
            CloseableFakeClassifier(tier).also { built += it }
        }

        adapter.classify(frame)
        // ensureResolved() termina después de la primera inyección/llamada:
        policy.current = DeviceTier.HIGH
        adapter.classify(frame)

        assertEquals(listOf(DeviceTier.LOW, DeviceTier.HIGH), built.map { it.builtFor })
        assertTrue(built[0].closed, "el clasificador de la gama anterior se libera")
    }

    @Test
    fun `con la gama estable no se reconstruye nada`() = runTest {
        val policy = MutablePolicy(DeviceTier.MID)
        var builds = 0
        val adapter = TierAwareWasteClassifier(policy) { tier ->
            builds++
            CloseableFakeClassifier(tier)
        }

        adapter.classify(frame)
        adapter.inspectContamination(frame)
        adapter.classify(frame)

        assertEquals(1, builds)
    }

    @Test
    fun `la degradacion y el ajuste manual recambian en ambos sentidos`() = runTest {
        val policy = MutablePolicy(DeviceTier.HIGH)
        val built = mutableListOf<DeviceTier>()
        val adapter = TierAwareWasteClassifier(policy) { tier ->
            CloseableFakeClassifier(tier).also { built += tier }
        }

        adapter.classify(frame)
        policy.current = DeviceTier.MID // degradación en uso
        adapter.classify(frame)
        policy.current = DeviceTier.HIGH // el usuario vuelve a subir manualmente
        adapter.inspectContamination(frame)

        assertEquals(listOf(DeviceTier.HIGH, DeviceTier.MID, DeviceTier.HIGH), built)
    }

    @Test
    fun `close libera el clasificador activo`() = runTest {
        val policy = MutablePolicy(DeviceTier.LOW)
        val built = mutableListOf<CloseableFakeClassifier>()
        val adapter = TierAwareWasteClassifier(policy) { tier ->
            CloseableFakeClassifier(tier).also { built += it }
        }
        adapter.classify(frame)

        adapter.close()

        assertTrue(built.single().closed)
    }
}
