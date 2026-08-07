package com.recycol.android.ui.classify

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.recycol.android.R
import com.recycol.android.camera.CameraFrameSource
import com.recycol.android.camera.CameraPermission
import com.recycol.android.camera.CameraSessionState
import com.recycol.android.ui.components.BotaButton
import com.recycol.android.ui.components.BotaButtonStyle
import com.recycol.android.ui.theme.BotaTheme

/**
 * Visor de cámara real sobre [CameraFrameSource] (integración con el agente
 * CAM, RF-009). Gestiona el flujo interactivo del permiso — que el contrato
 * de `CameraPermission` asigna a la capa de UI — y arranca la captura ligada
 * al ciclo de vida cuando hay permiso y superficie.
 *
 * La imagen nunca sale del proceso (RNF-012): este visor solo pinta la
 * previsualización; los frames van al dominio por el flujo de la fuente.
 */
@Composable
fun CameraViewfinder(
    frameSource: CameraFrameSource,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sessionState by frameSource.state.collectAsState()

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var permissionGranted by remember { mutableStateOf(CameraPermission.isGranted(context)) }
    var permissionAsked by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionAsked = true
        permissionGranted = granted
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted && !permissionAsked) {
            permissionLauncher.launch(CameraPermission.NAME)
        }
    }

    LaunchedEffect(permissionGranted, previewView, lifecycleOwner) {
        val surface = previewView
        if (permissionGranted && surface != null) {
            frameSource.start(lifecycleOwner, surface.surfaceProvider)
        }
    }
    DisposableEffect(frameSource) {
        onDispose { frameSource.stop() }
    }

    Box(modifier = modifier.background(BotaTheme.colors.cameraBackdrop)) {
        if (permissionGranted) {
            AndroidView(
                factory = { viewContext ->
                    PreviewView(viewContext).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }.also { previewView = it }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PermissionRequest(
                onRequest = { permissionLauncher.launch(CameraPermission.NAME) },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = BotaTheme.spacing.xxxl),
            )
        }

        if (sessionState is CameraSessionState.Failed) {
            Text(
                text = stringResource(R.string.camera_error_message),
                style = BotaTheme.typography.footnote,
                color = BotaTheme.colors.onScrim,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(BotaTheme.spacing.xxxl),
            )
        }
    }
}

/** Estado sin permiso: explicación breve y acción para concederlo. */
@Composable
private fun PermissionRequest(
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.camera_permission_message),
            style = BotaTheme.typography.subheadline,
            color = BotaTheme.colors.onScrim,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(BotaTheme.spacing.xl))
        BotaButton(
            text = stringResource(R.string.camera_permission_action),
            onClick = onRequest,
            style = BotaButtonStyle.Tinted,
            compact = true,
        )
    }
}
