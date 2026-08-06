# Contexto para vibe coding — BotaBien

Aplicación Android (portable a iOS) que clasifica residuos por cámara con redes neuronales que corren íntegramente en el dispositivo, y devuelve la caneca correcta según el perfil normativo del país activo. Detecta además si un reciclable está contaminado y degrada la decisión cuando corresponde.

La especificación completa está en `docs/F_Analisis_de_Requerimientos_V1,0_BotaBien.docx` y los diagramas en `docs/arquitectura.md`. **Consúltalos antes de diseñar cualquier módulo.** El plan y el orden de trabajo están en `plan/plan_de_trabajo.md`.

## Stack y convenciones (obligatorias)

- Kotlin 2.x, Kotlin Multiplatform. JDK 17. Gradle con version catalog en `gradle/libs.versions.toml`.
- Android: Jetpack Compose + Material 3, CameraX, LiteRT (`com.google.ai.edge.litert`). `minSdk = 26`, `targetSdk = 34`.
- Entrenamiento: Python 3.11, PyTorch, exportación a LiteRT. Vive en `ml/`, aislado del código de la app.
- **Identificadores en inglés** (`WasteCategory`, `classifyWaste`), `camelCase` para funciones y propiedades, `PascalCase` para clases, `SCREAMING_SNAKE_CASE` para constantes. **Documentación, comentarios KDoc, textos de UI y mensajes de commit en español.**
- Paradigma: Clean Architecture por capas + orientación a objetos. Dominio puro sin frameworks.
- **CI en runners propios**: los checks corren en runners self-hosted dockerizados sobre la misma imagen `botabien/android-build` del proyecto (ver `.github/runner/README.md`). La protección de rama exige el check «Compilar y probar» en verde para fusionar a `main`; si los runners propios caen, el respaldo es `gh workflow run ci-respaldo.yml --ref <rama>`. No canceles runs de `main` ni redispares checks en masa.
- Inyección de dependencias con Koin. Concurrencia con corrutinas y `Flow`. Nada de `GlobalScope`.
- No introducir dependencias nuevas sin justificarlo en el PR y añadirlas al version catalog.

## Estructura de módulos

```
BotaBien/
├── shared/                     # Kotlin Multiplatform, SIN dependencias de Android
│   ├── domain/                 # entidades, casos de uso, puertos (interfaces)
│   ├── rules/                  # motor de reglas y perfiles normativos
│   ├── data/                   # repositorios, SQLDelight, DataStore
│   └── resources/profiles/     # perfiles por país en JSON
├── androidApp/
│   ├── ui/                     # Compose: pantallas, design system
│   ├── camera/                 # CameraX, análisis de calidad de imagen
│   ├── inference/              # LiteRT, delegados, tiering de dispositivo
│   └── di/
├── ml/                         # pipeline de datos y entrenamiento (Python)
├── docs/
└── plan/
```

## Arquitectura — reglas fijas

Estos invariantes no se negocian. Un PR que los rompa se rechaza aunque funcione.

1. **`shared/` no conoce Android.** Cero imports de `android.*`, `androidx.*` o de LiteRT en `shared/`. Si el dominio necesita una capacidad de plataforma, se define una interfaz (puerto) en `shared/domain/port/` y se implementa en `androidApp/`. Esta regla es lo que hace posible la fase iOS; violarla mata el requisito RNF-005.
2. **El modelo predice materiales, nunca canecas.** La red neuronal devuelve una `WasteMaterial` con su confianza. La conversión material → caneca ocurre exclusivamente en `shared/rules/RuleEngine`. Ningún clasificador, ninguna capa de UI y ningún repositorio puede mapear material a color de caneca por su cuenta.
3. **Las reglas normativas viven en datos, no en código.** Todo lo específico de un país está en `shared/resources/profiles/<iso>.json`. Agregar un país es agregar un archivo y registrarlo en el catálogo. Está prohibido escribir `if (country == "CO")` en cualquier parte del proyecto.
4. **La UI nunca llama a `inference/` ni a `data/` directamente.** Pasa siempre por un caso de uso de `shared/domain/usecase/`. Los `ViewModel` orquestan casos de uso, no lógica de negocio.
5. **La política de gama del dispositivo se consulta, no se asume.** Cualquier función costosa pregunta a `DeviceTierPolicy` antes de activarse. Ningún módulo asume que hay NPU, GPU o memoria disponible.
6. **Las imágenes no salen del proceso.** No se escriben a disco, no se envían por red, no se registran en logs. El historial guarda únicamente el resultado, nunca el frame.
7. **Sin red en tiempo de clasificación.** El módulo `inference/` y el motor de reglas no pueden tener ninguna dependencia de red. La app debe funcionar completa en modo avión.

## Contratos entre agentes

Los agentes trabajan en paralelo. Para que no se bloqueen ni colisionen, estas interfaces se definen en el milestone M0 y a partir de ahí **son inmutables**: cambiarlas requiere una issue propia y coordinación explícita.

```kotlin
// shared/domain/port/WasteClassifier.kt — implementa el agente EDGE, consume el agente FRONT
interface WasteClassifier {
    suspend fun classify(frame: ImageFrame): ClassificationResult
    suspend fun inspectContamination(frame: ImageFrame): ContaminationResult
}

// shared/domain/port/BinDetector.kt — implementa el agente BINS
interface BinDetector {
    suspend fun detectBins(frame: ImageFrame): List<DetectedBin>
}

// shared/domain/port/FrameQualityAnalyzer.kt — implementa el agente CAM
interface FrameQualityAnalyzer {
    fun analyze(frame: ImageFrame): FrameQuality   // sharpness, luminance, lensSoiling, framing
}

// shared/rules/RuleEngine.kt — implementa el agente RULES
interface RuleEngine {
    fun resolve(material: WasteMaterial, contamination: ContaminationState,
                availableBins: Set<BinId>, profile: CountryProfile): Disposal
}

// shared/domain/port/DeviceTierPolicy.kt — implementa el agente EDGE
interface DeviceTierPolicy {
    val tier: DeviceTier                            // LOW, MID, HIGH
    fun isEnabled(feature: Feature): Boolean
}

// shared/domain/port/AuthProvider.kt — implementa el agente DATA (solo stub en v1)
interface AuthProvider {
    suspend fun currentSession(): Session           // v1 devuelve siempre Session.Guest
    suspend fun signIn(credentials: Credentials): Result<Session>
}
```

Mientras una implementación real no exista, cada agente trabaja contra un *fake* en `shared/testing/` que devuelve datos deterministas. **Nadie espera a nadie.**

## Requerimientos que gobiernan el código

- Cada issue del repo cita los RF y CUS que implementa. Implementa **exactamente** eso: ni una feature de más ni una de menos. Si detectas que falta un requerimiento, abre una issue nueva; no lo improvises dentro de otra.
- RNF siempre vigentes durante todo el desarrollo:
  - **RNF-001** — Objetivo ≤ 2 s extremo a extremo en gama media; ≤ 4 s en gama baja. Es una meta de diseño, no un bloqueo: si un dispositivo no llega, la app degrada funciones pero **la clasificación por cámara sigue funcionando**.
  - **RNF-002** — Funcionamiento sin conexión, siempre. Cualquier PR que añada una llamada de red en la ruta de clasificación se rechaza.
  - **RNF-004** — Un país nuevo debe ser un archivo JSON. Si añadir un país requiere tocar código Kotlin, el diseño está mal.
  - **RNF-005** — `shared/` compila para JVM y iOS sin cambios.
  - **RNF-008** — ≥ 85 % top-1 en material y ≥ 95 % de acierto en la caneca destino. La segunda métrica manda: acertar la caneca importa más que acertar el nombre exacto del material.
  - **RNF-009** — Estética minimalista de inspiración iOS. Todo componente sale del design system, nunca se estilizan colores o tipografías ad hoc.
  - **RNF-011** — Ningún literal de texto visible en el código. Todo pasa por recursos de cadenas.
  - **RNF-012** — Los frames de cámara no se persisten ni se registran.

## Modelos y datos — restricción dura

**No hay dataset propio y no se va a recolectar uno.** El pipeline se construye exclusivamente sobre fuentes públicas, augmentación y síntesis:

- Fuentes base: Garbage Dataset v2, Garbage Classification (12 clases, ~15.9k imágenes), RealWaste (UCI, ~4.7k imágenes en entorno de relleno real), TrashNet, TACO y ZeroWaste.
- Unificación mediante un mapeo de etiquetas explícito y versionado en `ml/taxonomy/label_mapping.yaml`. Nunca se mezclan datasets sin pasar por ese mapeo.
- La contaminación se sintetiza: segmentación del objeto limpio con U²-Net y composición de texturas de líquido, grasa y residuo alimenticio sobre su superficie, siguiendo el enfoque de EcoBin. Es la única vía viable sin etiquetado manual.
- Augmentación agresiva orientada al dominio móvil real: desenfoque gaussiano y de movimiento, variación de brillo y temperatura de color, ruido, artefactos JPEG, oclusión parcial y perspectiva.
- **Validación cruzada por dataset**: se entrena sobre unos y se valida sobre otro no visto. Una métrica medida sobre el mismo dataset de entrenamiento no cuenta como evidencia de generalización.
- Toda fuente usada se documenta con su licencia en `ml/DATASETS.md` antes de incorporarse.

## Política de gama de dispositivo

`DeviceTierPolicy` se resuelve al arrancar combinando RAM total, número de núcleos, disponibilidad de delegados NNAPI y GPU, nivel de API y — la señal más fiable — un micro-benchmark de calentamiento que mide la latencia real de inferencia. La gama se reevalúa si la latencia observada se degrada de forma sostenida.

| | Gama baja | Gama media | Gama alta |
|---|---|---|---|
| Clasificación por cámara | **Sí**, bajo demanda con botón | **Sí**, continua ~5 fps | **Sí**, continua ~10 fps |
| Modelo | MobileNetV3-Small INT8 | MobileNetV3-Large 0.75 INT8 | EfficientNet-Lite2 INT8 |
| Detección del objeto | No: marco guía fijo en pantalla | Sí, detector ligero | Sí, detector completo |
| Etapa de contaminación | Solo en captura manual dirigida | Bajo demanda de la regla | Automática |
| Escaneo de canecas | Foto única | Continuo | Continuo |
| Análisis de calidad de imagen | Nitidez y luz | Completo | Completo |

La clasificación por cámara está disponible en las tres gamas sin excepción. Lo que se degrada es la fluidez y las funciones auxiliares, nunca la función principal.

## Definición de "hecho"

Una issue se cierra cuando, todo a la vez:

1. Compila sin warnings nuevos y `./gradlew :shared:allTests` pasa.
2. Cumple literalmente el criterio de hecho escrito en la issue.
3. No rompe ningún invariante de la sección "Arquitectura — reglas fijas".
4. No degrada ningún RNF medible; si toca la ruta de clasificación, se adjunta la latencia medida.
5. La lógica de dominio nueva tiene pruebas unitarias.
6. Los textos visibles están en recursos de cadenas.
7. El PR referencia la issue y los RF/CUS que implementa.

## Qué NO hacer

- No agregar funcionalidades que no estén trazadas a un RF del documento de requerimientos.
- No cambiar el stack, la estructura de módulos ni las convenciones de nombres.
- No mover lógica de negocio a la capa de UI ni a los `ViewModel`.
- No introducir llamadas de red en la ruta de clasificación, ni siquiera "temporalmente para probar".
- No condicionar comportamiento por país con `if` en el código: eso va en el perfil JSON.
- No guardar, cachear ni loguear frames de cámara.
- No modificar las interfaces de la sección "Contratos entre agentes" sin una issue dedicada.
- No implementar el backend de autenticación en v1: solo el stub y su interfaz.
- No reportar exactitud medida sobre el mismo dataset con el que se entrenó.
- No inventar personas, roles ni responsables en la documentación.
