package com.botabien.android.inference.tier

import com.botabien.domain.model.DeviceTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class PolicyTierPreferenceRepositoryTest {

    private class InMemoryTierStore : TierStore {
        private var tier: DeviceTier? = DeviceTier.HIGH
        private var manual: DeviceTier? = null
        override fun read(): DeviceTier? = tier
        override fun write(tier: DeviceTier) {
            this.tier = tier
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
    fun `escribir la preferencia actualiza la politica en caliente y persiste`() = runTest {
        val store = InMemoryTierStore()
        val policy = BenchmarkedTierPolicy(store, resolveTier = { DeviceTier.HIGH })
        policy.ensureResolved()
        val repository = PolicyTierPreferenceRepository(policy, store)

        repository.setManualOverride(DeviceTier.LOW)

        assertEquals(DeviceTier.LOW, policy.tier, "la gama efectiva cambia sin reiniciar")
        assertEquals(DeviceTier.LOW, repository.manualOverride(), "y queda persistida")
    }

    @Test
    fun `null vuelve a la seleccion automatica`() = runTest {
        val store = InMemoryTierStore()
        val policy = BenchmarkedTierPolicy(store, resolveTier = { DeviceTier.HIGH })
        policy.ensureResolved()
        val repository = PolicyTierPreferenceRepository(policy, store)
        repository.setManualOverride(DeviceTier.LOW)

        repository.setManualOverride(null)

        assertNull(repository.manualOverride())
        assertEquals(DeviceTier.HIGH, policy.tier, "vuelve la gama medida")
    }
}
