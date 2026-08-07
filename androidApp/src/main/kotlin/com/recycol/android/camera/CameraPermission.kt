package com.recycol.android.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Consulta del permiso de cámara. La solicitud interactiva es de la capa de
 * UI (agente FRONT); aquí solo se comprueba el estado actual.
 */
object CameraPermission {

    /** Nombre del permiso que la UI debe solicitar. */
    const val NAME: String = Manifest.permission.CAMERA

    /** `true` si el permiso de cámara está concedido ahora mismo. */
    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, NAME) == PackageManager.PERMISSION_GRANTED
}
