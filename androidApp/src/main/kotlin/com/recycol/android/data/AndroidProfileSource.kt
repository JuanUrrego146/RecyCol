package com.recycol.android.data

import android.content.Context
import com.recycol.rules.profile.ProfileSource
import java.io.IOException

/**
 * Implementación Android de [ProfileSource] sobre `assets/` (S30/S36,
 * coordinación #48).
 *
 * Los JSON de `shared/resources/profiles/` (RULES) se empaquetan como raíz
 * de `assets/` — ver `assets.srcDirs` en `androidApp/build.gradle.kts` — así
 * que `catalog.json`, `co.json`, etc. son accesibles por su nombre tal cual
 * lo espera [ProfileCatalog][com.recycol.rules.profile.ProfileCatalog], sin
 * copiarlos ni versionarlos por duplicado.
 */
class AndroidProfileSource(
    private val context: Context,
) : ProfileSource {

    override fun read(fileName: String): String? =
        try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            null
        }
}
