package com.recycol.android.camera

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.recycol.domain.model.ImageFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fuente de frames de cámara hacia el dominio (RF-009, CUS-003).
 *
 * La capa de UI (agente FRONT) observa [state] para pintar los estados de
 * permiso y error, y los `ViewModel` consumen [frames] para alimentar los
 * casos de uso. Este módulo no pinta nada: solo captura y convierte.
 */
interface CameraFrameSource {

    /** Estado observable de la sesión de cámara. */
    val state: StateFlow<CameraSessionState>

    /**
     * Frames convertidos al tipo del dominio, conflados: si el consumidor va
     * más lento que la cámara, se descartan frames intermedios y siempre se
     * procesa el más reciente. Cada frame es válido solo durante el
     * procesamiento síncrono del colector (ver [LumaImageFrame]).
     */
    val frames: Flow<ImageFrame>

    /**
     * Arranca la captura ligada al ciclo de vida dado. Si falta el permiso de
     * cámara, no arranca y publica [CameraSessionState.PermissionRequired]:
     * la UI decide cómo pedirlo y vuelve a llamar a [start] tras la concesión.
     *
     * @param surfaceProvider destino de la previsualización; `null` para
     *   capturar sin previsualizar (p. ej. en pruebas instrumentadas).
     */
    fun start(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider?)

    /** Detiene la captura y libera cámara, ejecutor y búferes. Idempotente. */
    fun stop()
}

/** Estados de la sesión de captura que la UI puede pintar. */
sealed interface CameraSessionState {

    /** Sin sesión activa. */
    data object Idle : CameraSessionState

    /** Esperando a que el proveedor de cámara quede enlazado. */
    data object Starting : CameraSessionState

    /** Capturando y emitiendo frames. */
    data object Streaming : CameraSessionState

    /**
     * El permiso de cámara no está concedido. La captura no puede empezar;
     * la app sigue viva y la UI muestra el flujo de solicitud (RF-009).
     */
    data object PermissionRequired : CameraSessionState

    /** La cámara no pudo enlazarse o falló en uso. */
    data class Failed(val cause: Throwable) : CameraSessionState
}
