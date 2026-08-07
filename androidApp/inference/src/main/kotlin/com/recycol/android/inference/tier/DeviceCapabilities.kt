package com.recycol.android.inference.tier

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import org.tensorflow.lite.gpu.CompatibilityList

/**
 * Capacidades declaradas del dispositivo (CUS-008, RF-029).
 *
 * Son la mitad barata de la señal de gama: se leen en milisegundos. La otra
 * mitad, la que no miente, es el micro-benchmark de latencia real
 * ([WarmupBenchmark]); las listas de especificaciones envejecen y la RAM
 * sola engaña (decisión de arquitectura documentada en docs/arquitectura.md).
 */
data class DeviceCapabilities(
    val totalRamMb: Long,
    val cpuCores: Int,
    val apiLevel: Int,
    val nnapiAvailable: Boolean,
    val gpuDelegateAvailable: Boolean,
)

/** Puerto interno de sondeo, doblable en pruebas JVM. */
interface CapabilitiesProbe {
    fun probe(): DeviceCapabilities
}

/**
 * Sondeo real sobre las APIs de Android. Cada lectura es defensiva: un fallo
 * en una señal no impide resolver la gama con las demás.
 */
class AndroidCapabilitiesProbe(private val context: Context) : CapabilitiesProbe {

    override fun probe(): DeviceCapabilities = DeviceCapabilities(
        totalRamMb = totalRamMb(),
        cpuCores = Runtime.getRuntime().availableProcessors(),
        apiLevel = Build.VERSION.SDK_INT,
        nnapiAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1,
        gpuDelegateAvailable = gpuDelegateAvailable(),
    )

    private fun totalRamMb(): Long = try {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        info.totalMem / BYTES_PER_MB
    } catch (_: Exception) {
        0L
    }

    private fun gpuDelegateAvailable(): Boolean = try {
        CompatibilityList().use { it.isDelegateSupportedOnThisDevice }
    } catch (_: Throwable) {
        // Sin la librería nativa cargable no hay delegado GPU: señal en falso.
        false
    }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024L
    }
}
