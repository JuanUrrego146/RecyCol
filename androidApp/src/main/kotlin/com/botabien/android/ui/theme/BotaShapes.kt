package com.botabien.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Radios de esquina del design system. Curvas amplias y consistentes,
 * al estilo de las superficies continuas de iOS (RNF-009). Ningún
 * componente declara `RoundedCornerShape` con valores propios.
 */
@Immutable
data class BotaShapes(
    /** 10 dp: controles pequeños. */
    val small: Shape = RoundedCornerShape(10.dp),
    /** 14 dp: botones y campos. */
    val medium: Shape = RoundedCornerShape(14.dp),
    /** 20 dp: tarjetas. */
    val large: Shape = RoundedCornerShape(20.dp),
    /** 28 dp solo en las esquinas superiores: hojas inferiores. */
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    /** Cápsula: píldoras de estado y asas. */
    val capsule: Shape = RoundedCornerShape(percent = 50),
)

/** Proveedor interno de los radios activos. */
internal val LocalBotaShapes = staticCompositionLocalOf { BotaShapes() }
