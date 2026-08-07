package com.botabien.android.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService

/**
 * Grados del material de cristal. El nombre describe lo que el material
 * **hace**, no la versión de Android que hace falta: la capacidad se resuelve
 * una sola vez en [rememberGlassMaterial] y ninguna pantalla pregunta por el
 * nivel de API ni por la memoria del aparato.
 */
enum class BotaGlassMaterial {

    /**
     * Cristal transparente, el equivalente de la variante *clear* del lenguaje
     * de Apple: translúcido y tintado, con una capa de atenuación que sostiene
     * la legibilidad, y el borde rematado con un brillo especular.
     *
     * Es el material del cromo que flota sobre la cámara, donde el propio vídeo
     * aporta el color y el movimiento de detrás. No desenfoca: el visor monta un
     * `SurfaceView`, que el sistema compone en una capa aparte y **no se puede
     * capturar** para desenfocarlo. Sobre contenido en movimiento, además, el
     * desenfoque aporta menos de lo que cuesta.
     */
    Clear,

    /**
     * Sin translucidez: superficie tintada opaca con el borde apenas insinuado.
     * Es lo que se sirve en gama baja, con el ahorro de energía activo o cuando
     * el usuario tiene las animaciones desactivadas. Cuesta lo mismo que
     * cualquier fondo plano y mantiene la jerarquía intacta.
     */
    Veil,
}

/**
 * Material vigente para el subárbol. El valor por defecto es el más barato a
 * propósito: si alguien olvida proveerlo, la app se ve sobria pero **nunca se
 * arrastra**, que es el fallo que de verdad se nota en gama baja.
 */
val LocalGlassMaterial: ProvidableCompositionLocal<BotaGlassMaterial> =
    staticCompositionLocalOf { BotaGlassMaterial.Veil }

/**
 * Resuelve hasta dónde puede llegar el cristal en este dispositivo.
 *
 * Mide **capacidad y preferencia de dibujo**, que no es la gama del dispositivo
 * para inferencia: esa la decide `DeviceTierPolicy` y llega por caso de uso.
 * Cuando una pantalla disponga de ella podrá degradar más todavía envolviendo su
 * subárbol en [LocalGlassMaterial]; lo que se resuelve aquí es el techo.
 */
@Composable
fun rememberGlassMaterial(): BotaGlassMaterial {
    val context = LocalContext.current
    return remember(context) { resolveGlassMaterial(context) }
}

/**
 * Lee del sistema las tres señales que deciden el material.
 *
 * Android no expone un ajuste equivalente al «reducir transparencia» de iOS, así
 * que se atiende a las que sí existen y apuntan a lo mismo: memoria escasa,
 * ahorro de energía y animaciones desactivadas por el usuario.
 */
private fun resolveGlassMaterial(context: Context): BotaGlassMaterial = glassMaterialFor(
    lowRamDevice = context.getSystemService<ActivityManager>()?.isLowRamDevice == true,
    savingPower = context.getSystemService<PowerManager>()?.isPowerSaveMode == true,
    animationsDisabled = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        DEFAULT_ANIMATOR_SCALE,
    ) == 0f,
)

/**
 * La decisión en sí, sin depender del sistema para poder probarla.
 *
 * Cualquiera de las tres señales basta para renunciar a la translucidez: no se
 * ponderan ni se combinan, porque las tres significan lo mismo desde el punto de
 * vista del usuario —este aparato no está para efectos— y equivocarse hacia el
 * material barato solo cuesta un poco de belleza, mientras que equivocarse hacia
 * el caro cuesta fluidez en la cámara.
 */
internal fun glassMaterialFor(
    lowRamDevice: Boolean,
    savingPower: Boolean,
    animationsDisabled: Boolean,
): BotaGlassMaterial = if (lowRamDevice || savingPower || animationsDisabled) {
    BotaGlassMaterial.Veil
} else {
    BotaGlassMaterial.Clear
}

/** Escala de animación del sistema cuando el ajuste no está definido. */
private const val DEFAULT_ANIMATOR_SCALE = 1f
