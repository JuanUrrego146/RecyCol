package com.botabien.android.inference.tier

import com.botabien.domain.model.DeviceTier

/**
 * Combina capacidades declaradas y benchmark en una decisión de gama (RF-029).
 *
 * Reglas, en orden:
 * 1. Las capacidades fijan un techo: sin delegado (NNAPI o GPU) o con poca
 *    RAM no se llega a HIGH; API vieja o hardware mínimo fuerzan LOW.
 * 2. El benchmark manda cuando existe: es la única señal medida y puede
 *    subir o bajar la estimación, pero nunca por encima del techo de
 *    capacidades.
 * 3. Sin benchmark (primer arranque sin modelos empaquetados), deciden las
 *    capacidades solas.
 *
 * Umbrales provisionales medidos sobre el modelo de gama baja; su
 * calibración con dispositivos reales es parte del banco de S41 (agente QA).
 */
object TierResolver {

    fun resolve(capabilities: DeviceCapabilities, benchmarkMedianMillis: Long?): DeviceTier {
        val ceiling = capabilityCeiling(capabilities)
        val benchmarked = benchmarkMedianMillis?.let(::tierForLatency) ?: ceiling
        return minOf(benchmarked, ceiling)
    }

    /** Techo según hardware declarado: lo que el dispositivo podría ser. */
    private fun capabilityCeiling(c: DeviceCapabilities): DeviceTier = when {
        c.apiLevel < MIN_API_FOR_MID -> DeviceTier.LOW
        c.totalRamMb in 1 until MIN_RAM_MB_FOR_MID -> DeviceTier.LOW
        c.cpuCores in 1 until MIN_CORES_FOR_MID -> DeviceTier.LOW
        !c.nnapiAvailable && !c.gpuDelegateAvailable -> DeviceTier.MID
        c.totalRamMb >= MIN_RAM_MB_FOR_HIGH && c.cpuCores >= MIN_CORES_FOR_HIGH -> DeviceTier.HIGH
        else -> DeviceTier.MID
    }

    /** Gama que sugiere la latencia real medida sobre el modelo de gama baja. */
    private fun tierForLatency(medianMillis: Long): DeviceTier = when {
        medianMillis <= HIGH_LATENCY_CEILING_MILLIS -> DeviceTier.HIGH
        medianMillis <= MID_LATENCY_CEILING_MILLIS -> DeviceTier.MID
        else -> DeviceTier.LOW
    }

    // Capacidades. RAM en MB; totalRamMb == 0 significa «no se pudo leer» y no fuerza LOW.
    private const val MIN_API_FOR_MID = 27
    private const val MIN_RAM_MB_FOR_MID = 3 * 1024L
    private const val MIN_RAM_MB_FOR_HIGH = 6 * 1024L
    private const val MIN_CORES_FOR_MID = 6
    private const val MIN_CORES_FOR_HIGH = 8

    // Latencia (mediana del calentamiento sobre el modelo de gama baja).
    private const val HIGH_LATENCY_CEILING_MILLIS = 60L
    private const val MID_LATENCY_CEILING_MILLIS = 180L
}
