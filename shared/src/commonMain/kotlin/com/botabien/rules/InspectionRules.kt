package com.botabien.rules

import com.botabien.domain.model.CountryProfile
import com.botabien.domain.model.InspectionRule
import com.botabien.domain.model.WasteMaterial

/**
 * Evaluación de las reglas de inspección del perfil activo (RF-019, CUS-005).
 *
 * El perfil declara qué materiales no pueden decidirse sin evaluar
 * contaminación; estos ayudantes son la única lectura de esa declaración.
 * El flujo de captura (CUS-003) los consulta para pedir la vista interior
 * antes de dar la decisión definitiva.
 */

/** Regla de inspección declarada para [material], o `null` si no requiere inspección. */
fun CountryProfile.inspectionRuleFor(material: WasteMaterial): InspectionRule? =
    inspectionRules.firstOrNull { it.material == material }

/** `true` si el perfil exige evaluar contaminación antes de decidir sobre [material]. */
fun CountryProfile.requiresInspection(material: WasteMaterial): Boolean =
    inspectionRuleFor(material) != null
