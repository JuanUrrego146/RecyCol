package com.recycol.android.inference.diagnostics

import com.recycol.domain.model.WasteMaterial
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Puntuación de una clase en la salida del modelo. */
data class MaterialScore(val material: WasteMaterial, val probability: Float)

/**
 * Lo que el modelo vio y cuánto tardó en una clasificación.
 *
 * @property modelAsset artefacto que corrió, para saber qué variante se cargó.
 * @property scores todas las clases ordenadas de mayor a menor probabilidad:
 *   la ganadora y las alternativas que consideró.
 * @property latencyMillis preprocesado + inferencia de la etapa 1.
 */
data class ClassificationTrace(
    val modelAsset: String,
    val scores: List<MaterialScore>,
    val latencyMillis: Long,
)

/**
 * Canal de diagnóstico de la inferencia, para el sabor «desarrollador».
 *
 * Existe para responder a una pregunta concreta cuando la app falla: ¿el frame
 * no llegó al modelo, el modelo dudó, o acertó el material y falló la caneca?
 * Sin ver las probabilidades de todas las clases esa pregunta no se puede
 * contestar desde fuera — solo se ve que «no reconoce nada».
 *
 * Apagado por defecto y sin coste cuando lo está: el sabor estándar nunca lo
 * enciende y [publish] sale en la primera comparación. Solo números y nombres
 * de clase; jamás píxeles ni frames (RNF-012).
 */
object InferenceDiagnostics {

    @Volatile
    var enabled: Boolean = false

    private val mutableLastTrace = MutableStateFlow<ClassificationTrace?>(null)

    /** Última clasificación observada, o nulo si aún no hubo ninguna. */
    val lastTrace: StateFlow<ClassificationTrace?> = mutableLastTrace.asStateFlow()

    fun publish(modelAsset: String, probabilities: FloatArray, latencyMillis: Long) {
        if (!enabled) return
        val materials = com.recycol.android.inference.model.ModelOutputOrder.MATERIALS
        if (probabilities.size != materials.size) return
        mutableLastTrace.value = ClassificationTrace(
            modelAsset = modelAsset,
            scores = materials
                .mapIndexed { index, material -> MaterialScore(material, probabilities[index]) }
                .sortedByDescending { it.probability },
            latencyMillis = latencyMillis,
        )
    }
}
