package com.recycol.domain.usecase

/**
 * Calidad mínima que debe tener un frame para llegar al clasificador (RF-015).
 *
 * Se inyectan porque son **calibración de dispositivo**, no política de
 * dominio: quien mide las métricas es el módulo de cámara de cada plataforma y
 * es él quien sabe con qué escala las produce. Tenerlas duplicadas aquí como
 * constantes ya costó caro — el caso de uso exigía casi el doble de nitidez
 * que el umbral calibrado de la cámara, y como un frame que no pasa la calidad
 * no llega a clasificarse, la app dejaba de reconocer nada.
 *
 * Los valores por omisión son deliberadamente permisivos: ante la duda es
 * mejor clasificar y dejar que decida la confianza (RF-023) que callar.
 */
data class QualityThresholds(
    val sharpness: Float = 0.18f,
    val luminance: Float = 0.16f,
)
