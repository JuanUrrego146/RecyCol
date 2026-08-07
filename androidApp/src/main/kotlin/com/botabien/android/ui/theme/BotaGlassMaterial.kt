package com.botabien.android.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

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
     * la legibilidad, refracción en el canto y brillo especular en el borde.
     *
     * Es el material del cromo que flota sobre la cámara, donde el propio vídeo
     * aporta el color y el movimiento de detrás. No desenfoca: el visor monta un
     * `SurfaceView`, que el sistema compone en una capa aparte y **no se puede
     * capturar** para desenfocarlo.
     */
    Clear,

    /**
     * Sin translucidez: superficie tintada opaca con el borde apenas insinuado,
     * para quien ha pedido al sistema que no haya efectos.
     */
    Veil,
}

/**
 * Material vigente para el subárbol.
 */
val LocalGlassMaterial: ProvidableCompositionLocal<BotaGlassMaterial> =
    staticCompositionLocalOf { BotaGlassMaterial.Clear }

/**
 * Resuelve el material para este dispositivo.
 *
 * **Solo degrada por preferencia del usuario, no por potencia**, y eso es un
 * cambio deliberado respecto a la primera versión: sin desenfoque de fondo el
 * cristal son dos degradados y un borde, o sea lo mismo que cuesta cualquier
 * superficie plana. Degradarlo en dispositivos modestos o con el ahorro de
 * energía activo no ahorraba nada y dejaba la aplicación sin su material a
 * mucha gente —el ahorro de batería lo lleva encendido medio mundo— a cambio
 * de un beneficio inexistente.
 *
 * Si algún día se añade desenfoque real, ese sí cuesta, y entonces la
 * degradación por gama vuelve a tener sentido: consúltese a `DeviceTierPolicy`
 * por caso de uso y degrádese el subárbol correspondiente con
 * [LocalGlassMaterial].
 */
@Composable
fun rememberGlassMaterial(): BotaGlassMaterial {
    val context = LocalContext.current
    return remember(context) { resolveGlassMaterial(context) }
}

/**
 * Lee del sistema la única señal que importa: si el usuario ha desactivado las
 * animaciones. Android no expone un ajuste equivalente al «reducir
 * transparencia» de iOS, y esta es la señal más cercana que existe — quien
 * apaga las animaciones no quiere efectos, y la translucidez lo es tanto como
 * el movimiento.
 */
private fun resolveGlassMaterial(context: Context): BotaGlassMaterial = glassMaterialFor(
    animationsDisabled = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        DEFAULT_ANIMATOR_SCALE,
    ) == 0f,
)

/** La decisión en sí, sin depender del sistema para poder probarla. */
internal fun glassMaterialFor(animationsDisabled: Boolean): BotaGlassMaterial =
    if (animationsDisabled) BotaGlassMaterial.Veil else BotaGlassMaterial.Clear

/** Escala de animación del sistema cuando el ajuste no está definido. */
private const val DEFAULT_ANIMATOR_SCALE = 1f
