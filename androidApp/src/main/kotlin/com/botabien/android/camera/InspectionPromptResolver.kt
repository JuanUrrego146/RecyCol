package com.botabien.android.camera

import androidx.annotation.StringRes
import com.botabien.android.R

/**
 * Traduce la `promptKey` de una regla de inspección del perfil normativo al
 * recurso de cadena que la UI debe mostrar (RF-020, RNF-011).
 *
 * La clave es dato del perfil; el texto visible vive únicamente en recursos
 * de cadenas. Una clave desconocida (por ejemplo, de un perfil de país más
 * nuevo que la app) cae en la solicitud genérica: un perfil nuevo nunca
 * rompe la captura dirigida (RNF-004).
 */
object InspectionPromptResolver {

    @StringRes
    fun resolve(promptKey: String): Int = when (promptKey) {
        "inspection.point_inside" -> R.string.camera_inspection_point_inside
        else -> R.string.camera_inspection_generic
    }
}
