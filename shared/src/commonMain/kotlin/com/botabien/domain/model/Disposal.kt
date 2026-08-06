package com.botabien.domain.model

/**
 * Decisión final de disposición producida por el motor de reglas.
 *
 * @property bin caneca destino según el perfil normativo activo.
 * @property route ruta de disposición de la caneca destino.
 * @property justification justificación normativa de la decisión; proviene del
 *   perfil (dato), de modo que la UI pueda citarla sin literales en código.
 * @property degradedByContamination `true` si la decisión se degradó a la
 *   caneca alternativa por contaminación del residuo.
 */
data class Disposal(
    val bin: BinDefinition,
    val route: DisposalRoute,
    val justification: String,
    val degradedByContamination: Boolean,
)
