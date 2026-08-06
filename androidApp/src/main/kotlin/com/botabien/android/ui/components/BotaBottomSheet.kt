package com.botabien.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.botabien.android.ui.theme.BotaTheme

/**
 * Hoja inferior del design system: superficie elevada con esquinas
 * superiores amplias, asa discreta y velo suave, al estilo de las hojas
 * modales de iOS (RNF-009). Envuelve [ModalBottomSheet] de Material 3 para
 * que ninguna pantalla configure colores ni formas por su cuenta.
 *
 * @param onDismissRequest Se invoca cuando el usuario descarta la hoja.
 * @param sheetState Estado de la hoja; por defecto el estándar de Material 3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotaBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = BotaTheme.shapes.sheet,
        containerColor = BotaTheme.colors.surfaceElevated,
        scrimColor = BotaTheme.colors.scrim,
        dragHandle = { BotaSheetGrabber() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = BotaTheme.spacing.screenMargin,
                    end = BotaTheme.spacing.screenMargin,
                    bottom = BotaTheme.spacing.xxl,
                ),
            content = content,
        )
    }
}

/** Asa de arrastre: cápsula discreta idéntica al grabber de iOS. */
@Composable
private fun BotaSheetGrabber() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BotaTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(5.dp)
                .clip(BotaTheme.shapes.capsule)
                .background(BotaTheme.colors.fill),
        )
    }
}
