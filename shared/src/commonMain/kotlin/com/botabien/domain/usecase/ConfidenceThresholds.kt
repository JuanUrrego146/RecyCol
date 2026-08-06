package com.botabien.domain.usecase

/**
 * Umbrales de confianza del flujo de clasificación (RF-023).
 *
 * Valores por defecto conservadores; su calibración fina con datos reales es
 * responsabilidad de S39 (agente QA), que los ajusta inyectando otra instancia
 * sin tocar los casos de uso.
 *
 * @property material confianza mínima para aceptar la predicción de material.
 * @property contamination confianza mínima para dar por concluyente la inspección.
 */
data class ConfidenceThresholds(
    val material: Float = 0.55f,
    val contamination: Float = 0.50f,
)
