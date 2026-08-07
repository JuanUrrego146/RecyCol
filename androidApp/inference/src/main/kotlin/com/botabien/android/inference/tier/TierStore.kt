package com.botabien.android.inference.tier

import android.content.Context
import com.botabien.domain.model.DeviceTier

/**
 * Caché de la gama resuelta (RF-029: «queda cacheada»).
 *
 * Puerto interno doblable en pruebas. La implementación de producción usa
 * `SharedPreferences` propias del módulo: la persistencia de dominio
 * (SQLDelight/DataStore) es ámbito del agente DATA y esta caché es un detalle
 * técnico del runtime, no estado de la aplicación.
 */
interface TierStore {

    /** Gama cacheada para el fingerprint actual del sistema, o nula. */
    fun read(): DeviceTier?

    /** Persiste la gama resuelta. */
    fun write(tier: DeviceTier)

    /** Invalida la caché (p. ej. para recálculo forzado). */
    fun clear()

    /** Sobrescritura manual del usuario (RF-031), o nula si rige el modo automático. */
    fun readManualOverride(): DeviceTier?

    /** Persiste la sobrescritura manual; `null` vuelve al modo automático. */
    fun writeManualOverride(tier: DeviceTier?)
}

/**
 * [TierStore] sobre `SharedPreferences`.
 *
 * La entrada se invalida sola cuando cambia el fingerprint del sistema
 * (actualización de OS o de vendor: los delegados disponibles pueden cambiar).
 */
class PrefsTierStore(
    context: Context,
    private val systemFingerprint: String = android.os.Build.FINGERPRINT,
) : TierStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): DeviceTier? {
        if (prefs.getString(KEY_FINGERPRINT, null) != systemFingerprint) return null
        val name = prefs.getString(KEY_TIER, null) ?: return null
        return DeviceTier.entries.firstOrNull { it.name == name }
    }

    override fun write(tier: DeviceTier) {
        prefs.edit()
            .putString(KEY_TIER, tier.name)
            .putString(KEY_FINGERPRINT, systemFingerprint)
            .apply()
    }

    override fun clear() {
        // Solo la gama medida: la preferencia manual del usuario sobrevive
        // a un recálculo (RF-031, la decisión explícita del usuario manda).
        prefs.edit()
            .remove(KEY_TIER)
            .remove(KEY_FINGERPRINT)
            .apply()
    }

    override fun readManualOverride(): DeviceTier? {
        val name = prefs.getString(KEY_MANUAL_OVERRIDE, null) ?: return null
        return DeviceTier.entries.firstOrNull { it.name == name }
    }

    override fun writeManualOverride(tier: DeviceTier?) {
        prefs.edit().apply {
            if (tier == null) remove(KEY_MANUAL_OVERRIDE) else putString(KEY_MANUAL_OVERRIDE, tier.name)
        }.apply()
    }

    private companion object {
        const val PREFS_NAME = "botabien_inference_tier"
        const val KEY_TIER = "tier"
        const val KEY_FINGERPRINT = "fingerprint"
        const val KEY_MANUAL_OVERRIDE = "manual_override"
    }
}
