package com.botabien.android.ui

import com.botabien.domain.usecase.ScanBinsUseCase
import com.botabien.domain.usecase.SelectCountryUseCase
import com.botabien.testing.FakeBinAvailabilityRepository
import com.botabien.testing.FakeBinDetector
import com.botabien.testing.FakeProfileRepository
import com.botabien.testing.TestProfiles

/**
 * Casos de uso que consume la capa de interfaz. La UI nunca llama a
 * repositorios ni a inferencia directamente (invariante 4): todo pasa por
 * estos casos de uso de `shared/domain/usecase/`.
 *
 * @property selectCountry configuración de país y perfil (CUS-001).
 * @property scanBins escaneo y confirmación de canecas (CUS-002); aquí se usa
 *   para reiniciar el conjunto al cambiar de país (RF-003).
 */
class AppDependencies(
    val selectCountry: SelectCountryUseCase,
    val scanBins: ScanBinsUseCase,
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
    )
}
