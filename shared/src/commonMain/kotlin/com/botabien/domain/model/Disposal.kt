package com.botabien.domain.model

/**
 * Motivo por el que la caneca decidida difiere de la ideal del material
 * (RF-027, coordinación #78). La pantalla de resultado lo usa para explicar
 * el cambio al usuario con el texto que declare el perfil.
 */
enum class FallbackReason {
    /** La caneca decidida es la ideal del material. */
    NONE,

    /** El residuo está contaminado: aplica la alternativa de la regla. */
    CONTAMINATION,

    /** La caneca ideal no está entre las disponibles: aplica la conservadora. */
    UNAVAILABLE_BIN,
}

/**
 * Decisión final de disposición producida por el motor de reglas.
 *
 * @property bin caneca destino según el perfil normativo activo.
 * @property route ruta de disposición de la caneca destino.
 * @property justification justificación normativa de la decisión; proviene del
 *   perfil (dato), de modo que la UI pueda citarla sin literales en código.
 * @property degradedByContamination `true` si la decisión se degradó a la
 *   caneca alternativa por contaminación del residuo. Equivale a
 *   `fallbackReason == CONTAMINATION`; se conserva por compatibilidad con los
 *   consumidores existentes (S07) hasta una limpieza coordinada.
 * @property fallbackReason motivo del cambio de caneca. El valor por defecto
 *   [FallbackReason.NONE] existe solo para no romper a los emisores en vuelo;
 *   el RuleEngine real lo emite desde S32 y es la señal autoritativa.
 */
data class Disposal(
    val bin: BinDefinition,
    val route: DisposalRoute,
    val justification: String,
    val degradedByContamination: Boolean,
    val fallbackReason: FallbackReason = FallbackReason.NONE,
)
