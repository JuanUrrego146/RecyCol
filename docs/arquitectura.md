# Arquitectura — BotaBien

## Visión general

BotaBien es una aplicación móvil de clasificación de residuos que ejecuta toda su inteligencia en el dispositivo. La cámara alimenta un pipeline de dos etapas: una red clasifica el material del residuo y una segunda inspecciona contaminación. El resultado del modelo nunca es una caneca: es un material con su confianza. La traducción material a caneca la hace un motor de reglas que consume un perfil normativo intercambiable por país, lo que permite escalar a otras normativas sin tocar los modelos ni el código.

La aplicación se estructura en Kotlin Multiplatform con un módulo `shared` que no conoce ninguna plataforma. Ahí viven el dominio, el motor de reglas, los perfiles y la persistencia. Cámara, inferencia e interfaz son específicas de cada plataforma y se conectan al dominio mediante puertos. Esta separación es lo que hace que la fase iOS sea una implementación de adaptadores y no una reescritura.

```mermaid
flowchart TB
    subgraph AND["Aplicación Android — Kotlin, Jetpack Compose"]
        UI["Capa de presentación<br/>Compose + Design System iOS-like"]
        VM["ViewModels<br/>orquestan casos de uso"]
        CAM["Módulo cámara<br/>CameraX + análisis de calidad"]
        INF["Módulo inferencia<br/>LiteRT + delegados NNAPI/GPU"]
        TIER["DeviceTierPolicy<br/>benchmark de arranque"]
    end

    subgraph SH["Módulo shared — Kotlin Multiplatform, sin dependencias de plataforma"]
        UC["Casos de uso<br/>ClassifyWaste, ScanBins, SelectCountry"]
        DOM["Dominio<br/>WasteMaterial, Disposal, DetectedBin"]
        RULES["RuleEngine<br/>material + contaminación + canecas → caneca destino"]
        PROF["Catálogo de perfiles<br/>JSON por país"]
        REPO["Repositorios<br/>SQLDelight + DataStore"]
    end

    subgraph ML["Pipeline de modelos — Python, fuera de la app"]
        DATA["Datasets públicos<br/>unificados por taxonomía"]
        SYN["Síntesis de contaminación<br/>U2-Net + composición"]
        TRAIN["Entrenamiento y cuantización INT8"]
    end

    subgraph IOS["Aplicación iOS — fase 2"]
        SUI["SwiftUI + AVFoundation + LiteRT iOS"]
    end

    UI --> VM
    VM --> UC
    CAM --> VM
    INF --> VM
    TIER --> INF
    TIER --> CAM
    UC --> DOM
    UC --> RULES
    UC --> REPO
    RULES --> PROF
    DATA --> SYN
    SYN --> TRAIN
    TRAIN -->|"modelos .tflite empaquetados"| INF
    SUI -.->|"reutiliza sin cambios"| SH
```

## Casos de uso

```mermaid
flowchart LR
    U(("Usuario"))
    S(("Sistema"))

    subgraph BotaBien
        UC1(["CUS-001 Configurar país y perfil"])
        UC2(["CUS-002 Escanear canecas disponibles"])
        UC3(["CUS-003 Clasificar residuo con la cámara"])
        UC4(["CUS-004 Asistir la captura"])
        UC5(["CUS-005 Detectar contaminación"])
        UC6(["CUS-006 Gestionar baja confianza"])
        UC7(["CUS-007 Consultar justificación normativa"])
        UC8(["CUS-008 Ajustar capacidades por gama"])
        UC9(["CUS-009 Consultar historial local"])
        UC10(["CUS-010 Iniciar sesión — preparado"])
    end

    U --> UC1
    U --> UC2
    U --> UC3
    U --> UC6
    U --> UC7
    U --> UC9
    U --> UC10
    S --> UC4
    S --> UC5
    S --> UC8
    UC3 --> UC4
    UC3 --> UC5
    UC3 --> UC6
    UC2 --> UC3
```

| Identificador | Nombre |
|---|---|
| CUS-001 | Configurar país y perfil de clasificación |
| CUS-002 | Escanear y registrar las canecas disponibles |
| CUS-003 | Clasificar un residuo mediante la cámara |
| CUS-004 | Asistir la captura ante condiciones adversas |
| CUS-005 | Detectar contaminación en residuos aprovechables |
| CUS-006 | Gestionar resultados de baja confianza |
| CUS-007 | Consultar la justificación normativa del resultado |
| CUS-008 | Ajustar capacidades según la gama del dispositivo |
| CUS-009 | Consultar el historial local de clasificaciones |
| CUS-010 | Iniciar sesión — módulo preparado para versión futura |

## Modelo de dominio

```mermaid
classDiagram
    class ClassifyWasteUseCase {
        -WasteClassifier classifier
        -FrameQualityAnalyzer qualityAnalyzer
        -RuleEngine ruleEngine
        -ProfileRepository profiles
        +execute(ImageFrame) ClassificationOutcome
    }

    class WasteClassifier {
        <<interface>>
        +classify(ImageFrame) ClassificationResult
        +inspectContamination(ImageFrame) ContaminationResult
    }

    class BinDetector {
        <<interface>>
        +detectBins(ImageFrame) List~DetectedBin~
    }

    class FrameQualityAnalyzer {
        <<interface>>
        +analyze(ImageFrame) FrameQuality
    }

    class DeviceTierPolicy {
        <<interface>>
        +tier DeviceTier
        +isEnabled(Feature) Boolean
    }

    class RuleEngine {
        +resolve(WasteMaterial, ContaminationState, Set~BinId~, CountryProfile) Disposal
    }

    class CountryProfile {
        -String isoCode
        -String regulationName
        -String regulationReference
        -List~BinDefinition~ bins
        -List~MaterialRule~ rules
        -List~InspectionRule~ inspectionRules
    }

    class BinDefinition {
        -BinId id
        -String displayName
        -String colorHex
        -DisposalRoute route
    }

    class MaterialRule {
        -WasteMaterial material
        -BinId targetBin
        -BinId contaminatedFallback
        -String justification
    }

    class InspectionRule {
        -WasteMaterial material
        -String promptKey
        -Boolean requiresInteriorView
    }

    class ClassificationResult {
        -WasteMaterial material
        -Float confidence
    }

    class ContaminationResult {
        -ContaminationState state
        -Float confidence
    }

    class FrameQuality {
        -Float sharpness
        -Float luminance
        -Boolean lensSoiling
        -Boolean objectCentered
    }

    class Disposal {
        -BinDefinition bin
        -DisposalRoute route
        -String justification
        -Boolean degradedByContamination
    }

    class ClassificationOutcome {
        -Disposal disposal
        -List~CaptureHint~ hints
        -Boolean needsUserDecision
    }

    class ClassificationRecord {
        -String id
        -WasteMaterial material
        -BinId bin
        -Long timestamp
    }

    class AuthProvider {
        <<interface>>
        +currentSession() Session
        +signIn(Credentials) Session
    }

    ClassifyWasteUseCase --> WasteClassifier : usa
    ClassifyWasteUseCase --> FrameQualityAnalyzer : usa
    ClassifyWasteUseCase --> RuleEngine : usa
    ClassifyWasteUseCase --> ClassificationOutcome : produce
    ClassifyWasteUseCase --> DeviceTierPolicy : consulta
    RuleEngine --> CountryProfile : evalúa
    RuleEngine --> Disposal : produce
    CountryProfile --> BinDefinition : define
    CountryProfile --> MaterialRule : define
    CountryProfile --> InspectionRule : define
    MaterialRule --> BinDefinition : apunta a
    WasteClassifier --> ClassificationResult : devuelve
    WasteClassifier --> ContaminationResult : devuelve
    FrameQualityAnalyzer --> FrameQuality : devuelve
    BinDetector --> BinDefinition : reconoce
    ClassificationOutcome --> ClassificationRecord : se persiste como
```

### Notas de diseño

`WasteMaterial` es un enumerado cerrado que constituye el vocabulario compartido entre el modelo entrenado y el motor de reglas. Es el único punto de acoplamiento entre ambos mundos: si se añade un material, se añade en la taxonomía de `ml/` y en el enumerado, y se actualizan los perfiles que quieran contemplarlo. Un perfil que no declare regla para un material cae en una ruta por defecto declarada en el propio perfil, nunca en una decisión cableada en código.

`DisposalRoute` modela la ruta de disposición con independencia del color: `RECYCLABLE`, `NON_RECYCLABLE`, `ORGANIC`, `HAZARDOUS`, `SPECIAL_COLLECTION`. El color y el nombre visible salen de `BinDefinition`, que sí es específico del país. Esta separación permite que la métrica de exactitud que realmente importa —acertar la ruta— sea independiente de cómo cada país pinte sus canecas.

`contaminatedFallback` en `MaterialRule` es la pieza que resuelve el caso del vaso de café: la regla declara que el cartón para bebidas va a la caneca blanca si está limpio y a la negra si está contaminado, y esa decisión es dato del perfil, no lógica del clasificador.

## Flujos principales

### CUS-003 y CUS-005 — Clasificación con inspección de contaminación

```mermaid
sequenceDiagram
    actor Usuario
    participant UI as Pantalla de cámara
    participant VM as ClassifyViewModel
    participant Q as FrameQualityAnalyzer
    participant C as WasteClassifier
    participant R as RuleEngine
    participant P as ProfileRepository

    Usuario->>UI: apunta la cámara al residuo
    UI->>VM: onFrame(frame)
    VM->>Q: analyze(frame)
    Q-->>VM: FrameQuality
    alt calidad insuficiente
        VM-->>UI: CaptureHint "acércate" / "más luz" / "limpia el lente"
        Note over UI: la indicación respeta el intervalo mínimo anti-saturación
    else calidad suficiente
        VM->>C: classify(frame)
        C-->>VM: ClassificationResult
        alt confianza < umbral
            VM-->>UI: pedir nueva toma o selección manual
        else confianza suficiente
            VM->>P: activeProfile()
            P-->>VM: CountryProfile
            VM->>R: resolve(material, UNKNOWN, availableBins, profile)
            R-->>VM: Disposal preliminar
            alt el material tiene InspectionRule
                VM-->>UI: "Apunta hacia adentro del vaso"
                Usuario->>UI: reorienta la cámara
                UI->>VM: onFrame(interiorFrame)
                VM->>C: inspectContamination(interiorFrame)
                C-->>VM: ContaminationResult
                VM->>R: resolve(material, contaminación, availableBins, profile)
                R-->>VM: Disposal definitivo
            end
            VM-->>UI: ClassificationOutcome con caneca y justificación
            UI-->>Usuario: muestra caneca, color y regla aplicada
        end
    end
```

### CUS-002 — Escaneo de canecas disponibles

```mermaid
sequenceDiagram
    actor Usuario
    participant UI as Pantalla de escaneo
    participant VM as ScanBinsViewModel
    participant D as BinDetector
    participant P as ProfileRepository
    participant Repo as BinAvailabilityRepository

    Usuario->>UI: inicia escaneo apuntando a las canecas
    UI->>VM: onFrame(frame)
    VM->>D: detectBins(frame)
    D-->>VM: List<DetectedBin> con color y confianza
    VM->>P: activeProfile()
    P-->>VM: CountryProfile
    VM->>VM: emparejar colores detectados con BinDefinition del perfil
    VM-->>UI: canecas reconocidas para confirmar
    Usuario->>UI: confirma, añade o elimina manualmente
    UI->>VM: confirmSelection(bins)
    VM->>Repo: saveAvailableBins(bins)
    Repo-->>VM: ok
    VM-->>UI: navegar a clasificación con el conjunto activo
    Note over VM,Repo: si no existe la caneca ideal, RuleEngine mapea a la<br/>disponible más conservadora y lo informa al usuario
```

### CUS-008 — Determinación de la gama del dispositivo

```mermaid
sequenceDiagram
    participant App as Arranque de la app
    participant T as DeviceTierPolicy
    participant HW as Consulta de hardware
    participant B as Micro-benchmark
    participant I as ModelProvider

    App->>T: resolveTier()
    T->>HW: RAM total, núcleos, API level, delegados disponibles
    HW-->>T: capacidades declaradas
    T->>B: ejecutar N inferencias de calentamiento
    B-->>T: latencia media real
    T->>T: combinar señales y fijar tier LOW / MID / HIGH
    T->>I: seleccionar variante de modelo para el tier
    I-->>T: modelo cargado
    T-->>App: tier activo y matriz de funciones habilitadas
    Note over T: si la latencia observada se degrada de forma<br/>sostenida en uso, el tier se recalcula a la baja
```

## Estados

### Máquina de estados de la sesión de clasificación

```mermaid
stateDiagram-v2
    [*] --> Onboarding
    Onboarding --> SeleccionPais
    SeleccionPais --> EscaneoCanecas
    EscaneoCanecas --> Listo : canecas confirmadas
    SeleccionPais --> Listo : usar canecas por defecto del perfil

    Listo --> Encuadrando : abrir cámara
    Encuadrando --> Encuadrando : calidad insuficiente, emitir indicación
    Encuadrando --> Clasificando : calidad suficiente
    Clasificando --> BajaConfianza : confianza bajo umbral
    Clasificando --> Inspeccionando : material requiere inspección interior
    Clasificando --> Resuelto : decisión directa
    Inspeccionando --> Inspeccionando : falta la vista interior
    Inspeccionando --> Resuelto : contaminación evaluada
    BajaConfianza --> Encuadrando : reintentar toma
    BajaConfianza --> Resuelto : selección manual del usuario
    Resuelto --> Listo : nueva clasificación
    Resuelto --> Historial : guardar y consultar
    Historial --> Listo
    Listo --> [*]
```

## Decisiones de arquitectura

| Decisión | Alternativas descartadas | Razón |
|---|---|---|
| Kotlin Multiplatform con `shared` sin dependencias de plataforma | Kotlin nativo puro; Flutter; React Native | Nativo puro obligaría a reescribir el motor de reglas en Swift, duplicando la fuente de verdad crítica. Flutter empujaría cámara e inferencia a platform channels justo en la ruta de latencia, con plugins de terceros sobre los delegados de aceleración. KMP comparte el dominio y deja nativo lo que debe serlo. |
| UI nativa por plataforma en vez de Compose Multiplatform | Compose Multiplatform para iOS | El objetivo estético es que cada plataforma se sienta propia. Compartir UI ahorraría código a costa de un iOS que no se siente iOS, que es precisamente lo que el producto quiere evitar. |
| Pipeline de dos etapas: material y luego contaminación | Clasificador único con clases "limpio/sucio" por material | Multiplicaría las clases y exigiría datos etiquetados por combinación. La evidencia publicada muestra que el enfoque de dos etapas corrige casi todos los casos contaminados que un clasificador base falla. Además permite ejecutar la segunda etapa solo cuando la regla lo pide, que es clave en gama baja. |
| El modelo predice materiales; las canecas salen de un motor de reglas | Entrenar un modelo por país; cablear el mapeo en la app | Un modelo por país es insostenible y exige reentrenar para escalar. Cablear el mapeo rompe el requisito de escalabilidad. Con perfiles en datos, un país nuevo es un archivo JSON. |
| Perfiles normativos como JSON versionado en recursos | Base de datos remota de reglas; constantes en código | La app debe funcionar sin conexión. JSON en recursos es offline por construcción, legible, revisable en un PR y actualizable sin migraciones. |
| LiteRT con delegados NNAPI y GPU | ONNX Runtime Mobile; MediaPipe; PyTorch Mobile | LiteRT tiene el mejor soporte de cuantización INT8 y de aceleración por hardware en el rango de dispositivos objetivo, y la ruta a iOS está cubierta. MediaPipe se evalúa solo para el detector de objeto. |
| Análisis de calidad de imagen con heurísticas, no con ML | Un modelo de calidad de imagen dedicado | Varianza del Laplaciano para nitidez, luminancia media para iluminación y diferencia entre frames para suciedad del lente son baratas, deterministas y explicables. Un modelo extra consumiría el presupuesto de latencia que necesita la clasificación. |
| Gama de dispositivo resuelta con benchmark de arranque | Lista de dispositivos; solo RAM y API level | Las listas envejecen y la RAM sola engaña. Medir la latencia real en el dispositivo concreto es la única señal que no miente. |
| Contaminación sintética a partir de datasets públicos | Recolección y etiquetado manual de dataset propio | No existe dataset público de reciclables contaminados y la recolección manual está descartada por el proyecto. La síntesis por segmentación y composición es la única vía viable. |
| Autenticación como interfaz con implementación invitado | Integrar Supabase o Firebase en v1 | La v1 es una demo sin backend. Definir el puerto ahora evita que la lógica de sesión se filtre por la app cuando llegue el backend real. |

## Reglas para no romper la arquitectura

1. `shared/` no importa nunca `android.*`, `androidx.*` ni el runtime de inferencia. Toda capacidad de plataforma entra por un puerto declarado en `shared/domain/port/`.
2. La conversión de material a caneca ocurre exclusivamente en `RuleEngine`. Ni el clasificador, ni la UI, ni los repositorios pueden decidir una caneca.
3. Ningún comportamiento se condiciona por país dentro del código. Lo específico de un país vive en su archivo de perfil.
4. La UI no llama a `inference/` ni a `data/`: pasa por un caso de uso.
5. Antes de activar cualquier función costosa se consulta `DeviceTierPolicy`. Nadie asume aceleración por hardware.
6. Los frames de cámara no se persisten, no se envían y no se registran en logs.
7. La ruta de clasificación no tiene ninguna dependencia de red. La app entera debe funcionar en modo avión.
8. Las interfaces de la sección "Contratos entre agentes" de `context-for-vibe-coding.md` son inmutables una vez fijadas en M0; cambiarlas requiere issue propia.
9. La exactitud se reporta siempre sobre un dataset no visto en entrenamiento, y siempre incluye el acierto de ruta además del top-1 de material.
