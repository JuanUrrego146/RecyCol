package com.botabien.android.inference.tier

import com.botabien.domain.model.DeviceTier
import kotlin.test.Test
import kotlin.test.assertEquals

class TierResolverTest {

    private fun capabilities(
        ramMb: Long = 8 * 1024,
        cores: Int = 8,
        api: Int = 34,
        nnapi: Boolean = true,
        gpu: Boolean = true,
    ) = DeviceCapabilities(
        totalRamMb = ramMb,
        cpuCores = cores,
        apiLevel = api,
        nnapiAvailable = nnapi,
        gpuDelegateAvailable = gpu,
    )

    @Test
    fun `hardware amplio sin benchmark resuelve gama alta`() {
        assertEquals(DeviceTier.HIGH, TierResolver.resolve(capabilities(), benchmarkMedianMillis = null))
    }

    @Test
    fun `poca memoria fuerza gama baja aunque el resto acompane`() {
        assertEquals(
            DeviceTier.LOW,
            TierResolver.resolve(capabilities(ramMb = 2 * 1024), benchmarkMedianMillis = null),
        )
    }

    @Test
    fun `api anterior a 27 fuerza gama baja`() {
        assertEquals(
            DeviceTier.LOW,
            TierResolver.resolve(capabilities(api = 26), benchmarkMedianMillis = null),
        )
    }

    @Test
    fun `sin ningun delegado el techo es gama media`() {
        assertEquals(
            DeviceTier.MID,
            TierResolver.resolve(capabilities(nnapi = false, gpu = false), benchmarkMedianMillis = 20L),
        )
    }

    @Test
    fun `el benchmark rapido sube hasta el techo de capacidades`() {
        assertEquals(
            DeviceTier.HIGH,
            TierResolver.resolve(capabilities(), benchmarkMedianMillis = 30L),
        )
    }

    @Test
    fun `el benchmark lento degrada aunque el hardware declare musculo`() {
        assertEquals(
            DeviceTier.LOW,
            TierResolver.resolve(capabilities(), benchmarkMedianMillis = 500L),
        )
        assertEquals(
            DeviceTier.MID,
            TierResolver.resolve(capabilities(), benchmarkMedianMillis = 150L),
        )
    }

    @Test
    fun `memoria ilegible no fuerza gama baja`() {
        // totalRamMb == 0 significa «no se pudo leer»: deciden las demás señales.
        assertEquals(
            DeviceTier.MID,
            TierResolver.resolve(capabilities(ramMb = 0, cores = 6), benchmarkMedianMillis = 100L),
        )
    }
}
