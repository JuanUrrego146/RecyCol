package com.botabien.domain.model

import kotlin.jvm.JvmInline

/**
 * Identificador estable de una caneca dentro de un perfil normativo.
 * El valor proviene del archivo de perfil del país (por ejemplo `"white"`).
 */
@JvmInline
value class BinId(val value: String)

/**
 * Definición de una caneca según el perfil normativo del país activo.
 *
 * Lo específico del país —color, nombre visible— vive aquí como dato del
 * perfil (RNF-004); la semántica portable es la [route].
 *
 * @property id identificador estable dentro del perfil.
 * @property displayName nombre visible de la caneca; es dato del perfil, no un literal de código.
 * @property colorHex color de la caneca en formato `#RRGGBB`.
 * @property route ruta de disposición que representa la caneca.
 */
data class BinDefinition(
    val id: BinId,
    val displayName: String,
    val colorHex: String,
    val route: DisposalRoute,
)
