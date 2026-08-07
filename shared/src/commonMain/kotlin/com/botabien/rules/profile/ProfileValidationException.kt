package com.botabien.rules.profile

/**
 * Rechazo explícito de un perfil o catálogo inválido (RF-002, RF-004).
 *
 * Acumula todos los problemas encontrados en una sola pasada para que el autor
 * del perfil corrija el archivo completo de una vez. El mensaje es técnico y
 * va a logs y diagnósticos; el texto que ve el usuario lo resuelve la UI desde
 * recursos de cadenas (RNF-011), nunca desde esta excepción.
 *
 * @property sourceName nombre del archivo o identificador del perfil rechazado.
 * @property problems lista de problemas concretos, uno por hallazgo.
 */
class ProfileValidationException(
    val sourceName: String,
    val problems: List<String>,
) : Exception(
    "El perfil «$sourceName» es inválido y fue rechazado:\n" +
        problems.joinToString("\n") { " - $it" },
)
