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
- `di/` — módulo Koin que expone el puerto `WasteClassifier`.

## Decisiones

- El orden de preferencia de aceleración es NNAPI → GPU → CPU. NNAPI se
  descarta bajo API 27 y se configura sin su CPU de referencia (si no hay
  acelerador real, nuestra vía CPU con XNNPACK es mejor). El fallo de un
  delegado en caliente degrada a CPU de forma permanente y reintenta la
  inferencia: el usuario nunca ve el error (criterio de S15).
- La verificación en dispositivo (modo avión, latencia real) queda pendiente
  de que existan modelos empaquetados; se cubre con el banco de S20/S41.
