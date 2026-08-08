# CONTEXTO — RecyCol

**Este es el único documento que un agente necesita leer antes de trabajar.**
Sustituye a `context-for-vibe-coding.md`, a `ml/DATASETS.md` y a los siete
`HANDOFF-*.md` que cada agente escribió al cerrar sesión. Todo lo que sigue está
sintetizado aquí; lo que no está aquí, está enlazado desde la sección
[Documentos formales](#documentos-formales) y no se duplica.

Última consolidación: **07/08/2026**, al cerrar la **versión 1**.

> ### ✅ Renombrado BotaBien → RecyCol: terminado
>
> Ya no queda nada del nombre viejo en infraestructura viva: namespace
> (`com.recycol.*`), `rootProject.name`, base de datos y preferencias, imágenes y
> contenedores Docker, runners self-hosted, repositorio de GitHub
> (`JuanUrrego146/RecyCol`) y **carpetas en disco** (§9). Las menciones a
> `BotaBien` que quedan en este documento son **históricas a propósito** —
> describen de dónde viene algo— y no rutas vivas.

> ### 🏁 Estado: versión 1 cerrada
>
> La app clasifica de verdad en dispositivo real, con los modelos de M4, y la
> decisión visible es estable. Publicada como **release `v1.0.0`** en GitHub con
> el APK de depuración adjunto. Lo que sigue son dos frentes independientes: la
> **APK de desarrollador** (§11, encargo abierto para el siguiente agente) y la
> **fase RecyCol Entrenamiento** (§10, para cerrar la brecha de dominio del
> modelo).

---

## 1. Qué es RecyCol

Aplicación móvil Android (portable a iOS) que, usando la cámara y redes
neuronales que corren **íntegramente en el dispositivo**, dice en qué caneca va
un residuo según la norma vigente del país activo. Detecta además si un
reciclable está contaminado y degrada la decisión cuando corresponde.

El problema real que resuelve: la gente no sabe a qué caneca va cada material, y
las reglas son más sutiles de lo que parecen. Un vaso de café aparenta cartón
reciclable, pero lleva recubrimiento de polietileno y suele tener líquido dentro:
en Colombia va a la **negra**, no a la blanca. La Resolución 2184 de 2019 exige
que lo aprovechable esté «limpio y seco», y esa condición es invisible desde
fuera del objeto.

> ### ⚠️ El proyecto es COMERCIAL, no académico
>
> Esto no es un detalle administrativo: **condiciona qué datasets y qué pesos
> preentrenados pueden entrar al pipeline.** Toda licencia NC (no comercial) o
> con cadena de derechos sin acreditar queda fuera, aunque mejore las métricas.
> El registro legal vigente es [`ml/DATA_LICENSES.md`](ml/DATA_LICENSES.md), que
> manda sobre cualquier otro documento. Cualquier texto que diga «proyecto
> académico sin fines comerciales» es material obsoleto anterior al 06/08/2026.

Todo ocurre sin conexión. No se envían imágenes a ningún servidor, no hay APIs
externas y la app funciona de gama baja a gama alta habilitando funciones de
forma escalonada.

**Alcance v1 (solo Android):** selección de país y perfil normativo ·
clasificación por cámara offline · escaneo de canecas disponibles · detección de
contaminación con toma dirigida · asistencia de captura · adaptación por gama ·
justificación normativa · historial local · pantalla de login preparada sin
backend.

**Fuera de v1:** IA en la nube, backend real y cámaras fijas. La **recolección de
dataset propio** también lo estaba, y **dejó de estarlo el 08/08**: existe como
componente aparte, desplegado, en `dataApp/` (§10). No toca la app Android ni sus
invariantes — tiene su propio despliegue y su propio modelo de privacidad.

---

## 2. Cómo se trabaja aquí — reglas del enjambre

El desarrollo lo ejecutan agentes de IA en paralelo. Juan Urrego dirige, revisa e
integra. Estas reglas existen porque **cada una se aprendió rompiendo algo**.

### Git y worktrees

- **Un worktree por agente.** El clon principal
  `C:\Users\Juan\Documents\GitHub\BotaBien` es **zona neutral compartida**: su
  rama cambia debajo de ti. Nunca trabajes ni commitees ahí.
  `git worktree add ../BotaBien-<agente> <rama>`.
- **Nunca `git add -A`.** Añade por rutas explícitas. Un `add -A` ya arrastró
  trabajo sin confirmar de otro agente.
- **Una rama por issue, un PR por issue**, patrón `<agente>/S<NN>-<slug>`.
  `main` no se toca directo (protección de rama activa, también para admins).
- **`Closes #N` en inglés.** «Cierra #N» **no** cierra la issue: GitHub lo
  ignora. Ya pasó con #14.
- **Abre cada PR contra `main`.** Apilar ramas (`A ← B ← C`) hace que, si se
  fusiona «hacia dentro», los PRs intermedios queden huérfanos y se cierren sin
  fusionar — le pasó a CAM (S11–S14), a FRONT (#106) y a BINS (#76). Si de todos
  modos apilas: se aterriza de abajo arriba y al fusionar la base hay que
  reapuntar el siguiente con `gh pr edit <n> --base main`.
- La base que muestra `gh pr list` **miente**: verifica con `gh pr view`.

### Build y CI

- **No hay JDK, Android SDK ni Python en la máquina.** Todo se compila en
  contenedor, y el sufijo `-p <proyecto>` es obligatorio para no chocar por el
  lock de Gradle:
  ```bash
  docker compose -p recycol-<agente> run --rm android-build ./gradlew <tareas>
  ```
  Batería equivalente a CI:
  `:shared:allTests :shared:testing:allTests :shared:verifyPlatformIsolation :androidApp:testDebugUnitTest :androidApp:assembleDebug`
- **CI verde obligatorio antes de fusionar.** El check que satisface la
  protección de rama es **«Compilar y probar»** (workflow `CI`). Verifícalo de
  verdad, no por el rollup:
  `gh api repos/JuanUrrego146/RecyCol/commits/<sha>/check-runs`.
- **Runners propios**, self-hosted y dockerizados, sobre la misma imagen
  `recycol/android-build` que el build local. Runbook en
  [`.github/runner/README.md`](.github/runner/README.md). Levantarlos tras un
  reinicio, desde `.github/runner/`:
  ```bash
  docker compose -f docker-compose.runners.yml up -d
  gh api repos/JuanUrrego146/RecyCol/actions/runners --jq '.runners[].status'
  ```
  Dimensionado vigente (#131): **1 ejecutor de 8 GB**; el segundo (4 GB) solo en
  picos, con `--profile ola`.
- **Respaldo hospedado** si Actions no crea runs de `pull_request` (pasó el
  06/08, issue #111): `gh workflow run ci-respaldo.yml --ref <rama>` — produce el
  mismo check y satisface la protección. La rama debe contener ese workflow, así
  que fusiona `main` primero.
- **No relanzar checks en masa** (congeló la cola 90 min) ni empujar commits
  vacíos a ramas ajenas para redisparar.
- Cada run verde deja el APK: `gh run download <run-id> -n recycol-debug-apk`.

### Diagnósticos que cuesta caro repetir

| Síntoma | Causa real |
|---|---|
| Job en `failure` **sin ningún paso fallido** | OOM del contenedor contra su `mem_limit` (un pipeline en frío pide 6–7 GB). Comprueba: `docker inspect recycol-runner-1 --format '{{.State.OOMKilled}}'` |
| «Could not read workspace metadata» | Caché `kotlin-dsl` del volumen corrupta tras reiniciar Docker: bórrala y repite |
| Run muerto con `runner: NONE`, cero pasos | Infraestructura: relanzar |
| `gh pr checks` vacío | Verifica por rama y SHA: `gh run list --branch <rama>` contra `git rev-parse origin/<rama>` |
| «BLOCKED» con el check verde en el head | Lag del evaluador de merges de GitHub, no un bloqueo real |
| Docker Desktop cae bajo carga («unexpected EOF») | Reintenta antes de diagnosticar |

### Coordinación

- **Ámbito de archivos exclusivo por agente** (§4). Tocar ámbito ajeno requiere
  **issue de coordinación explícita**. `CODEOWNERS` refleja la partición.
- **Tablero de estado del enjambre: issue #123.** Publica ahí cada hito en tres
  líneas: **qué** terminaste, **dónde** está (rama, PR, ruta de artefactos si no
  viven en git) y **qué sigue**. Computación larga: heartbeat cada ~30 min con
  ETA. Lo que no esté allí ni en una rama visible, no existe.
- **Sin respuesta no hay acuerdo.** Una oferta o petición **no es una asignación
  hasta que el receptor la acepta por escrito**. Quien pide lo dice
  explícitamente y con destinatario; quien recibe **responde siempre**, aunque
  sea «no» o «lo miro en N minutos». Ignorar una coordinación dirigida a ti dejó
  dos agentes parados dos horas con un entregable construido esperando (oferta
  de CAM a ML en #21). Una coordinación **sin respuesta en ~30 min es un
  atasco**: dilo en el tablero.
- En los reportes de estado distingue siempre **hecho** / **pedido y aceptado** /
  **pedido sin respuesta**. La última es la más importante: es donde se pierde
  trabajo.
- **Regla anti-parón**: nadie termina su turno con trabajo pendiente de su
  milestone. Al terminar un PR se arranca la siguiente issue en el mismo turno.
  Nadie espera CI, notificaciones ni fusiones.
- **QA fusiona** los PRs verdes y dentro de ámbito; **QA no fusiona los suyos**.
  **Juan autoriza** toda decisión de producto, de arquitectura que afecte a
  varios agentes, y de riesgo (legal, licencias, rendimiento). Históricamente
  CORE era el interlocutor de Juan; **desde el 07/08 el orquestador coordina a
  los agentes directamente** y CORE solo guarda los contratos compartidos.
- El estado real vive en **ramas, drafts y `ml/reports/`** — nunca lo reportes
  midiendo solo `main` e issues.
- Email de commit: `200016968+JuanUrrego146@users.noreply.github.com`. El Gmail
  personal hace que el push se rechace.

### Definición de «hecho»

Una issue se cierra cuando, **todo a la vez**:

1. Compila sin warnings nuevos y `./gradlew :shared:allTests` pasa.
2. Cumple literalmente el criterio de hecho escrito en la issue.
3. No rompe ningún invariante de arquitectura (§4).
4. No degrada ningún RNF medible; si toca la ruta de clasificación, se adjunta la
   latencia medida.
5. La lógica de dominio nueva tiene pruebas unitarias.
6. Los textos visibles están en recursos de cadenas.
7. El PR referencia la issue y los RF/CUS que implementa.

---

## 3. Estado real por milestone

Fecha de corte: **07/08/2026**, tras el reinicio.

| M | Workstream | Agente | Estado |
|---|---|---|---|
| **M0** | Fundación y contratos | CORE | ✅ **Cerrado en `main`** (ampliado con #48, #49, #94) |
| **M1** | App shell y design system | FRONT | ✅ **Cerrado en `main`** — 6/6 sesiones + extras (#101 selección manual, #113 adopción de casos de uso, #127 baja confianza como flujo protagonista) |
| **M2** | Cámara y calidad de imagen | CAM | ✅ **Cerrado en `main`**. Costuras abiertas: #104 (EDGE), #105 (FRONT), #21 (ML) |
| **M3** | Inferencia y gamas | EDGE | ✅ **Cerrado en `main`** — S15–S20 y la coordinación #102 fusionadas. Quedan 2 ramas listas sin PR: `edge/coord-94-tier-preference` y `edge/coord-s27-banco-validacion` |
| **M4** | Modelos y datos | ML | ✅ **S22–S28 completos** (07/08). Modelo ganador `mid` MobileNetV3-Large: **74,2 % de ruta contra control**, desde el 61,4 % de partida. **RNF-008 sigue sin cumplirse** y la brecha restante es de dominio — la ataca la fase RecyCol Entrenamiento (§10). Ver §7 |
| **M5** | Motor de reglas y perfiles | RULES | ✅ **Cerrado en `main`** — S29–S33 + coordinación #54 |
| **M6** | Escaneo de canecas | BINS | 🔶 S34 en `main`; **S35 en PR #133** (CI verde, sin fusionar). La issue #33 **no se cierra con #133**: falta la pantalla de confirmación, que es ámbito de FRONT |
| **M7** | Persistencia, historial y auth | DATA | ✅ **Cerrado en `main`** — S36, S37, S38 |
| **M8** | Confianza, integración y QA | QA | ✅ **Integración cerrada (v1)**: inferencia real cableada, decisión estabilizada y auditoría en dispositivo real. Ver «Qué cerró la v1» |
| **M9** | Preparación iOS y demo | RELEASE | ⬜ Sin empezar. Los targets iOS de `:shared` se activan en S43 (necesita macOS); protegido mientras tanto por `:shared:verifyPlatformIsolation` |

### Qué cerró la versión 1 (PR #162)

Hasta este punto **la app entera corría sobre los fakes de `shared/testing/`**.
El runtime de inferencia de EDGE estaba escrito y probado en aislamiento desde
S18, pero su módulo de Koin nunca se había registrado, `:androidApp:inference`
nunca fue dependencia de compilación y no existía implementación Android de
`ProfileSource`. Nadie lo había notado porque cada pieza pasaba sus pruebas.

1. **Cableado real**: clasificador LiteRT sobre los `.tflite` de M4, perfiles
   normativos empaquetados como assets, detección de gama enchufada. Cero fakes
   en el APK.
2. **Contrato de entrada de los modelos**: los `.tflite` declaran
   `[1,3,lado,lado] INT8 NCHW`, no el `[1,lado,lado,3] UINT8` del contrato S15.
   Se adaptó **el preprocesado del runtime**, con paridad numérica verificada
   contra el preprocesado de referencia de ML. Ver `androidApp/inference/README.md`.
3. **Reparto por gama corregido**: con el criterio de acierto de **ruta**, la
   gama alta llevaba el peor modelo. Alta y media comparten ahora el ganador.
4. **Calidad de frame recalibrada** con capturas del propio dispositivo: los
   umbrales estaban puestos a ojo y bloqueaban casi todos los fotogramas, así
   que la app pedía «acércate» eternamente y no clasificaba nunca.
5. **Estabilización temporal de la decisión** (`ClassificationStabilizer`, §4).
6. **Cuatro bugs de interfaz** que solo aparecen probando en un teléfono, no
   compilando: la flor de recompensa nunca se dibujaba, la pregunta de suciedad
   se pisaba a sí misma entre materiales, tres superficies de cristal se
   apilaban, y los «recuadros» eran la sombra de elevación del propio cristal
   vista a través de él (#161).

### Auditoría en dispositivo — issues abiertas

Encontradas probando en un Samsung Galaxy A35 real. **Ninguna está arreglada**;
las cuatro son de FRONT salvo la de gama.

| Issue | Gravedad | Qué pasa |
|---|---|---|
| **#157** | 🔴 | Girar la pantalla reinicia la sesión hasta el onboarding: `AppNavState` usa `remember`, no `rememberSaveable` |
| **#158** | 🟠 | La detección de gama no llega a la interfaz: Ajustes promete que se mide al arrancar y no se refleja |
| **#159** | 🟡 | El resultado dice «Selección manual» aunque el material lo identificara la cámara |
| **#160** | 🟡 | Con la cámara denegada conviven «Apunta a un residuo» y «Permitir cámara» |

### PRs abiertos

| PR | Qué es | Estado |
|---|---|---|
| **#114** | S22 · pipeline de ingesta de ML (+ cadena S23–S27) | **Draft.** Al salir de draft, CORE debe revisar el diff de los 4 archivos `docker/` (ámbito CORE, versiones fijadas) |

### Salud de `main`

- **Workflow `CI` («Compilar y probar»), que es el check obligatorio: verde.**
- **Workflow `Calidad`: rojo de forma sostenida**, en `:androidApp:lintDebug`.
  Es deuda real de Android Lint, no flake, y lleva fallando desde al menos #110.
  No bloquea fusiones. Sigue pendiente.
- **Runner self-hosted `recycol-runner-1`: arriba.**
- **Disco C: 19 GB libres.** Es el margen que hay para datasets y checkpoints;
  por debajo de 15 GB conviene avisar antes de entrenar.

---

## 4. Arquitectura — dónde vive cada cosa

Kotlin Multiplatform. Un módulo `shared` que no conoce ninguna plataforma
(dominio, motor de reglas, perfiles, persistencia); cámara, inferencia e interfaz
son nativas y se conectan por puertos. Esa separación es lo que hace que iOS sea
una implementación de adaptadores y no una reescritura.

La cámara alimenta un pipeline de dos etapas: una red clasifica **material**, una
segunda inspecciona **contaminación**. El modelo nunca devuelve una caneca: la
traducción material → caneca la hace el motor de reglas contra un perfil
normativo intercambiable por país.

```
RecyCol/
├── shared/                          # KMP, SIN dependencias de Android
│   ├── domain/                      # entidades, casos de uso, puertos      → CORE
│   ├── testing/                     # fakes deterministas                   → CORE
│   ├── rules/                       # RuleEngine, perfiles, matcher canecas → RULES
│   ├── data/                        # SQLDelight, repositorios              → DATA
│   └── resources/profiles/          # perfiles por país en JSON             → RULES
├── androidApp/
│   ├── ui/                          # Compose, design system                → FRONT
│   ├── camera/                      # CameraX, calidad de imagen            → CAM
│   ├── inference/                   # LiteRT, delegados, gamas              → EDGE
│   │   └── bins/                    # detector de canecas                   → BINS
│   └── di/                          # Koin
├── ml/                              # pipeline Python de datos y modelos    → ML
├── benchmark/                       # banco de latencia                     → QA
├── .github/                         # workflows y runners                   → CORE
├── docker/                          # imágenes de build y GPU               → CORE
├── docs/  ·  plan/
└── iosApp/ · release/               # fase 2                                → RELEASE
```

### Invariantes — no se negocian

Un PR que los rompa se rechaza aunque funcione.

1. **`shared/` no conoce Android.** Cero imports de `android.*`, `androidx.*` o
   LiteRT. Toda capacidad de plataforma entra por un puerto de
   `shared/domain/port/`. Lo hace cumplir la tarea Gradle
   `:shared:verifyPlatformIsolation`, que rompe el build. Violarlo mata RNF-005.
2. **El modelo predice materiales, nunca canecas.** La conversión material →
   caneca ocurre **exclusivamente** en `RuleEngine`. Ni el clasificador, ni la
   UI, ni los repositorios deciden una caneca.
3. **Las reglas normativas viven en datos, no en código.** Todo lo específico de
   un país está en `shared/resources/profiles/<id>.json` + su entrada en
   `catalog.json`. **Prohibido `if (country == "CO")` en cualquier parte.**
4. **La UI nunca llama a `inference/` ni a `data/` directamente.** Siempre pasa
   por un caso de uso. Los `ViewModel` orquestan, no deciden.
5. **La gama del dispositivo se consulta, no se asume.** Toda función costosa
   pregunta a `DeviceTierPolicy`. Nadie asume NPU, GPU ni memoria.
6. **Las imágenes no salen del proceso.** No se escriben a disco, no se envían
   por red, no se registran en logs. El historial guarda el resultado, nunca el
   frame. Probado sobre el `.db` real: `ClassificationHistoryPersistenceTest`
   verifica que no hay columnas BLOB ni firmas JPEG/PNG en el archivo. Si alguien
   añade una columna binaria, esa prueba falla — es deliberado.
7. **Sin red en la ruta de clasificación.** La app entera funciona en modo avión.
8. **Ante la duda no se adivina**: material sin regla, o inspección exigida y no
   verificada → caneca conservadora del perfil, marcada con su `FallbackReason`.
9. La exactitud se reporta **siempre** sobre un dataset no visto en
   entrenamiento, y **siempre** incluye el acierto de ruta además del top-1.

### Contratos entre agentes — inmutables sin issue de coordinación

Puertos en `shared/domain/port/`: `WasteClassifier`, `BinDetector`,
`FrameQualityAnalyzer`, `DeviceTierPolicy`, `AuthProvider`, `ProfileRepository`,
`BinAvailabilityRepository`, `ClassificationHistoryRepository`,
`TierPreferenceRepository`. `RuleEngine` vive en `shared/rules/` (impl:
`DefaultRuleEngine`).

```kotlin
interface WasteClassifier {
    suspend fun classify(frame: ImageFrame): ClassificationResult
    suspend fun inspectContamination(frame: ImageFrame): ContaminationResult
}
interface BinDetector      { suspend fun detectBins(frame: ImageFrame): List<DetectedBin> }
interface FrameQualityAnalyzer { fun analyze(frame: ImageFrame): FrameQuality }
interface DeviceTierPolicy { val tier: DeviceTier; fun isEnabled(feature: Feature): Boolean }
interface RuleEngine {
    fun resolve(material: WasteMaterial, contamination: ContaminationState,
                availableBins: Set<BinId>, profile: CountryProfile): Disposal
}
interface AuthProvider {
    suspend fun currentSession(): Session          // v1 devuelve siempre Session.Guest
    suspend fun signIn(credentials: Credentials): Result<Session>
}
```

Casos de uso en `shared/domain/usecase/`: `ClassifyWaste` (dos pasos con
inspección), `ResolveManualDisposal`, `ScanBins`, `SelectCountry` (resetea
canecas al cambiar de país, #65), `ManageHistory`, `AdjustPerformance`,
`ConfidenceThresholds` y `StabilityThresholds` (inyectables; su calibración
es de QA).

Mientras una implementación real no exista, se trabaja contra el *fake*
determinista de `shared/testing/`. **Nadie espera a nadie.** Con el aviso que
costó la v1: que cada pieza pase sus pruebas contra fakes **no significa que
estén conectadas**. Antes de dar por hecho que algo funciona de verdad, hay que
verlo correr en un teléfono.

### Estabilización de la decisión — por qué existe

`ClassificationStabilizer` + `TrackClassificationUseCase`, en
`shared/domain/usecase/`. Es el único componente **con estado** del flujo de
clasificación y tiene **ciclo de vida por pantalla**.

Existe por un dato medido: sobre un objeto quieto, el top-1 del modelo **cambia
en el 13,8 % de los fotogramas consecutivos**. Publicar cada frame —que es lo
que hacía la pantalla— daba una decisión distinta tres veces por segundo, con
vibración a 3,3 Hz, y la respuesta del usuario duraba lo que tardaba el
siguiente fotograma.

Cuatro mecanismos, cada uno con un trabajo distinto:

1. **Permanencia mínima** (1,4 s): suelo duro contra el parpadeo. No depende de
   la cadencia de análisis, que no está garantizada.
2. **Votación por papeletas** sobre una ventana **de conteo** (no de tiempo): la
   duda de RF-023 vota como una candidata más, en vez de fabricar un segundo
   camino en la interfaz.
3. **Caducidad de la evidencia** por tiempo **y** por pasadas. Las dos: solo con
   tiempo, una cadencia lenta borra y repinta la pantalla en cada pasada; solo
   con pasadas, perder la calidad de forma sostenida no retira nada.
4. **Congelación de lo publicado**: una decisión comprometida solo la releva una
   ventana **entera y unánime**, y la del usuario, lo mismo. Que el clasificador
   insista no es motivo para contradecir a una persona.

Decisión de producto detrás del punto 4: con un modelo que se contradice a sí
mismo el 14 % de los fotogramas, **una respuesta estable que se corrige con «No
es esto» sirve más que una sucesión de respuestas honestas**. El precio es que
si acierta mal, se queda mal hasta que alguien la corrija o aparte el objeto.

### Contrato de modelos EDGE ↔ ML — congelado

Los `.tflite` **no se versionan**: se reconstruyen con el pipeline de `ml/` y se
dejan caer en `androidApp/inference/src/main/assets/models/`. Mientras no
existan, se sirve un `StubWasteClassifier` determinista y la app funciona.

| Archivo | Modelo | Entrada | Salida |
|---|---|---|---|
| `material_low.tflite` | MobileNetV3-Small INT8 | 224×224×3 RGB UINT8 | softmax sobre materiales |
| `material_mid.tflite` | MobileNetV3-Large 0.75 INT8 | 224×224×3 RGB UINT8 | softmax sobre materiales |
| `material_high.tflite` | EfficientNet-Lite2 INT8 | 260×260×3 RGB UINT8 | softmax sobre materiales |
| `contamination.tflite` | binario INT8 | 224×224×3 RGB UINT8 | softmax `[CLEAN, CONTAMINATED]` |
| `detector.tflite` | detector genérico, **opcional** | 320×320×3 RGB UINT8 | `[cajas, clases, puntuaciones, conteo]` |

1. **Orden de salida** = orden de declaración de `WasteMaterial` (etapa 1) y
   `[CLEAN, CONTAMINATED]` (etapa 2), definido en `ModelOutputOrder`. Debe
   coincidir con `ml/taxonomy/label_mapping.yaml`.
2. **Layout de entrada**: `[1, lado, lado, 3]`, RGB por filas, sin alfa, UINT8
   `[0,255]`. Una entrada FLOAT32 se declara en su `ModelSpec` con media y
   desviación.
3. Si el número de clases no cuadra con la taxonomía, el clasificador **falla con
   error explícito**; nunca mapea en silencio.
4. Cambiar nombres, tamaños u orden de clases **es cambiar el contrato**.

Banco de validación autoservicio para ML (rama
`edge/coord-s27-banco-validacion`, un solo comando):
`./androidApp/inference/validate_models.sh ml/dist/models <conjunto-eval>` —
verifica contrato, orden de clases contra el lote de referencia, **pérdida por
cuantización INT8 vs float** y latencia/memoria. Detalle en
[`androidApp/inference/README.md`](androidApp/inference/README.md).

### Política de gama

`DeviceTierPolicy` combina capacidades declaradas (techo) con un
**micro-benchmark de latencia real** (manda dentro de ese techo), con presupuesto
duro de 1,2 s para no comprometer el arranque. Cachea en `SharedPreferences`
invalidadas por fingerprint del sistema. Degrada un escalón si la latencia
observada se sostiene sobre el umbral; el ajuste manual del usuario (RF-031)
manda sobre todo y suspende la degradación automática.

| | Gama baja | Gama media | Gama alta |
|---|---|---|---|
| Clasificación por cámara | **Sí**, bajo demanda con botón | **Sí**, continua ~5 fps | **Sí**, continua ~10 fps |
| Detección del objeto | No: marco guía fijo | Detector ligero | Detector completo |
| Etapa de contaminación | Solo en captura manual dirigida | Bajo demanda de la regla | Automática |
| Escaneo de canecas | Foto única | Continuo | Continuo |
| Análisis de calidad | Nitidez y luz | Completo | Completo |

**La clasificación por cámara funciona en las tres gamas sin excepción.** Lo que
se degrada es la fluidez y las funciones auxiliares, nunca la función principal.

---

## 5. Stack y convenciones

- Kotlin 2.x, KMP, JDK 17, Gradle con version catalog en
  `gradle/libs.versions.toml` (**ámbito CORE**: entradas aditivas se toleran, el
  resto requiere issue).
- Android: Compose + Material 3, CameraX, LiteRT (`com.google.ai.edge.litert`),
  `minSdk = 26`, `targetSdk = 34`. Koin para DI, corrutinas y `Flow` (nada de
  `GlobalScope`), kotlinx.serialization, SQLDelight + DataStore.
- Entrenamiento: Python 3.11, PyTorch → LiteRT. Vive en `ml/`, aislado de la app.
- **Identificadores en inglés** (`WasteCategory`, `classifyWaste`); `camelCase`,
  `PascalCase`, `SCREAMING_SNAKE_CASE`. **KDoc, comentarios, textos de UI y
  mensajes de commit en español.**
- Clean Architecture por capas, orientación a objetos, dominio puro.
- No introducir dependencias nuevas sin justificarlo en el PR y añadirlas al
  version catalog.

### RNF siempre vigentes

| RNF | Qué exige |
|---|---|
| **RNF-001** | ≤ 2 s extremo a extremo en gama media, ≤ 4 s en baja. **Meta de diseño, no bloqueo**: si un dispositivo no llega, degrada funciones pero la clasificación sigue |
| **RNF-002** | Funcionamiento sin conexión, siempre |
| **RNF-004** | Un país nuevo = un archivo JSON. Si exige tocar Kotlin, el diseño está mal |
| **RNF-005** | `shared/` compila para JVM e iOS sin cambios |
| **RNF-008** | ≥ 85 % top-1 de material y **≥ 95 % de acierto de caneca**. **La segunda manda** |
| **RNF-009** | Estética minimalista iOS; todo componente sale del design system |
| **RNF-011** | Cero literales de texto visible en código |
| **RNF-012** | Los frames de cámara no se persisten ni se registran |

### Qué NO hacer

- Añadir funcionalidad no trazada a un RF. Si falta un requerimiento, **abre una
  issue nueva**; no lo improvises dentro de otra.
- Cambiar stack, estructura de módulos o convenciones de nombres.
- Mover lógica de negocio a la UI o a los `ViewModel`.
- Introducir red en la ruta de clasificación, «ni temporalmente para probar».
- Condicionar comportamiento por país con `if` en código.
- Guardar, cachear o loguear frames.
- Modificar los contratos sin issue dedicada.
- Implementar el backend de autenticación en v1: solo el stub y su interfaz.
- Reportar exactitud medida sobre el mismo dataset con el que se entrenó.
- Inventar personas, roles ni responsables en la documentación.

### Decisiones de diseño ya tomadas — no revisitar

- **UI**: nada de `ripple` (respuesta iOS: escala 0.96 + muelle sin rebote).
  Navegación propia (`AppNavState`, ~40 líneas) en vez de Navigation Compose. El
  color de caneca es **dato** (`BinDefinition.colorHex`), no diseño: el design
  system no conoce ningún color de caneca. **Nunca solo color** (RNF-010): color
  + nombre + `BotaRouteGlyph`. Tokens y componentes en
  [`docs/design-system.md`](docs/design-system.md) — léelo antes de tocar una
  pantalla.
- **Login v1 es plomería, no barrera.** Siempre `Session.Guest`, sin red,
  `AuthUnavailableException` determinista. El proveedor previsto para v2 es
  Supabase y **la capa no lo sabe**: sustituirlo es registrar otro `AuthProvider`
  en Koin.
- **DataStore no puede vivir en `shared/`** (RNF-005): entra por el puerto
  `KeyValueStore` con implementación en `androidApp/data/`.
- **El perfil activo degrada, no revienta**: si el país persistido desaparece del
  catálogo, el perfil pasa a `null` y la app vuelve al onboarding.
- **Composición temporal sobre fakes**: `AppDependencies.fakeAppDependencies()`
  cablea los casos de uso reales sobre `shared/testing/` y `:shared:testing` entra
  en el APK **a propósito y de forma temporal**. Sale cuando DATA (S36/S37) y
  EDGE (S18) se cableen por Koin, **sin tocar pantallas**.
- **Trampas de Kotlin/Gradle**: prohibido `vararg` de value classes (`BinId`);
  fusionar `main` puede romper `libs.versions.toml` **sin conflicto textual**
  (verifica `git diff <rama> origin/main -- gradle/libs.versions.toml`); al
  añadir una caneca a un perfil hay pruebas que afirman su número
  (`ColombiaProfileTest`, `ProfileResourcesTest`).

---

## 6. Decisiones de producto de Juan

Están tomadas. No se re-discuten sin él.

1. **`ELECTRONIC` entra en v1 con ruta a punto de recolección especial**, igual
   que las pilas. **No hay detección automática**: se llega por selección manual
   o por desambiguación de baja confianza (CUS-006). Motivo: ninguno de los
   datasets del inventario tiene clase de aparatos eléctricos (issue #54).
2. **Frase aprobada del aviso de caneca ausente:**
   > «No hay {ideal} disponible; usa {assigned}.»

   Es plantilla del perfil (`unavailableBinNotice`) que el motor renderiza con
   los nombres visibles — no un literal en código.
3. **Regla de inspección del vaso de café**: el cartón para bebidas exige vista
   interior. Limpio → caneca blanca; contaminado → negra. Es dato del perfil
   (`contaminatedFallback` en `MaterialRule`), no lógica del clasificador.
4. **La caja de pizza exige inspección del interior igual que el vaso de café**
   (aprobado el 07/08, PR #134, ya en `main`). Clave de recurso pendiente en
   FRONT: `inspection.show_box_interior`.
5. **Perfiles normativos por país + institución.** El catálogo modela
   **país → institución**, no solo país. Hoy existen `co.json` (Resolución 2184
   de 2019, tres canecas), `co-gtc24.json` (GTC 24) y `es.json`. S33 lo demostró:
   el commit que añade España y GTC 24 **no toca una línea de Kotlin**.
   *Limitación conocida*: `ProfileRepository.setActiveProfile(isoCode)` no puede
   activar variantes institucionales porque `co` y `co-gtc24` comparten
   `isoCode`. Propuesta registrada en #48 (seleccionar por *id de perfil*). No
   bloquea la v1, que usa los perfiles por defecto.
6. **Los destinos `SPECIAL_COLLECTION` están exentos** de la restricción por
   canecas disponibles (un punto posconsumo no es una caneca del entorno) y **se
   excluyen del escaneo**: ni se detectan por color, ni se añaden a mano, ni
   entran al «omitir escaneo».
7. **Baja confianza es el flujo protagonista, no la excepción.** Ante la brecha
   del modelo con residuos reales, la app asume la duda en primera persona («Me
   cuesta identificarlo desde aquí») y **nunca culpa al usuario ni a su foto**.
   Mantén ese tono.
8. **Garbage v2 se usa pese al riesgo legal** (decisión del 06/08) para no frenar
   el camino crítico — pero **bloquea el lanzamiento comercial** hasta revisión
   legal. Ver §8.
9. **Plan B de contaminación activado** (07/08, tras la evidencia de S26 en §7).
   La etapa 2 automática se sustituye por **una pregunta al usuario** («¿está
   sucio? ¿tiene grasa o restos?»), y **solo para cartón y papel**, que es donde
   la contaminación es irreversible. **Plástico, vidrio y metal no preguntan
   nada**: se enjuagan y se reciclan igual.

   Lo que **no** cambia: el motor de reglas (la regla vive en el perfil, en
   `contaminatedFallback`), ni el contrato EDGE (`inspectContamination` sigue
   existiendo). Lo único que cambia es **quién rellena `ContaminationState`**.
   Encaja con la decisión 7 y con que en gama baja la etapa 2 ya era solo captura
   manual dirigida: el plan B alinea las tres gamas.

   Ventaja no menor: elimina el falso «limpio» silencioso, que es el error caro
   —manda un reciclable sucio a la blanca sin que nadie se entere—. El usuario sí
   sabe si su vaso tiene café dentro.

---

## 7. ML — estado y hallazgos (lo más valioso del proyecto)

M4 es el **camino crítico**. Los artefactos pesados (`ml/data/` 2,0 GB,
`ml/runs/` 94 MB, `ml/reports/`) **no están en git por diseño**: viven en el
worktree `BotaBien-ml` y **no se pueden regenerar sin volver a descargar y
entrenar**.

### Pool de datos

Partición determinista con semilla fija, deduplicación perceptual (pHash) sobre
la unión de fuentes de entrenamiento antes de particionar:

> **train 17 176 · val 3 088 · control RealWaste 4 752**

| Dataset | Rol | Nota |
|---|---|---|
| **Garbage Dataset v2** (~13,3k) | Entrenamiento, **columna vertebral** (~70 % del pool comercial) | Riesgo legal abierto (§8) |
| **TrashNet** (2 527) | Entrenamiento, complemento | MIT. Fondo blanco uniforme, lejos del dominio móvil |
| **TACO** (~1 500 img / 4 784 anotaciones) | Entrenamiento vía recortes de bbox | CC BY 4.0 (paquete Zenodo). **Única fuente de `BEVERAGE_CARTON`** |
| **RealWaste** (4 752) | 🔒 **CONTROL — nunca entrena** | CC BY 4.0. Residuos reales degradados fotografiados en un relleno sanitario |
| Clothing Dataset (CC0), Fresh & Rotten Fruits (CC BY), Open Images V7 (filtrado) | Refuerzo de clases débiles | `TEXTILE`, `ORGANIC`, `METAL`, `BEVERAGE_CARTON` |
| ~~Garbage Classification 12c~~ · ~~ZeroWaste~~ | ❌ **Excluidos** | Imágenes © autores originales / CC BY-**NC**. Incompatibles con uso comercial |

Ningún dataset se mezcla sin pasar por `ml/taxonomy/label_mapping.yaml`. Toda
fuente se registra en [`ml/DATA_LICENSES.md`](ml/DATA_LICENSES.md) **antes** de
usarse.

### Métricas contra control — el hallazgo central de M4

Todas medidas sobre RealWaste (4 752 imágenes jamás vistas). Reporte completo en
`ml/REPORTE_METRICAS.md`, que llega a `main` con el PR #114.

| Run | val ruta | **control material** | **control ruta** |
|---|---|---|---|
| baseline `low` (sin v2) | 98,4 % | 39,3 % | 65,9 % |
| `full-v2` (con v2 entero) | 97,7 % | 42,1 % | **61,4 %** |
| `low` sin `trash` | 98,1 % | 46,2 % | 70,6 % |
| **`mid` sin `trash` — GANADOR** | 98,6 % | **50,6 %** | **74,2 %** |
| `high` sin `trash` (EfficientNet-B2) | **98,7 %** | 49,3 % | 69,8 % |
| `low` sin `trash` + palancas de coste | 97,6 % | 41,8 % | 63,6 % |

**La val interna no predice el control.** Pasó tres veces: `full-v2` (mejor val,
peor control), EfficientNet-B2 (**la mejor val de todo el proyecto y el peor
control de las tres variantes finales**) y la etapa 2 de contaminación (94 % en
sintético, inservible en dominio real). **Cualquier decisión tomada solo con val
interna es sospechosa por defecto.**

**RNF-008 (≥85 % material, ≥95 % ruta) no se cumple.** La brecha restante es de
**dominio**, y está demostrado: el INT8 no la causa (§ export) y la arquitectura
tampoco (más capacidad la empeora).

> ### ⚠️ La varianza entre runs idénticos es 2,16 pp de ruta
>
> Dos ejecuciones de la **misma** configuración dieron 68,5 % y 70,6 %. Antes era
> invisible porque `train_material.py` sembraba la augmentación con `hash()` de
> Python, **aleatorizado por proceso** salvo `PYTHONHASHSEED` (que la imagen no
> fija). Corregido con md5 el 07/08.
>
> **Regla: una diferencia menor de ~2 pp entre dos runs no significa nada.**
> Afecta retroactivamente a `ml/runs/sweep_summary.md`, cuyo «ganador» aventajaba
> al segundo en 0,13 pp: **ese ganador nunca estuvo establecido**, y de hecho la
> evaluación contra control lo desmintió.

### Causa raíz: la carpeta `trash` de Garbage v2 está envenenada

Auditoría cruzada de RULES sobre `label_mapping.yaml` y las matrices colapsadas a
canecas con el perfil colombiano (issue #23). El mapeo **no tiene errores**:
`trash → RESIDUAL` es **fiel a la etiqueta de origen y aun así dañino**.

Esa carpeta está llena de **envases sucios y deformados** — visualmente
PLASTIC/CARDBOARD/GLASS, o sea **caneca blanca** — etiquetados RESIDUAL, o sea
**negra**. El modelo aprende «envase degradado ⇒ RESIDUAL». Y como el control es
íntegramente dominio degradado, dispara reciclables a la caneca negra.

Masa de error en el control, colapsada a canecas:

| | baseline | full-v2 | Δ |
|---|---|---|---|
| **blanca → negra (caro)** | 447 | **866** | **+419** |
| blanca acertada | 2 637 | 2 131 | −506 |
| verde acertada | 124 | 270 | +146 |
| negra acertada | 372 | 503 | +131 |

v2 compró +17 pp de ORGANIC y +15 pp de RESIDUAL pagando entre 24 % y 35 % de
cada corriente blanca hacia la negra. **Eso explica íntegro el −4,5 pp de ruta.**
Errores totales −134, pero **errores caros +229** (1 619 → 1 848).

Se excluye de forma **declarativa**, sin tocar el manifiesto de S22 (para no
romper la reproducibilidad de la partición):
`--exclude garbage_dataset_v2:RESIDUAL` (350 filas en train, 51 en val).

### Optimizar RUTA DE CANECA contra control, no top-1

**El 44 % de los errores de material son gratis.** PLASTIC ↔ METAL ↔ GLASS ↔
PAPER ↔ CARDBOARD caen todos en la caneca blanca; TEXTILE ↔ RESIDUAL, todos en la
negra. Por eso convivían top-1 39,3 % y ruta 65,9 %: **el modelo era mejor de lo
que su top-1 sugería.**

Lo que el usuario sufre son las **confusiones caras**: cualquier cosa ↔ ORGANIC,
el cruce blanca ↔ negra, y `BEVERAGE_CARTON` confundido con `CARDBOARD` (se salta
la inspección del vaso: la contaminación no se detecta y termina en blanca).

De ahí dos palancas — `--route-cost` (pérdida sensible al coste de caneca) y
`--select route-macro` (checkpoint por ruta promediada por clase). **Se probaron
juntas el 07/08 y empeoran: −5,9 pp, 2,7 veces la varianza.** Suben el error caro
de ~505 a 734, justo lo que venían a evitar.

Explicación probable: optimizan la ruta **en el dominio de entrenamiento**, donde
ya está al 97–98 % y no queda margen; el término solo rigidiza el modelo
alrededor de las fronteras del dominio limpio y se paga al generalizar. **No se
descarta la idea, se descarta esa configuración** — un peso de 0,1 y el `select`
por separado siguen sin probar, y con ±2,16 pp cada pregunta cuesta dos runs.

**La receta final es la exclusión sola**: `--exclude garbage_dataset_v2:RESIDUAL`,
sin palancas.

**Publicar siempre la matriz colapsada por caneca** junto a la de material.
`evaluate_control.py` ya la emite. Sin ella las decisiones se toman a ciegas.

### 🔒 El control no se toca. Nunca.

RealWaste **no entrena, no ajusta umbrales y no elige checkpoints**. Es la única
evidencia de generalización que queda; en cuanto se use para seleccionar algo,
deja de serlo. Toda selección se hace sobre val interna.

### S26 · contaminación: entrena bien y no transfiere

Relanzado el 07/08 tras perderse en el crash de Docker. Separa los pares
sintéticos casi perfectamente —**94,0 % de exactitud**, umbral 0,62 con recall
92,2 %— y **declara limpio el 98,75 % de RealWaste**, que son residuos reales
degradados de un relleno sanitario.

| Conjunto | Marcado CONTAMINATED |
|---|---|
| val limpia de estudio | 0,00 % |
| control RealWaste (degradado real) | **1,25 %** |

**Por qué no transfiere.** La causa principal no es la mancha: es **el diseño del
par**. Cada par limpio/sucio es *la misma foto* con y sin mancha superpuesta, así
que la tarea se reduce a «¿hay un parche añadido?» — trivialmente separable. El
modelo nunca tuvo que aprender qué aspecto tiene un objeto sucio en términos
absolutos. Encima la señal es del tipo equivocado: la síntesis compone un blob
localizado, opaco (alfa 0,80–0,97) y de color saturado dentro de la máscara de
U²-Net, dejando el fondo intacto; la suciedad real es **decoloración global,
pérdida de transparencia, deformación, arrugas, restos adheridos con textura,
humedad y fondo de vertedero**. Nada de eso se reproduce.

**Palancas si se retoma**, por retorno: (1) romper la simetría del par —que
limpio y sucio no vengan del mismo objeto—, que es lo más barato y el mejor
diagnóstico: si la exactitud sintética se desploma, confirma que el 94 % era el
atajo; (2) bajar el alfa a 0,3–0,7; (3) añadir degradación **global** además de
la mancha; (4) componer sobre fondos degradados. **Pero ninguna es medible sin un
conjunto real con etiqueta limpio/sucio**: la métrica sintética ya demostró
mentir. Ese mini-set es la condición para iterar aquí, y es justo lo que RecyCol
Entrenamiento (§10) debe capturar.

### S27 · export INT8: la brecha no es de cuantización

Los cuatro artefactos del contrato salen: **18,0 MB** de 150 de presupuesto.

| Variante | float → INT8 top-1 | float → INT8 ruta |
|---|---|---|
| `low` | 46,2 % → **15,9 %** | 70,6 % → 61,1 % |
| `mid` | 50,6 % → 49,4 % | 74,2 % → **74,4 %** |
| `high` | 49,3 % → 47,3 % | 69,8 % → 67,7 % |

**`mid` cuantiza sin perder ruta: la brecha contra control NO la causa el INT8.**
Era la pregunta que la separación float/INT8 venía a responder, y queda cerrada:
es brecha de **dominio**.

**Problema nuevo: `low` pierde 30 pp de top-1 al cuantizar.** Con el mismo
pipeline para las tres variantes, que dos aguanten y una colapse señala al
modelo: MobileNetV3-Small cuantiza mal (hard-swish y bloques SE). **Afecta justo
a la gama baja.** Opciones: QAT, cuantización por canal más agresiva, o servir a
la gama baja el modelo de gama media si la latencia lo permite.

> ⚠️ **Los `.tflite` no cumplen el contrato de entrada de S15 y no se pueden
> cablear tal cual.** Declaran `[1, 3, lado, lado] INT8 (NCHW)`; el contrato exige
> `[1, lado, lado, 3] UINT8`. El **orden de salida sí es correcto**. Hay que
> reexportar con firma UINT8 NHWC o declarar esta en el `ModelSpec` de EDGE:
> requiere issue de coordinación.

**Deuda de herramienta**: `ai-edge-torch` se renombró a `litert-torch`; el paquete
viejo instalable es un shim vacío y sus versiones anteriores fijan un `tf-nightly`
que PyPI ya purgó. `export_litert.py` lo resuelve con tres fallbacks
(`litert_torch`, `torchao.quantization.pt2e`, `torch.export.export`) y **exige
torch ≥ 2.11**, que se instala en el contenedor de export sin tocar el de
entrenamiento (que sigue en 2.6 y no debe moverse: invalidaría la comparabilidad
de lo ya entrenado).

### Lo que M4 dejó sin cerrar

1. **RNF-008 no se cumple** (50,6 % / 74,2 % frente a 85 % / 95 %). Brecha de
   dominio: la ataca §10.
2. **La gama alta llevaría hoy el peor modelo.** Decisión de contrato pendiente.
3. **Firma de entrada de los `.tflite`** — coordinación con EDGE.
4. **`low` inutilizable en INT8** — afecta a la gama baja.
5. **RESIDUAL es la clase más débil** (2,8 % top-1, 37,2 % ruta): perdió 350
   ejemplos al excluir `trash`. El error va en la dirección menos grave (residuo
   señalado como reciclable), pero es deuda.
6. **`frame_quality_gate.py` de CAM sigue sin usarse** para caracterizar la
   degradación del control.

Coste medido en la RTX 3060 Ti: `low` ~35 min, `mid` ~40 min, `high` ~45 min por
run; evaluación de control ~3 min; export completo ~5 min. VRAM pico: 610 MB
(`low`), 1 585 MB (`mid`), 3 122 MB (`high` con batch 32 — con 64 no cabe).

### Lección del orquestador que se cayó

`m4_final.ps1` encadenaba reentrenamiento → evaluación → export → reporte sin
intervención, y **perdió ocho horas de GPU sin entrenar nada**. Docker Desktop se
cayó; `docker ps` empezó a devolver **error** en vez de una lista, y una salida
vacía por error es indistinguible de «no hay contenedores». El bucle de espera
interpretó «GPU libre» y siguió. Con `$ErrorActionPreference = "Continue"` y **sin
comprobar un solo exit code**, los diez pasos siguientes fallaron al instante
contra el daemon muerto: la cadena se «completó» en 5 segundos y escribió
`M4-FINAL-COMPLETO` en el log. Los errores reales solo estaban en el `.err.log`,
que nadie miraba.

**El orquestador nuevo debe comprobar el exit code de cada paso, distinguir
«Docker caído» de «GPU libre», y escribir FALLO en el log que sí se vigila.**

### Entorno de ML

**Docker es el único entorno.** No hay Python local.
`docker compose -p recycol-ml` (CPU) o el overlay
`docker-compose.gpu.yml -p recycol-ml-gpu`. `shm_size` está acotado a 2 GB **a
propósito**: subirlo invita al OOM killer de la VM de WSL2, que ya tumbó el
barrido una vez. Los runners de CI y los entrenamientos comparten la RAM de esa
VM (issue #128): no los corras a la vez.

### Sin usar todavía

- `frame_quality_gate.py` de CAM, para caracterizar la degradación del control y
  ver si el filtro de calidad rescataría parte de la brecha.
- **El banco de validación de EDGE**: corre como `connectedDebugAndroidTest` y
  **exige un dispositivo o emulador Android conectado**, que esta máquina no
  tiene. La parte que no depende de hardware —la pérdida por cuantización— se
  midió con `eval/evaluate_tflite.py` sobre el intérprete de LiteRT. Lo que sigue
  necesitando hardware real es **latencia y memoria por gama**: es S41 de QA.

---

## 8. Riesgos abiertos

| Riesgo | Estado |
|---|---|
| 🔴 **LEGAL — Garbage Dataset v2 sin cadena de derechos acreditada** (issue **#77**) | **Bloquea el lanzamiento comercial, no el desarrollo.** La ficha de Kaggle dice MIT y el paper dice CC BY 4.0 — ambas permiten uso comercial, pero la inconsistencia ya es señal de gestión informal, y parte del contenido viene de «repositorios públicos y web scraping curados»: la declaración del autor solo vale para lo que era suyo. **Ningún modelo entrenado con él puede publicarse comercialmente sin revisión legal previa.** Aporta ~70 % del pool: excluirlo sin reemplazo hace inalcanzable RNF-008 |
| 🟠 **Salida al riesgo legal: dataset propio** | **Construida y desplegada** el 08/08 (§10): RecyCol Aporta recoge fotos con cesión explícita y versionada. Falta lo único que no depende del código — que la gente aporte. Estimación original, aún válida como referencia de esfuerzo: Completo (11 clases, 5 500–11 000 fotos): 25–90 h de captura + 15–20 h de QC. **Quirúrgico (solo `BEVERAGE_CARTON`, 300–500 fotos): 3–5 h** — máximo retorno por hora, recomendado hacerlo pronto porque ninguna fuente pública apta cubre bien esa clase |
| 🔴 **El caso estrella no es verificable** | RealWaste **no contiene `BEVERAGE_CARTON`, `BATTERY` ni `ELECTRONIC`**. Hoy el diferenciador del producto — el vaso de café contaminado — no tiene control de dominio real. RULES dejó especificado un mini-set propio de ≈400 fotos, **de evaluación exclusivamente, jamás de entrenamiento**. **Lo resuelve §10** |
| ✅ ~~La contaminación sintética puede no transferir~~ | **Materializado y cerrado con decisión** (07/08): 94 % en sintético y **98,75 % de RealWaste marcado como limpio**. Plan B activado — pregunta al usuario, solo cartón y papel (decisión 9). Diagnóstico y palancas en §7; captura prioritaria en §10 |
| 🟠 **Los datasets públicos no generalizan al móvil real** | **El riesgo dominante una vez cerrado M4.** Mitigado en parte —de 61,4 % a 74,2 % de ruta contra control— pero **RNF-008 sigue sin cumplirse** y ni la arquitectura ni la cuantización explican lo que falta. **La solución de fondo es §10** |
| 🟠 **`low` inutilizable en INT8** | Pierde 30 pp de top-1 al cuantizar (§7) y es **la gama baja**. Opciones: QAT, cuantización por canal, o servir el modelo `mid` a gama baja si la latencia lo permite |
| 🟠 **Los `.tflite` no cumplen la firma de entrada del contrato** | Declaran `[1,3,lado,lado] INT8 NCHW`; S15 exige `[1,lado,lado,3] UINT8`. **No se pueden cablear tal cual.** El orden de salida sí es correcto. Coordinación con EDGE |
| 🟠 **La gama alta llevaría el peor modelo** | EfficientNet-B2 rinde 4,4 pp por debajo de MobileNetV3-Large contra control pese a tener la mejor val. Cambiarlo toca el contrato congelado de S15 |
| 🟠 **Pesos preentrenados** | «APTO con nota»: las licencias de distribución (BSD-3/Apache 2.0) permiten uso comercial, pero hay debate jurídico no resuelto sobre pesos entrenados sobre datasets solo-investigación (ImageNet, DUTS-TR). Práctica de industria: utilizables. **La aceptación es decisión de Juan** |
| 🟡 **`Calidad` rojo en `main`** | 9 errores de Android Lint sin atender. No bloquea fusiones pero es deuda visible |
| 🟡 **Costura CAM ↔ EDGE (#104)** | **Bloquea la integración real.** CameraX entrega `LumaImageFrame` (solo luma); el clasificador exige `PixelAccessFrame` con `readArgbPixels()` / `readArgbRegion()`. Sin esa conversión YUV→ARGB, la clasificación no puede consumir el flujo de cámara. Lo mismo bloquea al detector de canecas (#108) |
| 🟡 **Infraestructura** | Disco C: al 87 %; RAM de la VM de WSL2 compartida entre runners y GPU (#128); caché de AGP corrupta en un runner (#130) |

### Otras coordinaciones abiertas

- **#105 · CAM ↔ FRONT** — `HintPresenter` (FRONT) y `CaptureHintEngine` (CAM)
  implementan la misma política y divergen: intervalo (4 s vs 3 s), retiro
  (permanencia mínima vs inmediato) y —lo importante— **falta la supresión por
  confianza suficiente (RF-018) en FRONT**. CAM se ofreció a hacer el cambio
  (~30 líneas) dejando `HintPresenter` como envoltorio que delega en el motor.
  **La oferta está negociada: acéptala.** Decisión de producto pendiente: 3 s o
  4 s, a fijar por parámetro en un solo lugar junto con QA.
- **#126 · Top-K en el contrato del clasificador** — `WasteClassifier` devuelve
  una única hipótesis; la desambiguación necesita 2–3 candidatos. **La UI ya está
  lista** (`ManualSelectionSheet` acepta `candidates: List<WasteMaterial>`):
  cuando EDGE emita el top-K que LiteRT ya calcula, solo cambia la línea que arma
  la lista.
- **#100 · `ScanBinsUseCase`** — debería excluir los destinos
  `SPECIAL_COLLECTION` del emparejamiento por color, como ya hacen el matcher y
  `BinSelection`.
- **#107** — colocación del matcher de color en `shared/rules/bins`.
- **#33** — pantalla de confirmación de canecas (FRONT). La lógica está lista.
- **Criterios sin cerrar que QA debe verificar**: **#18** (latencia por gama: no
  hay números reales) y **#7** (los tres ajustes aún no persisten entre
  reinicios).

---

## 9. Carpetas en disco

Al cerrar la v1 (07/08) **se consolidó todo en una sola carpeta**. En
`C:\Users\Juan\Documents\GitHub\` queda:

| Carpeta | Qué es |
|---|---|
| `RecyCol` | **Único clon del proyecto**, en `main`. Aquí está todo: código, datasets de ML, checkpoints y modelos exportados |

Antes había cuatro (`BotaBien`, `BotaBien-front`, `BotaBien-ml`, `BotaBien-qa`):
el clon principal y tres worktrees de agentes. Se eliminaron los worktrees —
todos limpios y con sus ramas en `origin`, así que no se perdió ni un commit— y
se renombró el clon principal.

**Lo que no está en git se conservó moviéndolo, no copiándolo:**

| Ruta dentro de `RecyCol` | Qué es | Por qué importa |
|---|---|---|
| `ml/data/` | Datasets (≈2,0 GB) | No se regeneran sin volver a descargar de Kaggle |
| `ml/runs/` | Checkpoints de entrenamiento | Horas de GPU |
| `ml/dist/models/` | Los cuatro `.tflite` exportados | Son los que la app empaqueta |
| `ml/reports/logs/` | Registros de entrenamiento | Trazabilidad de las métricas de §7 |
| `androidApp/inference/src/main/assets/models/` | Copia de los `.tflite` que entra en el APK | Sin ellos la app arranca pero no clasifica |

**Nada de eso está en git y nada de eso se regenera solo.** Antes de borrar o
mover esta carpeta, respáldalas.

### El dataset propio NO está en disco: está en Azure

Las fotos que aporta la gente por [RecyCol Aporta](#10-recycol-aporta--en-producción)
no viven ni en git ni en `ml/data/`. Viven en **una sola cuenta de
almacenamiento**, `strecycolaporta94b924`, dentro del grupo `rg-recycol-aporta`:

| Qué | Dónde |
|---|---|
| Las fotos | Blob Storage, contenedor `captures`, como `MATERIAL/aportante/captura.jpg` |
| Las etiquetas y metadatos | Table Storage, tabla `captures` |
| Aportantes, contadores y cola de moderación | Table Storage: `contributors`, `counters`, `pendingreview` |

**Para descargarlo todo**, con `az login` hecho y desde la raíz del repositorio:

```bash
bash dataApp/infra/export.sh
```

Deja las imágenes y `manifest.csv` en `ml/data/recycol_aporta/`, más un
`RESUMEN.txt` con los recuentos y los avisos. **No hace falta `azcopy`** —no está
instalado en la máquina— ni sesión abierta en la aplicación web. Procedimiento
completo y trampas en
[`dataApp/docs/DESCARGAR-DATASET.md`](dataApp/docs/DESCARGAR-DATASET.md).

> **Ese script es también la copia de seguridad.** Si se borrara el grupo de
> recursos, o si Microsoft suspendiera la suscripción de estudiante, lo único que
> sobrevive es lo que ya esté en disco.

### Límites de la suscripción de Azure — caros de redescubrir

La suscripción del proyecto es **Azure for Students**, y su política **solo**
permite las regiones `southcentralus, chilecentral, canadacentral, eastus,
northcentralus`. Comprobado a base de chocar, el 08/08:

| Lo que no se puede | Qué pasa | Qué se hizo en su lugar |
|---|---|---|
| **Cosmos DB** | Rechaza crear la cuenta en las cuatro regiones permitidas, con capa gratuita, sin servidor y aprovisionada. Dice «alta demanda»; es un tope de la suscripción y levantarlo son días de trámite | Los metadatos van en **Table Storage**, en la misma cuenta que las fotos. Encaja mejor: su clave de partición es literalmente `contributorId` |
| **Azure Static Web Apps** | Solo existe en `centralus, eastus2, westus2, westeurope, eastasia`. **La intersección con lo permitido es vacía** | Una **aplicación de funciones** sirve la web y la API desde el mismo origen |
| **Plan de consumo Linux** | Se crea sin errores, queda en «Running» y devuelve 503 para siempre, también su sitio de despliegue | Plan de consumo **Windows**, que funcionó a la primera en la misma región |

Antes de crear cualquier recurso nuevo, comprobar la lista vigente:

```bash
az policy assignment list --query "[0].parameters.listOfAllowedLocations.value"
```

Y el CLI de Azure está instalado pero **fuera del PATH**:
`export PATH="/c/Program Files/Microsoft SDKs/Azure/CLI2/wbin:$PATH"`.

### Un agente nuevo

Ya no hace falta un worktree por agente: **se trabaja en `RecyCol` directamente,
en una rama propia.** El esquema de un worktree por agente tenía sentido con
siete agentes en paralelo; con uno o dos a la vez solo multiplicaba copias de
2 GB y caches de Gradle.

```bash
cd C:/Users/Juan/Documents/GitHub/RecyCol
git switch -c <agente>/S<NN>-<slug> origin/main
```

Si alguna vez vuelven a coincidir varios agentes a la vez, el patrón de worktree
sigue siendo válido — pero el que lo cree es responsable de borrarlo al
terminar, y **jamás debe apuntar a la carpeta que contiene `ml/data`**.

---

## 10. RecyCol Aporta — en producción

> ### 🟢 Construido y desplegado el 08/08/2026
>
> **https://func-recycol-aporta-w94b924.azurewebsites.net**
>
> Vive en [`dataApp/`](dataApp/). No es una APK: acabó siendo una **web**, porque
> así se difunde con un enlace y contribuye cualquiera desde el navegador del
> móvil sin instalar nada. Todo lo demás de esta sección sigue vigente — son las
> recomendaciones con las que se construyó.
>
> **Dónde están los datos y cómo bajarlos: §9.** El código en
> [`dataApp/README.md`](dataApp/README.md).

Es **la solución directa a los dos problemas que M4 no pudo cerrar**, y las
recomendaciones técnicas de abajo salen de haber chocado con ellos.

Al detectar un objeto le toma la foto, se la muestra a la persona y le pregunta
«¿qué es esto?». La pareja foto + etiqueta se guarda en Azure.

**Lo que cambió respecto a lo previsto aquí, y por qué:**

- **La aplicación pide la clase que falta** («busca un vaso de café») en vez de
  que el modelo proponga y la persona corrija. Los `.tflite` son INT8 con firma
  NCHW y no corren en navegador; además el modo misión resuelve el equilibrio por
  clase, que esta misma sección dice que pesa más que el volumen. El flujo de
  «el modelo propone» queda para cuando ML exporte a ONNX, y entonces cada
  corrección será aprendizaje activo.
- **Hay cuentas, opcionales.** Se añadieron el 07/08 a petición de Juan para
  hablar con profesores de la UMNG y que den puntos por aportar. Entrar con el
  correo institucional **acredita** la pertenencia; declararla desde otra cuenta
  la deja como «declarado», y el informe distingue las dos. Aportar sin cuenta
  sigue siendo el camino por defecto.
- **Consentimiento versión 2.0**, en
  [`dataApp/docs/CONSENT-v2.md`](dataApp/docs/CONSENT-v2.md). La 1.0 prometía que
  no se pedía nombre ni cuenta y dejó de ser cierta; nunca llegó a producción.

### Por qué resuelve exactamente lo que bloquea a M4

1. **Ataca la brecha de dominio, que es la única que queda.** El modelo da 98,6 %
   de ruta en su propio dominio y 74,2 % contra residuos reales. Esa distancia no
   es de arquitectura (más capacidad la empeora, §7) ni de cuantización (`mid`
   cuantiza sin pérdida, §7): es que **los datasets públicos son fotos de estudio
   y la app se usa con un móvil sobre basura real**. RecyCol Entrenamiento produce
   fotos del dominio exacto de destino. Es la palanca de mayor retorno que queda.
2. **Disuelve el riesgo legal de raíz.** Garbage v2 aporta ~70 % del pool y
   **bloquea el lanzamiento comercial** (#77). Datos propios con consentimiento
   explícito tienen cadena de derechos limpia. No hay que reemplazar el 70 % de
   golpe: cada imagen propia reduce la dependencia.
3. **Cubre lo que ningún dataset público cubre.** `BEVERAGE_CARTON` sigue con
   ~100 imágenes y es **el caso estrella del producto**; `ELECTRONIC` está a cero
   y por eso entró en v1 solo por selección manual (decisión 1). Ni RealWaste los
   contiene, así que hoy **el diferenciador del producto no es ni siquiera
   verificable**.
4. **Es la única vía para reactivar la etapa 2 de contaminación.** Ver abajo.

### Qué capturar además de la foto y la etiqueta

Una foto con etiqueta sirve para entrenar material. Sin lo demás, **se pierde la
mitad del valor** y no se puede diagnosticar nada cuando el modelo falle.

| Campo | Por qué | Prioridad |
|---|---|---|
| **Estado de contaminación** (limpio / con restos / con líquido / con grasa) | **Lo más valioso de todo.** Es exactamente lo que la síntesis no logró replicar (§7) y no existe en ninguna fuente pública. **Prioritario en cartón y papel**, que es donde el plan B pregunta y donde la contaminación es irreversible | 🔴 máxima |
| **Etiqueta de material** | El objetivo primario | 🔴 máxima |
| **Consentimiento explícito y su versión** | Sin esto los datos **no son utilizables comercialmente** y se repite el problema que se venía a resolver | 🔴 máxima |
| Foto **sin recortar** + recorte/bbox si lo hubo | Permite reprocesar con otro pipeline mañana. Guardar solo el recorte es irreversible | 🟠 alta |
| Condición de luz (interior / exterior / poca luz / contraluz) | Etiquetar el dominio permite medir **dónde** falla el modelo en vez de saber solo que falla | 🟠 alta |
| Ángulo aproximado (cenital / oblicuo / lateral) | Igual que la luz: es la variable que separa foto de estudio de foto de móvil | 🟠 alta |
| Métricas de `frame_quality_gate.py` (nitidez, exposición) | **Ya existe, es de CAM y sigue sin usarse.** Sale gratis y permite filtrar por calidad sin descartar nada | 🟠 alta |
| Estado físico (íntegro / deformado / roto) | RealWaste es todo objeto degradado; sin este campo no se puede replicar esa condición | 🟡 media |
| Fondo (caneca / suelo / mesa / bolsa) | El modelo puede estar usando el fondo como atajo — pasó con la síntesis | 🟡 media |
| Marca de dispositivo/gama y timestamp | Diagnóstico de sesgo por cámara | 🟡 media |
| Geolocalización | ❌ **No capturar.** Riesgo de privacidad sin retorno técnico; el país ya se conoce por el perfil activo | — |

> **Invariante 6 del proyecto — «las imágenes no salen del proceso» — es de la app
> principal y aquí no aplica**, porque el propósito es justamente enviarlas. Pero
> eso convierte a RecyCol Entrenamiento en **una app con un modelo de privacidad
> distinto**: necesita consentimiento explícito, aviso claro, y no debe compartir
> el código de persistencia con la app principal para que nadie herede por
> accidente el permiso de subir imágenes.

### Cómo evitar etiquetas basura

El texto libre es la peor opción: da «botella», «botella de plástico», «plastico»,
«PET», «envase», y todas hay que mapearlas a mano. Y las faltas de ortografía
crecen sin límite. El plan que recomiendo:

1. **El modelo actual propone y el usuario confirma o corrige.** Se le enseñan las
   2–3 hipótesis del top-K (que EDGE ya puede emitir, #126) más «ninguna de
   estas». Un toque para confirmar: fricción mínima y etiqueta canónica.
2. **«Ninguna de estas» abre la lista cerrada de los 11 materiales**, con icono y
   ejemplo. Nunca texto libre como vía principal.
3. **Texto libre solo como campo opcional de matiz** («vaso de café con tapa»),
   que no se usa para entrenar pero sirve para descubrir clases que faltan.
4. **La corrección del usuario vale más que la confirmación.** Cuando alguien
   corrige al modelo, esa imagen es **precisamente donde el modelo falla**:
   márcala y priorízala en el muestreo. Es aprendizaje activo casi gratis.
5. **Redundancia en una submuestra**: que un ~10 % lo etiquete más de una persona.
   Sin acuerdo entre etiquetadores no se sabe si una clase es difícil para el
   modelo o **ambigua para los humanos** — y si lo segundo, el problema es la
   taxonomía, no el modelo.
6. **Confirmar sin mirar es el fallo de modo esperable.** Si el usuario acepta la
   sugerencia en menos de ~1 s, o acepta 20 seguidas, baja la confianza de esas
   etiquetas. Guardar el tiempo de respuesta cuesta un entero.

### Volumen y balance que moverían la aguja

Referencia: el pool actual es de 17 176 imágenes de entrenamiento y da 74,2 % de
ruta contra control. **No hacen falta otras 17 000**: los datos propios valen
mucho más por imagen porque son del dominio de destino.

| Objetivo | Volumen | Qué compra |
|---|---|---|
| **Desbloquear el caso estrella** | **300–500 de `BEVERAGE_CARTON`**, la mitad con restos dentro | El vaso de café pasa de no verificable a verificable. **Es el mejor retorno por hora de todo el proyecto**: 3–5 h de captura |
| **Reactivar la etapa 2** | **400–600 de cartón y papel** con estado de contaminación etiquetado, balanceado limpio/sucio | Convierte la contaminación de sintética a real, **solo en las clases donde el plan B pregunta** |
| **Cubrir `ELECTRONIC`** | 300–500 | Cierra la única clase a cero |
| **Mover la aguja de dominio** | **500–800 por clase** en las 11, ~6 000–9 000 en total | Es donde cabría esperar acercarse a RNF-008 |
| Mínimo con el que ya se aprende algo | ~150 por clase | Permite **fine-tuning** sobre el modelo actual, no entrenar desde cero |

**Balance**: importa más el equilibrio que el total. El pool actual ya está sesgado
y `RESIDUAL` es hoy la clase más débil (2,8 % de top-1). Un tope por clase en la
app —dejar de pedir fotos de plástico cuando sobran— vale más que duplicar el
volumen.

### Cómo integrarlo sin contaminar el control

**Esta es la parte donde es fácil destruir año y medio de evidencia.** RealWaste
es la única prueba de generalización que existe; si se contamina, no hay forma de
saber si un modelo mejora.

1. **RealWaste sigue intocable, pase lo que pase.** No se mezcla, no se amplía, no
   se sustituye. Los datos nuevos son **otra fuente**, con su entrada en
   `label_mapping.yaml` y en `DATA_LICENSES.md` **antes** de usarse.
2. **Partir por aportante, no por imagen.** Si la misma persona fotografía su
   botella cinco veces y esas fotos caen unas en train y otras en validación, la
   métrica queda inflada — es el mismo error que ya sospechamos entre train y val.
   **La unidad de partición es el usuario, y después el objeto físico.**
3. **Reservar desde el primer día un segundo control propio**, congelado, que
   **jamás entrena**: idealmente aportado por personas que no aparecen en train.
   RealWaste no contiene `BEVERAGE_CARTON`, `BATTERY` ni `ELECTRONIC`, así que
   **hoy no hay control para el caso estrella**; este lo daría.
4. **Deduplicar contra todo lo existente** con el pHash que ya usa S22, incluido
   contra RealWaste: si alguien fotografía algo casualmente idéntico al control,
   fuera.
5. **Cuarentena antes de entrenar.** Ninguna imagen entra al pool sin pasar por
   revisión — la app es pública y llegará ruido, fotos irrelevantes y, con
   suerte, alguna imagen inapropiada.
6. **Medir el efecto por separado**: entrenar con y sin los datos propios y
   comparar **contra el control de siempre**. Es la única forma de saber cuánto
   aportan, y evita repetir la lección de `full-v2`, donde una fuente nueva mejoró
   la val interna mientras empeoraba lo que importa.
7. **Reentrenar con la receta ya validada** (`--exclude garbage_dataset_v2:RESIDUAL`,
   sin palancas de coste) y recordar la regla de §7: **una diferencia menor de
   ~2 pp entre runs no significa nada.**

### Sobre la contaminación en cartón, ahora que el problema es más pequeño

Con el recorte del plan B a **solo cartón y papel**, el problema deja de ser
«detectar suciedad en cualquier residuo» y pasa a ser **«detectar grasa y líquido
en fibra de celulosa»**. Es mucho más fácil, y por una razón física aprovechable:
la fibra **absorbe**. Una mancha de grasa en cartón no es un parche superpuesto —
que es justo lo que la síntesis fabricaba y por lo que fracasó— sino un cambio de
**translucidez y saturación** del propio material, con bordes difusos y sin brillo
especular. Esa firma es más estable entre objetos que «suciedad» en abstracto, y
apunta a que una síntesis específica de absorción en fibra (oscurecer, saturar y
reducir contraste local con bordes difusos, **sin** superponer color opaco)
transferiría mucho mejor que la actual.

Pero se aplica lo mismo que en §7: **sin datos reales etiquetados no hay forma de
saber si funciona**, porque la métrica sintética ya demostró mentir. Por eso el
estado de contaminación en cartón y papel es **la captura prioritaria** de
RecyCol Entrenamiento.

---

## 11. Encargo abierto — «RecyCol desarrollador»

**Esto es lo siguiente que hay que construir.** Juan lo pidió al cerrar la v1 y
lo hará otro agente. Lo que sigue es el encargo tal cual, más lo que ya está
hecho para que nadie lo repita.

### Qué es

Una **segunda APK**, instalable **junto a la normal** (no en vez de ella), cuyo
único fin es **diagnosticar y subir la tasa de aciertos**. La normal es para
enseñar; esta es para entender por qué falla.

Lo que Juan pidió exponer, y por qué cada cosa:

| Qué | Para qué sirve |
|---|---|
| **Confianza por clase**, no solo la ganadora | Distinguir «se equivocó con seguridad» de «estaba dudando». Son fallos distintos y se arreglan distinto |
| **Alternativas que consideró el modelo** | Ver si la correcta iba segunda —brecha de calibración— o ni aparece —brecha de dominio, la de §7 |
| **Latencia** | Confirmar RNF-001 y el reparto por gama con números reales, que es la issue **#18** y sigue sin cerrar |
| **Gama detectada** | Saber qué modelo corrió de verdad; hoy no hay forma de comprobarlo desde la app (#158) |
| **Corregir a mano lo que el modelo falló** | Cada corrección es una etiqueta. Es la semilla directa de la captura de §10 |

Juan fue explícito: **«define tú qué es útil con ese objetivo, es tu criterio»**.
Lo de arriba es el mínimo, no el límite.

### Lo que ya está hecho — no rehacer

- **`InferenceDiagnostics`** existe en
  `androidApp/inference/.../diagnostics/`: está escrito y sin cablear. Falta
  **una línea** en `LiteRtWasteClassifier`, justo antes del `argmax`, para
  publicar la distribución completa y la latencia. Ese es el punto de entrada
  natural, no hace falta abrir el puerto de dominio.
- **El puerto `WasteClassifier` solo devuelve el top-1.** La distribución de las
  11 clases existe *dentro* de la inferencia y se descarta ahí. Para exponerla
  sin romper el contrato de M0, intercepta en la capa de inferencia; abrir el
  puerto a top-K es la issue **#126** y es una decisión de CORE.
- **`ClassificationStabilizer.candidates()`** ya expone las hipótesis vivas de la
  ventana de votación, y es lo que siembra la hoja de selección manual.
- **La corrección manual ya existe** en la interfaz («No es esto» y la entrada
  manual) y ya fija la decisión. Lo que falta es **registrarla**.

### Restricciones

- **Dos APKs instalables a la vez** ⇒ `applicationIdSuffix` distinto, no dos
  ramas. Lo natural es un `productFlavor`; el intento de la v1 se revirtió sin
  terminar, así que se empieza limpio.
- **RNF-002 y RNF-012 siguen mandando**: todo local, y **jamás se retienen
  fotogramas**. Los diagnósticos son números, no imágenes. Guardar fotos entra en
  el terreno de §10 y **eso todavía no se construye**.
- La base de datos y la app de recolección de datos **no son parte de este
  encargo**: Juan las dejó explícitamente para una fase posterior.

---

## Documentos formales

Sobreviven porque son entregables o registros, no contexto. **Enlázalos, no
copies su contenido aquí.**

| Documento | Qué contiene |
|---|---|
| [`docs/F_Analisis_de_Requerimientos_V1,0_RecyCol.md`](docs/F_Analisis_de_Requerimientos_V1%2C0_RecyCol.md) · [.docx](docs/F_Analisis_de_Requerimientos_V1%2C0_RecyCol.docx) | **Especificación completa**: CUS, RF, RNF y matriz de trazabilidad. Es la fuente de verdad de qué hay que construir |
| [`ml/DATA_LICENSES.md`](ml/DATA_LICENSES.md) | **Registro legal de procedencia**: licencia, evidencia y veredicto comercial de cada dataset, peso y herramienta. Manda sobre el resto |
| [`docs/arquitectura.md`](docs/arquitectura.md) | Diagramas Mermaid: contexto, casos de uso, modelo de dominio, secuencias, estados, y la tabla de decisiones de arquitectura con sus alternativas descartadas |
| [`plan/plan_de_trabajo.md`](plan/plan_de_trabajo.md) | Estimación, cronograma y criterio de hecho por sesión (S01–S44) |
| [`docs/design-system.md`](docs/design-system.md) | Tokens, componentes y notas de contraste. Léelo antes de tocar una pantalla |
| [`.github/runner/README.md`](.github/runner/README.md) | Runbook de los runners self-hosted |
| [`androidApp/inference/README.md`](androidApp/inference/README.md) | Banco de validación de `.tflite` y contrato de modelos, en detalle |
| [`benchmark/README.md`](benchmark/README.md) | Protocolo y esquema del reporte de latencia |
| [`shared/resources/profiles/README.md`](shared/resources/profiles/README.md) | Esquema del perfil normativo y cómo añadir un país |
| [`ml/README.md`](ml/README.md) | Estructura del pipeline de ML, fuentes y comandos de descarga (`docker/GPU.md` llega con el PR #114) |
| [`README.md`](README.md) | Portada pública del repositorio |
| [`dataApp/README.md`](dataApp/README.md) | **RecyCol Aporta**: qué es, cómo funciona para quien aporta y qué se guarda de cada foto |
| [`dataApp/docs/DESCARGAR-DATASET.md`](dataApp/docs/DESCARGAR-DATASET.md) | **Dónde está la base de datos y cómo bajarla para entrenar.** Un comando, más las tres cosas que no se pueden hacer mal |
| [`dataApp/docs/DESPLIEGUE.md`](dataApp/docs/DESPLIEGUE.md) | Qué hay montado en Azure, cómo publicar un cambio y por qué no es lo que decía la propuesta |
| [`dataApp/docs/CONSENT-v2.md`](dataApp/docs/CONSENT-v2.md) | Texto archivado del consentimiento y cesión de derechos vigente |
| [`dataApp/docs/INTEGRACION-ML.md`](dataApp/docs/INTEGRACION-ML.md) | Cómo entra el dataset propio al pipeline sin contaminar el control |
