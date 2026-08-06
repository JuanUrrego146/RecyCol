package com.botabien.android.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.botabien.domain.model.ImageFrame
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementación de [CameraFrameSource] sobre CameraX (RF-009).
 *
 * Enlaza dos casos de uso: previsualización (opcional) y análisis de imagen
 * con contrapresión `KEEP_ONLY_LATEST`, de modo que el análisis nunca encola
 * frames viejos. Cada frame se reduce a su plano de luminancia sobre un anillo
 * de búferes reutilizados: la memoria es constante en sesiones largas y el
 * `ImageProxy` nativo se cierra siempre antes de salir del analizador.
 *
 * Los frames no se persisten, no se serializan y no se registran (RNF-012).
 */
class CameraXFrameSource(
    private val context: Context,
    private val analysisResolution: Size = DEFAULT_ANALYSIS_RESOLUTION,
) : CameraFrameSource {

    private val mutableState = MutableStateFlow<CameraSessionState>(CameraSessionState.Idle)
    override val state: StateFlow<CameraSessionState> = mutableState.asStateFlow()

    private val mutableFrames = MutableSharedFlow<ImageFrame>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val frames: Flow<ImageFrame> = mutableFrames.asSharedFlow()

    private val ring = FrameRing()
    private var analysisExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null

    override fun start(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider?) {
        if (!CameraPermission.isGranted(context)) {
            mutableState.value = CameraSessionState.PermissionRequired
            return
        }
        if (mutableState.value is CameraSessionState.Starting ||
            mutableState.value is CameraSessionState.Streaming
        ) {
            return
        }
        mutableState.value = CameraSessionState.Starting

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                bindUseCases(provider, lifecycleOwner, surfaceProvider)
                cameraProvider = provider
                mutableState.value = CameraSessionState.Streaming
            } catch (e: Exception) {
                releaseResources()
                mutableState.value = CameraSessionState.Failed(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    override fun stop() {
        releaseResources()
        mutableState.value = CameraSessionState.Idle
    }

    private fun bindUseCases(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider?,
    ) {
        val executor = Executors.newSingleThreadExecutor()
        analysisExecutor = executor

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            analysisResolution,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        ),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(executor) { proxy -> onFrame(proxy) }

        val useCases = buildList {
            add(analysis)
            if (surfaceProvider != null) {
                add(Preview.Builder().build().also { it.surfaceProvider = surfaceProvider })
            }
        }

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            *useCases.toTypedArray(),
        )
    }

    private fun onFrame(proxy: ImageProxy) {
        try {
            val plane = proxy.planes[0]
            val width = proxy.width
            val height = proxy.height
            val luma = ring.nextSlot(width * height)
            LumaPlaneCopier.copy(
                source = plane.buffer,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
                width = width,
                height = height,
                dest = luma,
            )
            mutableFrames.tryEmit(
                LumaImageFrame(
                    width = width,
                    height = height,
                    timestampMillis = System.currentTimeMillis(),
                    luma = luma,
                    rotationDegrees = proxy.imageInfo.rotationDegrees,
                ),
            )
        } finally {
            proxy.close()
        }
    }

    private fun releaseResources() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
    }

    companion object {
        /**
         * Resolución objetivo del análisis. Suficiente para las heurísticas de
         * calidad y para la entrada del clasificador; mantiene barata la copia
         * del plano Y en gama baja (RNF-001).
         */
        val DEFAULT_ANALYSIS_RESOLUTION = Size(640, 480)
    }
}
