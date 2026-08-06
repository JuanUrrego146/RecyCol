package com.botabien.android.inference.tier

import com.botabien.domain.model.DeviceTier
import com.botabien.domain.model.Feature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class BenchmarkedTierPolicyTest {

    private class InMemoryTierStore(private var tier: DeviceTier? = null) : TierStore {
        var writes = 0
        override fun read(): DeviceTier? = tier
        override fun write(tier: DeviceTier) {
            this.tier = tier
            writes++
        }
        override fun clear() {
            tier = null
        }
    }

    @Test
    fun `sin cache arranca en gama baja con todas las funciones costosas apagadas`() {
        val policy = BenchmarkedTierPolicy(InMemoryTierStore(), resolveTier = { DeviceTier.HIGH })

        assertEquals(DeviceTier.LOW, policy.tier)
        Feature.entries.forEach { feature ->
            assertFalse(policy.isEnabled(feature), "la función $feature debería estar apagada")
        }
    }

    @Test
    fun `ensureResolved resuelve una vez y cachea`() = runTest {
        val store = InMemoryTierStore()
        var resolutions = 0
        val policy = BenchmarkedTierPolicy(store, resolveTier = { resolutions++; DeviceTier.HIGH })

        policy.ensureResolved()
        policy.ensureResolved()

        assertEquals(DeviceTier.HIGH, policy.tier)
        assertEquals(1, resolutions, "con caché válida no se vuelve a medir")
        assertEquals(1, store.writes)
    }

    @Test
    fun `con cache previa no se ejecuta el benchmark`() = runTest {
        val policy = BenchmarkedTierPolicy(
            InMemoryTierStore(DeviceTier.MID),
            resolveTier = { error("no debería medirse") },
        )

        assertEquals(DeviceTier.MID, policy.ensureResolved())
        assertEquals(DeviceTier.MID, policy.tier)
    }

    @Test
    fun `la matriz de funciones respeta la gama`() = runTest {
        val policy = BenchmarkedTierPolicy(InMemoryTierStore(DeviceTier.MID), resolveTier = { DeviceTier.MID })
        policy.ensureResolved()

        assertTrue(policy.isEnabled(Feature.CONTINUOUS_CLASSIFICATION))
        assertTrue(policy.isEnabled(Feature.OBJECT_DETECTION))
        assertFalse(
            policy.isEnabled(Feature.AUTOMATIC_CONTAMINATION_INSPECTION),
            "la contaminación automática es solo de gama alta",
        )
    }

    @Test
    fun `latencia degradada de forma sostenida baja un escalon y re-cachea`() = runTest {
        val store = InMemoryTierStore(DeviceTier.HIGH)
        val policy = BenchmarkedTierPolicy(store, resolveTier = { DeviceTier.HIGH }, degradationWindow = 3)
        policy.ensureResolved()

        repeat(3) { policy.reportObservedLatencyMillis(3_000) }

        assertEquals(DeviceTier.MID, policy.tier)
        assertEquals(DeviceTier.MID, store.read())
    }

    @Test
    fun `una latencia mala aislada no degrada la gama`() = runTest {
        val policy = BenchmarkedTierPolicy(
            InMemoryTierStore(DeviceTier.HIGH),
            resolveTier = { DeviceTier.HIGH },
            degradationWindow = 3,
        )
        policy.ensureResolved()

        policy.reportObservedLatencyMillis(3_000)
        policy.reportObservedLatencyMillis(400)
        policy.reportObservedLatencyMillis(3_000)
        policy.reportObservedLatencyMillis(3_000)

        assertEquals(DeviceTier.HIGH, policy.tier, "la ventana contiene una muestra buena")
    }

    @Test
    fun `la gama baja no baja mas aunque la latencia sea mala`() = runTest {
        val policy = BenchmarkedTierPolicy(
            InMemoryTierStore(DeviceTier.LOW),
            resolveTier = { DeviceTier.LOW },
            degradationWindow = 2,
        )
        policy.ensureResolved()

        repeat(4) { policy.reportObservedLatencyMillis(10_000) }

        assertEquals(DeviceTier.LOW, policy.tier)
    }

    @Test
    fun `invalidate borra la cache para volver a medir en el proximo arranque`() = runTest {
        val store = InMemoryTierStore(DeviceTier.MID)
        val policy = BenchmarkedTierPolicy(store, resolveTier = { DeviceTier.MID })

        policy.invalidate()

        assertNull(store.read())
    }
}
