package com.recycol.rules.profile

/**
 * Entrada del catálogo de perfiles normativos (`catalog.json`).
 *
 * El catálogo modela **país → institución**: un país puede tener varios
 * perfiles (el nacional por defecto y variantes institucionales como la
 * GTC 24 en universidades y hospitales colombianos). Agregar un perfil es
 * agregar su archivo JSON y su entrada aquí, nunca tocar código (RNF-004).
 *
 * @property id identificador único del perfil en el catálogo, por ejemplo
 *   `"co"` o `"co-gtc24"`.
 * @property country código ISO 3166-1 alfa-2 del país al que pertenece.
 * @property displayName nombre visible del perfil en el idioma del catálogo;
 *   es dato, no un literal de código.
 * @property fileName archivo del perfil dentro del catálogo de recursos.
 * @property isDefault `true` si es el perfil por defecto de su país; cada país
 *   declara exactamente uno.
 */
data class ProfileDescriptor(
    val id: String,
    val country: String,
    val displayName: String,
    val fileName: String,
    val isDefault: Boolean,
)
