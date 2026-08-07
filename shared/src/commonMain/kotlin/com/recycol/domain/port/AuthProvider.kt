package com.recycol.domain.port

import com.recycol.domain.model.Credentials
import com.recycol.domain.model.Session

/**
 * Puerto de autenticación (CUS-010).
 *
 * En v1 no hay backend: el agente DATA publica únicamente un stub que devuelve
 * [Session.Guest] y la app funciona completa sin cuenta. Definir el puerto
 * ahora evita que la lógica de sesión se filtre por la app cuando llegue el
 * backend real. Contrato inmutable desde M0.
 */
interface AuthProvider {

    /** Sesión vigente. En v1 devuelve siempre [Session.Guest]. */
    suspend fun currentSession(): Session

    /**
     * Intento de inicio de sesión. En v1 el stub resuelve de forma determinista
     * sin tocar la red; el contrato ya modela el caso de fallo para la fase 3.
     */
    suspend fun signIn(credentials: Credentials): Result<Session>
}
