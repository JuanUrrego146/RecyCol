# androidApp/inference — runtime de inferencia on-device

Ámbito del agente EDGE (M3). Implementa el puerto `WasteClassifier` del dominio
sobre LiteRT con delegados NNAPI y GPU y respaldo automático en CPU. Todo corre
local: sin red (RNF-002) y sin persistir frames (RNF-012).

## Contrato de modelos (estable — coordinación con el agente ML)

Los `.tflite` **no se versionan** (`.gitignore`): se reconstruyen con el
pipeline de `ml/` y se dejan caer en `src/main/assets/models/`. Mientras no
existan, la inyección sirve un `StubWasteClassifier` determinista y la app
sigue funcionando.

| Archivo | Modelo | Entrada | Salida |
|---|---|---|---|
| `material_low.tflite` | MobileNetV3-Small INT8 | 224×224×3 RGB UINT8 | softmax sobre los materiales |
| `material_mid.tflite` | MobileNetV3-Large 0.75 INT8 | 224×224×3 RGB UINT8 | softmax sobre los materiales |
| `material_high.tflite` | EfficientNet-Lite2 INT8 | 260×260×3 RGB UINT8 | softmax sobre los materiales |
| `contamination.tflite` | binario INT8 | 224×224×3 RGB UINT8 | softmax `[CLEAN, CONTAMINATED]` |
| `detector.tflite` | detector genérico (p. ej. EfficientDet-Lite0) | 320×320×3 RGB UINT8 | post-procesado TFLite estándar: `[cajas, clases, puntuaciones, conteo]` |

El detector es **opcional y agnóstico a la clase** (RF-010): solo aporta dónde
está el objeto dominante. Si falta, o la gama no habilita `OBJECT_DETECTION`,
todas las gamas usan el marco guía fijo y nada deja de funcionar.

Reglas del contrato:

1. **Orden de salida**: el índice `i` del tensor de salida corresponde a la
   posición `i` de `ModelOutputOrder` (`WasteMaterial` en su orden de
   declaración para la etapa 1; `[CLEAN, CONTAMINATED]` para la etapa 2).
   Debe coincidir con `ml/taxonomy/label_mapping.yaml`.
2. **Layout de entrada**: `[1, lado, lado, 3]`, RGB por filas, sin alfa.
   UINT8 `[0, 255]` en todas las variantes INT8. Si un modelo futuro usara
   entrada FLOAT32, se declara en su `ModelSpec` con media y desviación.
3. **Salida**: `[1, n_clases]`, UINT8/INT8 cuantizada o FLOAT32; el runtime
   decuantiza solo. Si `n_clases` no cuadra con la taxonomía, el clasificador
   falla con error explícito: nunca mapea mal en silencio.
4. Cambiar nombres de archivo, tamaños de entrada u orden de clases es cambiar
   el contrato: requiere issue de coordinación EDGE ↔ ML.

## Estructura

- `TierAwareWasteClassifier` — el `WasteClassifier` que se inyecta
  (coordinación #102): consulta la política de gama en cada llamada y
  recambia el clasificador concreto (modelo + ROI) cuando la gama cambia —
  resolución tardía del benchmark, degradación en uso o ajuste manual — sin
  reiniciar el proceso ni el grafo de Koin. Libera intérpretes y delegados
  del clasificador saliente.
- `frame/` — `PixelAccessFrame`: acceso a píxeles sobre el `ImageFrame` del
  dominio. Punto de encuentro con `androidApp/camera/` (el wrapper del agente
  CAM debe implementarlo). `BitmapImageFrame` para capturas puntuales y pruebas.
- `model/` — specs, catálogo por gama y carga de assets (`mmap` con respaldo a copia).
- `engine/` — `InferenceEngine` (abstracción comprobable, una o varias salidas),
  `ResilientInferenceEngine` (detección de disponibilidad y respaldo en caliente
  a CPU) y `LiteRtEngines` (enlace real con el intérprete y los delegados).
- `image/` — preprocesado determinista: recorte (región o centro) + bilineal + cuantización.
- `roi/` — aislamiento del objeto (RF-010): `DetectorRoi` en gama media/alta,
  `GuideFrameRoi` (marco guía fijo) en gama baja o como degradación. La
  geometría del marco guía es `GuideFrameRoi.GUIDE_FRACTION` del lado menor,
  centrada: la pantalla de cámara (FRONT) debe dibujar el marco con esa misma
  geometría. `LatencyMeter` registra el coste del detector (lo consume S20).
- `tier/` — política de gama real (CUS-008, RF-029): sondeo de capacidades,
  `WarmupBenchmark` (mediana de latencia sobre el modelo de gama baja, con
  presupuesto duro para el criterio de los 2 s), `TierResolver` (las
  capacidades ponen el techo, el benchmark manda), caché en `SharedPreferences`
  invalidada por fingerprint del sistema, y degradación en uso: con la ventana
  de latencias observadas sostenidamente sobre el umbral, la gama baja un
  escalón y se re-cachea. **Integración pendiente con `androidApp/di/`**: el
  arranque debe llamar una vez a `BenchmarkedTierPolicy.ensureResolved()`
  fuera del hilo principal.
- `di/` — módulo Koin que expone los puertos `DeviceTierPolicy` y `WasteClassifier`.

## Activación por gama (RF-030) y ajuste manual (RF-031)

- Toda función costosa consulta `DeviceTierPolicy.isEnabled(...)` antes de
  activarse (invariante 5). Consumidores: este módulo (`OBJECT_DETECTION` al
  elegir estrategia de ROI), el módulo de cámara (`CONTINUOUS_CLASSIFICATION`,
  `FULL_FRAME_QUALITY_ANALYSIS`), el flujo de clasificación
  (`AUTOMATIC_CONTAMINATION_INSPECTION`) y el escaneo de canecas
  (`CONTINUOUS_BIN_SCAN`).
- La clasificación por cámara **no** pasa por `isEnabled`: funciona en las
  tres gamas y bajo cualquier combinación de ajustes (RNF-001); hay prueba
  que lo verifica por gama.
- `BenchmarkedTierPolicy.setManualOverride(tier | null)` aplica el nivel de
  rendimiento elegido por el usuario: manda sobre la gama medida, persiste
  entre reinicios, suspende la degradación automática y `null` vuelve al modo
  automático. **Coordinación pendiente**: exponer esto a la pantalla de
  ajustes (FRONT, S08) requiere un caso de uso/puerto de CORE; mientras tanto
  el mecanismo queda estable y probado aquí.

## Decisiones

- El orden de preferencia de aceleración es NNAPI → GPU → CPU. NNAPI se
  descarta bajo API 27 y se configura sin su CPU de referencia (si no hay
  acelerador real, nuestra vía CPU con XNNPACK es mejor). El fallo de un
  delegado en caliente degrada a CPU de forma permanente y reintenta la
  inferencia: el usuario nunca ve el error (criterio de S15).
- La verificación en dispositivo (modo avión, latencia real) queda pendiente
  de que existan modelos empaquetados; se cubre con el banco de S20/S41.

## Latencia y memoria (S20, RNF-001, RNF-007)

Optimizaciones del bucle de análisis:

- **Cero asignaciones por frame en el preprocesado**: el búfer directo de
  entrada se reutiliza entre llamadas (`FramePreprocessor` es con estado y
  sincronizado) y solo se lee del frame la región que se va a muestrear
  (`PixelAccessFrame.readArgbRegion`; `BitmapImageFrame` copia solo la
  región — en un frame 1080p el recorte típico ahorra varios MB por frame).
  El wrapper del agente CAM debería sobrescribir `readArgbRegion` igual.
- **Cadencia por gama** (`AnalysisCadence` + `FrameThrottle`): baja = bajo
  demanda, media ≈ 5 fps, alta ≈ 10 fps. Los frames descartados no se copian
  ni se preprocesan. El módulo de cámara consume esta compuerta.
- **Lazo de degradación cerrado**: la fábrica conecta la latencia observada
  de la etapa de material con `BenchmarkedTierPolicy.reportObservedLatencyMillis`;
  si un dispositivo sostiene latencias impropias de su gama, baja de gama solo.

Medición y registro:

| Qué | Cómo | Estado |
|---|---|---|
| Preprocesado (JVM, contenedor de CI) | `PreprocessorThroughputBenchmarkTest`, imprime `REGISTRO-S20` | mediana **10 ms** por frame 1080p → 224 (medido en el contenedor `android-build`, 06/08/2026) |
| Latencia de inferencia por variante y vía | `DeviceLatencyBenchmark` (`connectedDebugAndroidTest`) | pendiente de modelos (S27) y dispositivos por gama; lo ejecuta S41 |
| Memoria en clasificación (< 350 MB) | mismo banco: heap JVM + heap nativo tras el calentamiento | ídem; la aserción del presupuesto ya está en el banco |
| Consumo de energía (RNF-007) | sesión larga con Battery Historian sobre el banco | procedimiento documentado; ejecuta S41 con hardware |

El registro por gama del criterio de hecho se llena ejecutando el banco en un
dispositivo de cada gama cuando existan los `.tflite`; las cifras se vuelcan a
esta tabla y al reporte de S41.
