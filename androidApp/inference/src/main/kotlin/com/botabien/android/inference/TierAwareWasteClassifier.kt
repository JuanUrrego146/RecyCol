package com.botabien.android.inference

import com.botabien.domain.model.ClassificationResult
import com.botabien.domain.model.ContaminationResult
import com.botabien.domain.model.DeviceTier
import com.botabien.domain.model.ImageFrame
import com.botabien.domain.port.DeviceTierPolicy
import com.botabien.domain.port.WasteClassifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Adaptador que mantiene el clasificador alineado con la gama vigente
 * (coordinación #102; invariante 5: la política se consulta, no se asume).
 *
 * El módulo Koin expone este adaptador como singleton; el clasificador real
 * de cada gama vive detrás y se **reconstruye perezosamente** cuando la gama
 * consultada difiere de la que lo construyó. Eso cubre los tres casos que
 * congelaban la gama:
 * - inyección antes de que `ensureResolved()` termine: la primera llamada
 *   posterior ya ve la gama resuelta y recambia modelo y ROI;
 * - degradación en uso (p. ej. HIGH → MID): la siguiente clasificación baja
 *   de variante sin reiniciar el proceso;
 * - ajuste manual (RF-031): ídem en ambos sentidos.
 *
 * Las llamadas se serializan con un candado: la inferencia ya era serial a
 * nivel de intérprete, y así ningún recambio puede cerrar un motor con una
 * inferencia en vuelo. El clasificador anterior se cierra al recambiar
 * (libera intérpretes y delegados).
 *
 * @param policy política de gama viva (se consulta en cada llamada).
 * @param buildDelegate fábrica del clasificador concreto para una gama.
 */
class TierAwareWasteClassifier(
    private val policy: DeviceTierPolicy,
    private val buildDelegate: (DeviceTier) -> WasteClassifier,
) : WasteClassifier, AutoCloseable {

    private val lock = Mutex()
    private var delegate: WasteClassifier? = null
    private var builtForTier: DeviceTier? = null

    override suspend fun classify(frame: ImageFrame): ClassificationResult =
        lock.withLock { currentDelegate().classify(frame) }

    override suspend fun inspectContamination(frame: ImageFrame): ContaminationResult =
        lock.withLock { currentDelegate().inspectContamination(frame) }

    /** Solo bajo el candado. */
    private fun currentDelegate(): WasteClassifier {
        val tier = policy.tier
        val existing = delegate
        if (existing != null && builtForTier == tier) return existing

        existing?.let { previous ->
            // Nunca dentro de una inferencia: el candado lo garantiza.
            runCatching { (previous as? AutoCloseable)?.close() }
        }
        return buildDelegate(tier).also {
            delegate = it
            builtForTier = tier
        }
    }

    override fun close() {
        (delegate as? AutoCloseable)?.close()
        delegate = null
        builtForTier = null
    }
}
