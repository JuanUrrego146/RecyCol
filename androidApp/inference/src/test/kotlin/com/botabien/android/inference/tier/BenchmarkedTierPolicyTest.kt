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

    private class InMemoryTierStore(
        private var tier: DeviceTier? = null,
        private var manual: DeviceTier? = null,
    ) : TierStore {
        var writes = 0
        override fun read(): DeviceTier? = tier
        override fun write(tier: DeviceTier) {
            this.tier = tier
            writes++
        }
        override fun clear() {
            tier = null
        }
        override fun readManualOverride(): DeviceTier? = manual
        override fun writeManualOverride(tier: DeviceTier?) {
            manual = tier
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

    @Test
    fun `el ajuste manual manda sobre la gama medida (RF-031)`() = runTest {
        val store = InMemoryTierStore(DeviceTier.HIGH)
        val policy = BenchmarkedTierPolicy(store, resolveTier = { DeviceTier.HIGH })
        policy.ensureResolved()

        policy.setManualOverride(DeviceTier.LOW)

        assertEquals(DeviceTier.LOW, policy.tier)
        assertFalse(policy.isEnabled(Feature.OBJECT_DETECTION))
        assertEquals(DeviceTier.LOW, store.readManualOverride(), "la preferencia persiste")
    }

    @Test
    fun `volver a automatico restaura la gama medida`() = runTest {
        val store = InMemoryTierStore(DeviceTier.MID, manual = DeviceTier.LOW)
        val policy = BenchmarkedTierPolicy(store, resolveTier = { DeviceTier.MID })
        policy.ensureResolved()
        assertEquals(DeviceTier.LOW, policy.tier, "arranca con la preferencia manual persistida")

        policy.setManualOverride(null)

        assertEquals(DeviceTier.MID, policy.tier)
        assertNull(store.readManualOverride())
    }

    @Test
    fun `con ajuste manual no hay degradacion automatica`() = runTest {
        val store = InMemoryTierStore(DeviceTier.HIGH)
        val policy = BenchmarkedTierPolicy(store, resolveTier = { DeviceTier.HIGH }, degradationWindow = 2)
        policy.ensureResolved()
        policy.setManualOverride(DeviceTier.HIGH)

        repeat(6) { policy.reportObservedLatencyMillis(10_000) }

        assertEquals(DeviceTier.HIGH, policy.tier, "la decisión explícita del usuario se respeta")
        assertEquals(DeviceTier.HIGH, store.read())
    }

    @Test
    fun `la matriz completa se respeta en las tres gamas (RF-030)`() = runTest {
        val expectations = mapOf(
            DeviceTier.LOW to emptySet(),
            DeviceTier.MID to setOf(
                Feature.CONTINUOUS_CLASSIFICATION,
                Feature.OBJECT_DETECTION,
                Feature.CONTINUOUS_BIN_SCAN,
                Feature.FULL_FRAME_QUALITY_ANALYSIS,
            ),
            DeviceTier.HIGH to Feature.entries.toSet(),
        )

        expectations.forEach { (tier, enabled) ->
            val policy = BenchmarkedTierPolicy(InMemoryTierStore(tier), resolveTier = { tier })
            policy.ensureResolved()
            Feature.entries.forEach { feature ->
                assertEquals(
                    feature in enabled,
                    policy.isEnabled(feature),
                    "función $feature en gama $tier",
                )
            }
        }
    }
}
