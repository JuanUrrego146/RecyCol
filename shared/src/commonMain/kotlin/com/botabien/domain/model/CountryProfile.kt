package com.botabien.domain.model

/**
 * Perfil normativo de un país: todo lo específico de una normativa vive aquí
 * como dato, nunca en código (RNF-004). Agregar un país es agregar un archivo
 * de perfil y registrarlo en el catálogo; está prohibido condicionar
 * comportamiento por país con `if` en cualquier módulo.
 *
 * @property isoCode código ISO 3166-1 alfa-2 en minúsculas, por ejemplo `"co"`.
 * @property regulationName nombre corto de la norma, por ejemplo «Resolución 2184 de 2019».
 * @property regulationReference referencia citable completa de la norma.
 * @property bins canecas que define la norma.
 * @property rules regla de destino por material.
 * @property inspectionRules materiales que exigen inspección de contaminación antes de decidir.
 * @property conservativeBin caneca por defecto (ruta conservadora) para materiales
 *   sin regla o cuando la caneca ideal no está disponible: ante la duda, no se
 *   contamina la corriente aprovechable.
 * @property unavailableBinNotice plantilla del aviso al usuario cuando la
 *   caneca ideal no está disponible (RF-008, coordinación #61; texto aprobado
 *   por Juan el 06/08/2026). El motor de reglas sustituye los marcadores
 *   `{ideal}` y `{assigned}` por los nombres visibles de las canecas. Cadena
 *   vacía = el perfil no declara aviso y `Disposal.unavailableBinNotice` va nulo.
 */
data class CountryProfile(
    val isoCode: String,
    val regulationName: String,
    val regulationReference: String,
    val bins: List<BinDefinition>,
    val rules: List<MaterialRule>,
    val inspectionRules: List<InspectionRule>,
    val conservativeBin: BinId,
    val unavailableBinNotice: String = "",
)

/**
 * Regla de destino de un material dentro de un perfil normativo.
 *
 * @property material material al que aplica la regla.
 * @property targetBin caneca destino cuando el residuo está limpio y seco.
 * @property contaminatedFallback caneca alternativa cuando el residuo está
 *   contaminado; `null` si la contaminación no cambia el destino.
 * @property justification justificación normativa citable de la regla.
 */
data class MaterialRule(
    val material: WasteMaterial,
    val targetBin: BinId,
    val contaminatedFallback: BinId?,
    val justification: String,
)

/**
 * Regla de inspección: declara que un material no puede decidirse sin evaluar
 * contaminación (por ejemplo, el vaso de cartón para bebidas exige mirar adentro).
 *
 * @property material material que dispara la inspección.
 * @property promptKey clave del mensaje de captura dirigida que la UI resuelve
 *   desde recursos de cadenas (RNF-011), por ejemplo `"inspection.point_inside"`.
 * @property requiresInteriorView `true` si hace falta una toma del interior del objeto.
 */
data class InspectionRule(
    val material: WasteMaterial,
    val promptKey: String,
    val requiresInteriorView: Boolean,
)
