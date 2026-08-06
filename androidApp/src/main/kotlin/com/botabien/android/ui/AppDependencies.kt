package com.botabien.android.ui

import com.botabien.domain.model.ImageFrame
import com.botabien.domain.usecase.ClassifyWasteUseCase
import com.botabien.domain.usecase.ScanBinsUseCase
import com.botabien.domain.usecase.SelectCountryUseCase
import com.botabien.testing.FakeBinAvailabilityRepository
import com.botabien.testing.FakeBinDetector
import com.botabien.testing.FakeFrameQualityAnalyzer
import com.botabien.testing.FakeProfileRepository
import com.botabien.testing.FakeRuleEngine
import com.botabien.testing.FakeWasteClassifier
import com.botabien.testing.StubImageFrame
import com.botabien.testing.TestProfiles
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/**
 * Casos de uso que consume la capa de interfaz. La UI nunca llama a
 * repositorios ni a inferencia directamente (invariante 4): todo pasa por
 * estos casos de uso de `shared/domain/usecase/`.
 *
 * @property selectCountry configuración de país y perfil (CUS-001).
 * @property scanBins escaneo y confirmación de canecas (CUS-002); aquí se usa
 *   para reiniciar el conjunto al cambiar de país (RF-003).
 * @property classifyWaste clasificación por cámara (CUS-003 a CUS-006).
 */
class AppDependencies(
    val selectCountry: SelectCountryUseCase,
    val scanBins: ScanBinsUseCase,
    val classifyWaste: ClassifyWasteUseCase,
)

/**
 * Composición provisional sobre los fakes deterministas de `shared/testing/`,
 * siguiendo el modelo de trabajo entre agentes: nadie espera a nadie. Cuando
 * los agentes RULES (S30, catálogo real) y DATA (S36, persistencia) publiquen
 * sus implementaciones, este cableado se sustituye por los módulos de Koin
 * sin tocar ninguna pantalla.
 *
 * `initiallyActive = null` reproduce el primer arranque: no hay perfil activo
 * hasta que el usuario elige país en el onboarding (RF-001).
 */
fun fakeAppDependencies(): AppDependencies {
    val profiles = FakeProfileRepository(
        catalog = listOf(TestProfiles.threeBins),
        initiallyActive = null,
    )
    val binAvailability = FakeBinAvailabilityRepository()
    return AppDependencies(
        selectCountry = SelectCountryUseCase(profiles),
        scanBins = ScanBinsUseCase(
            detector = FakeBinDetector(),
            profiles = profiles,
            binAvailability = binAvailability,
        ),
        classifyWaste = ClassifyWasteUseCase(
            qualityAnalyzer = FakeFrameQualityAnalyzer(),
            classifier = FakeWasteClassifier(),
            ruleEngine = FakeRuleEngine(),
            profiles = profiles,
            binAvailability = binAvailability,
        ),
    )
}

/**
 * Flujo de frames sintéticos a ~4 fps para ejercitar la pantalla de
 * clasificación mientras la cámara real (agente CAM, S10) no está integrada.
 * Con `CameraFrameSource` en main, este flujo se sustituye por
 * `frameSource.frames` sin tocar la pantalla.
 */
fun demoFrames(): Flow<ImageFrame> = flow {
    var timestamp = 0L
    while (currentCoroutineContext().isActive) {
        emit(StubImageFrame(timestampMillis = timestamp))
        timestamp += DEMO_FRAME_PERIOD_MS
        delay(DEMO_FRAME_PERIOD_MS)
    }
}

/** Periodo del flujo de demo: ~4 fps, la cadencia de la gama media. */
private const val DEMO_FRAME_PERIOD_MS = 250L
