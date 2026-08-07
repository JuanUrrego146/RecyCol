# Traspaso — Agente INFERENCIA (EDGE, milestone M3)

Fecha: 07/08/2026 · Ámbito exclusivo: `androidApp/inference/`

Este documento es lo que necesita saber quien retome el runtime de inferencia
on-device. Todo el código está escrito, probado y empujado; nada queda sin
confirmar. Lo que falta es fusión y hardware, no implementación.

## 1. Estado de M3

Las seis sesiones del milestone están implementadas y verificadas, más dos
coordinaciones derivadas de la revisión de QA y el apoyo a ML en S27.

| Sesión | Issue | Dónde está | Estado |
|---|---|---|---|
| S15 · LiteRT, delegados y respaldo en CPU | #14 | fusionado en `main` (PR #66) | **cerrada** |
| S16 · Detección y recorte del objeto | #15 | PR #81 | CI verde, espera fusión |
| S17 · Política de gama con micro-benchmark | #16 | PR #86 | CI verde, espera fusión |
| S18 · Activación escalonada por gama | #44 | PR #87 (ya fusionado hacia la pila) | espera llegar a `main` |
| S19 · Etapa de contaminación | #17 | PR #88 | CI verde, espera fusión |
| S20 · Latencia, memoria y consumo | #18 | PR #99 | CI verde, espera fusión |
| Coordinación · adaptador de gama y latencia e2e | #102, #103 | PR #109 | CI verde, espera fusión |
| Coordinación · puerto `TierPreferenceRepository` (RF-031) | #94 | rama `edge/coord-94-tier-preference` | listo; abrir PR cuando se fusione #96 |
| Apoyo a ML · banco de validación de exports | #25 | rama `edge/coord-s27-banco-validacion` | listo; abrir PR cuando convenga |

**Lo único que M3 no puede cerrar sin hardware**: el criterio de #18 pide
latencia y memoria *medidas por gama*, y eso exige modelos reales (ML, S27) y
un dispositivo físico de cada gama. El banco que produce ese registro está
entregado y se corre con un comando (ver §4); la corrida es alcance de S41
(QA). La parte JVM ya está medida: preprocesado con mediana de 10 ms por frame
1080p→224 en el contenedor de CI.

## 2. Cadena de PRs y orden de fusión

Los PRs están **apilados**: cada uno tiene como base la rama del anterior.
Fusionar en este orden y no otro:

```
#86 (S17+S18)  →  hacia la rama de #81
#81            →  hacia main   ← aquí entran S16, S17 y S18; cierra #15, #16, #44
#88 (S19)      →  retarget a main y fusionar; cierra #17
#99 (S20)      →  retarget a main y fusionar; cierra #18
#109 (coord)   →  retarget a main y fusionar; cierra #102 y #103
```

Al fusionar un PR de la pila, GitHub suele redirigir la base del siguiente
automáticamente; si no lo hace, `gh pr edit <n> --base main`. El detalle está
comentado en #86.

**Dos trampas ya pisadas, no repetirlas:**

- Las palabras clave de cierre **deben ir en inglés** (`Closes #N`). Los
  primeros PRs decían «Cierra #N» y GitHub los ignoró: #14 hubo que cerrarla
  a mano. Los PRs actuales ya están corregidos.
- Un PR de una pila puede fusionarse hacia su base intermedia en vez de a
  `main` (pasó con #87). No se pierde nada, pero deja la pila colapsada:
  revisar la base antes de fusionar.

Los fallos de CI que aparezcan en el historial de estas ramas fueron runs
cancelados en cola durante la caída de GitHub Actions del 06/08; los cuatro
PRs de la cadena terminaron en verde tras relanzarlos. Si un run muere sin
asignar runner (`runner: NONE`, cero pasos), es infraestructura: relanzar.

## 3. Contrato de modelos (EDGE ↔ ML) — estable, no cambiar a la ligera

Los `.tflite` **no se versionan** (`.gitignore`): se reconstruyen con el
pipeline de `ml/` y se dejan caer en
`androidApp/inference/src/main/assets/models/`. Mientras no existan, la
inyección sirve un `StubWasteClassifier` determinista y la app funciona.

| Archivo | Modelo | Entrada | Salida |
|---|---|---|---|
| `material_low.tflite` | MobileNetV3-Small INT8 | 224×224×3 RGB UINT8 | softmax sobre materiales |
| `material_mid.tflite` | MobileNetV3-Large 0.75 INT8 | 224×224×3 RGB UINT8 | softmax sobre materiales |
| `material_high.tflite` | EfficientNet-Lite2 INT8 | 260×260×3 RGB UINT8 | softmax sobre materiales |
| `contamination.tflite` | binario INT8 | 224×224×3 RGB UINT8 | softmax `[CLEAN, CONTAMINATED]` |
| `detector.tflite` | detector genérico, **opcional** | 320×320×3 RGB UINT8 | `[cajas, clases, puntuaciones, conteo]` |

Reglas duras:

1. **Orden de salida** = orden de declaración de `WasteMaterial` (etapa 1) y
   `[CLEAN, CONTAMINATED]` (etapa 2), definido en `ModelOutputOrder`. Debe
   coincidir con `ml/taxonomy/label_mapping.yaml`.
2. **Layout de entrada**: `[1, lado, lado, 3]`, RGB por filas, sin alfa,
   UINT8 `[0, 255]`. Una entrada FLOAT32 se declara en su `ModelSpec` con
   media y desviación.
3. Si el número de clases no cuadra con la taxonomía, el clasificador **falla
   con error explícito**; nunca mapea en silencio.
4. Cambiar nombres, tamaños u orden de clases es cambiar el contrato: requiere
   issue de coordinación EDGE ↔ ML y tocar `ModelCatalog`/`ModelOutputOrder`
   junto con el `label_mapping.yaml`.

El detector es una mejora opcional: si falta, o la gama no habilita
`OBJECT_DETECTION`, todas las gamas usan el marco guía fijo y nada deja de
funcionar.

## 4. Banco de validación de `.tflite` (rama `edge/coord-s27-banco-validacion`)

Pensado para que **ML lo use sin EDGE**. Un solo comando, desde la raíz del
repo (requiere `adb` en el PATH y un dispositivo o emulador conectado; en
Windows, desde Git Bash):

```bash
./androidApp/inference/validate_models.sh ml/dist/models ruta/al/conjunto-eval
```

Qué hace: copia los `.tflite` a los assets, imprime sus **SHA-256** (para
cotejar con el `export_report.json` de ML), corre el banco instrumentado
completo y deja el registro legible en
`androidApp/inference/build/registro-validacion.txt`.

Qué reporta:

- **Contrato** (`ModelContractVerification`) — formas, número de clases y
  softmax de cada modelo contra el runtime real. Un export fuera de contrato
  falla aquí, antes de llegar a la app.
- **Orden de clases** (`ReferenceBatchVerification`) — compara índice a índice
  contra el lote de referencia de ML. Formato:
  `src/androidTest/assets/eval/reference/reference.csv` con líneas
  `archivo,indiceEsperado` (el argmax que el pipeline de ML midió sobre el
  *mismo* artefacto INT8). Umbral de acuerdo 90 %; cada discrepancia se
  registra con ambos índices y materiales.
- **Pérdida por cuantización** (`QuantizationAccuracyBenchmark`) — top-1 INT8
  y top-1 float sobre un conjunto de evaluación real, con la pérdida en
  puntos, el acuerdo INT8↔float y el delta de confianza. Requiere el gemelo
  float junto al INT8: `<variante>_float.tflite`, entrada FLOAT32 RGB `[0,1]`.
  El conjunto va en `src/androidTest/assets/eval/` con `labels.csv`
  (`archivo,MATERIAL`, nombres de `WasteMaterial`) y **debe ser un conjunto no
  visto en entrenamiento**.
- **Latencia y memoria** (`DeviceLatencyBenchmark`) — mediana por variante y
  vía de aceleración, y memoria (heap JVM + nativo) con el presupuesto de
  350 MB asertado.

Sin modelos o sin conjunto de evaluación, las pruebas se **omiten** (`assume`),
no fallan.

**Por qué esto importa más que la latencia**: ML reportó 91 % de exactitud en
su propio dominio contra 42 % en el conjunto de control de residuos reales.
Con esa brecha, separar la pérdida de cuantización de la pérdida de dominio es
la información que decide si el problema es el INT8 o el dataset — por eso el
banco mide ambas cosas por separado y exige un conjunto de control no visto.

## 5. Arquitectura del módulo — lo que hay que entender antes de tocarlo

- **`TierAwareWasteClassifier`** es lo que se inyecta. Consulta la política de
  gama en *cada* llamada y reconstruye el clasificador concreto (variante de
  modelo + estrategia de ROI) cuando la gama cambia, liberando el anterior.
  Sin esto, el singleton de Koin congelaba la gama en LOW (coordinación #102).
  Las llamadas se serializan con un `Mutex` para que el recambio nunca cierre
  un motor con una inferencia en vuelo.
- **`ResilientInferenceEngine`** intenta NNAPI → GPU → CPU al construir, y si
  un delegado falla *en caliente* reconstruye en CPU y reintenta una vez. El
  usuario nunca ve un error de delegado. La CPU es el respaldo que no puede
  faltar.
- **`BenchmarkedTierPolicy`** resuelve la gama combinando capacidades
  declaradas (techo) con un micro-benchmark de latencia real (manda dentro de
  ese techo), con presupuesto duro de 1,2 s para no comprometer el arranque.
  Cachea en `SharedPreferences` invalidadas por fingerprint del sistema.
  Degrada un escalón si la latencia observada se sostiene sobre el umbral;
  el ajuste manual del usuario (RF-031) manda sobre todo y suspende la
  degradación automática.
- **`FramePreprocessor`** tiene estado a propósito: reutiliza el búfer directo
  entre llamadas y lee solo la región a muestrear. Es lo que evita asignar
  memoria nativa en el bucle de análisis continuo. Contrato: el búfer devuelto
  se sobrescribe en la siguiente llamada.
- **Invariantes que este módulo respeta y no se negocian**: el clasificador
  devuelve materiales, nunca canecas; la política de gama se consulta, no se
  asume; sin red en la ruta de clasificación; los frames no se persisten ni se
  registran; `shared/` no ve nada de LiteRT ni de Android.

## 6. Costuras abiertas con otros agentes

- **CAM (#104, #98)** — el wrapper de CameraX entrega `LumaImageFrame`, que
  solo transporta luma; el clasificador exige `PixelAccessFrame` con
  `readArgbPixels()` / `readArgbRegion(left, top, side)`. Mientras no exista
  esa conversión (YUV→ARGB, preferiblemente perezosa y por región para no
  materializar el frame completo), la clasificación no puede consumir el flujo
  de cámara. Está ofrecido escribir el conversor en `inference/frame/` si CAM
  pasa el layout de planos. **Es la costura que bloquea la integración real.**
- **CORE (#96)** — el puerto `TierPreferenceRepository` calza con la API de
  S18 sin cambios. El adaptador EDGE (`PolicyTierPreferenceRepository`) está
  listo y probado en `edge/coord-94-tier-preference`; abrir el PR en cuanto
  #96 llegue a `main` para que el diff quede contenido en `inference/`.
- **QA (S41, #39)** — `DeviceLatencyBenchmark` es directamente su banco de
  latencia por gama; los medidores por etapa alimentan sus umbrales de S39.
- **ML (#25)** — contrato congelado y banco autoservicio entregado. ML confirmó
  que su lado está implementado en `ml/export/export_litert.py` (PR #114) y
  avisará en #25 con rutas y hashes cuando fije los checkpoints ganadores.

## 7. Cómo trabajar en este repo (aprendido a golpes)

- **No hay Android SDK en la máquina de Juan.** La única vía de build es el
  contenedor: `docker compose -p botabien-edge run --rm android-build ./gradlew <tareas>`.
  El sufijo `-p <proyecto>` es obligatorio: sin él, dos agentes compilando a la
  vez chocan por el lock de Gradle y sale un `BUILD FAILED` engañoso.
- **Varios agentes comparten el clon principal.** Nunca cambiar de rama ahí:
  crear un worktree propio (`git worktree add ../BotaBien-inferencia <rama>`) y
  trabajar desde él.
- **Nunca `git add -A`**: añadir los archivos propios explícitamente. Un
  `add -A` ya arrastró trabajo sin confirmar de otro agente.
- Una rama por issue, un PR por issue, `main` no se toca directo.

## 8. Comandos útiles

```bash
# Pruebas del módulo (equivalente a lo que corre CI)
docker compose -p botabien-edge run --rm android-build ./gradlew :androidApp:inference:testDebugUnitTest

# Build completo equivalente a CI
docker compose -p botabien-edge run --rm android-build ./gradlew :shared:allTests :shared:testing:allTests :shared:verifyPlatformIsolation :androidApp:assembleDebug

# Banco en dispositivo (necesita adb + dispositivo conectado)
./gradlew :androidApp:inference:connectedDebugAndroidTest
```

Estado de pruebas al cierre: **79 pruebas unitarias del módulo en verde** en la
punta de la cadena (`edge/coord-94-tier-preference`), más el banco instrumentado
compilando. Todos los builds se verificaron en el contenedor `android-build`.
