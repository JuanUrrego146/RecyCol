# Reporte de métricas — M4 (S28)

Evaluación cruzada sobre **RealWaste** (4 752 imágenes de relleno sanitario,
jamás usadas en entrenamiento ni selección). La métrica que manda es el
**acierto de ruta de disposición** (RNF-008: ≥85 % material, ≥95 % ruta).

## Resumen

- **Modelo ganador: `mid/no-trash` (mobilenet_v3_large)** — contra control, top-1 **50.6%** y ruta **74.2%**.
- **RNF-008 no se cumple** (exige 85 % / 95 %). La brecha que queda es de **dominio**, no de arquitectura ni de cuantización: el mismo checkpoint pasa del 98 % de ruta en val interna al 74 % contra control.
- **El hallazgo que desbloqueó M4**: la carpeta `trash` de Garbage v2 son envases sucios etiquetados como residual, y enseñaban «envase degradado ⇒ caneca negra». Excluirla subió la ruta de 61,4 % a ~70 % en la misma variante.
- **La val interna no predice el control.** Ocurrió tres veces: con `full-v2` (mejor val, peor control), con EfficientNet-B2 (mejor val de todas, peor control que MobileNetV3-Large) y con la etapa 2 de contaminación (94 % en sintético, inútil en dominio real). Cualquier decisión tomada solo con val interna es sospechosa por defecto.
- **La varianza entre dos ejecuciones idénticas es 2,16 pp de ruta.** Diferencias menores de ~2 pp entre runs no significan nada.

## Clasificador de material — todas las condiciones evaluadas

| Variante/run | Top-1 material | Acierto de ruta | Ruta macro (por clase) |
|---|---|---|---|
| mid/no-trash (mobilenet_v3_large) | 50.6% | 74.2% | 74.3% |
| low/no-trash-seed (mobilenet_v3_small) | 46.2% | 70.6% | 71.1% |
| high/no-trash (efficientnet_b2) | 49.3% | 69.8% | 68.7% |
| low/no-trash (mobilenet_v3_small) | 44.1% | 68.5% | 69.0% |
| low/full (mobilenet_v3_small) | 39.3% | 65.9% | 66.7% |
| low/no-trash-cost (mobilenet_v3_small) | 41.8% | 63.6% | 64.0% |
| low/full-v2 (mobilenet_v3_small) | 42.1% | 61.4% | 63.0% |

**Mejor contra control: mid/no-trash** — top-1 50.6%, ruta 74.2%.

**Veredicto RNF-008: material NO CUMPLE (objetivo 85 %) · ruta NO CUMPLE (objetivo 95 %).**

### Matriz colapsada por caneca (mejor run)

| real \ predicho | NON_RECYCLABLE | ORGANIC | RECYCLABLE |
|---|---|---|---|
| NON_RECYCLABLE | 417 | 77 | 319 |
| ORGANIC | 76 | 453 | 318 |
| RECYCLABLE | 401 | 37 | 2654 |

### Por clase (mejor run)

| Material | n | Top-1 | Ruta | Confundido con |
|---|---|---|---|---|
| PLASTIC | 921 | 52.1% | 83.6% | METAL |
| PAPER | 500 | 70.6% | 88.4% | CARDBOARD |
| CARDBOARD | 461 | 32.3% | 82.4% | PAPER |
| GLASS | 420 | 37.4% | 88.6% | PLASTIC |
| METAL | 790 | 72.0% | 87.3% | PLASTIC |
| ORGANIC | 847 | 53.5% | 53.5% | PLASTIC |
| TEXTILE | 318 | 72.0% | 73.3% | CARDBOARD |
| RESIDUAL | 495 | 2.8% | 37.2% | TEXTILE |

## Clasificador de contaminación (S26)

- Umbral elegido (prioriza no llamar limpio a lo contaminado): 0.62 — recall contaminado 92.2%, precisión 96.2% (validación del sintético).
- Control indirecto de transferencia: tasa de 'contaminado' en val limpia 0.0 vs control RealWaste 0.0125 (RealWaste debería puntuar claramente más alto; no existe etiqueta real de contaminación en ninguna fuente pública — limitación documentada).

⚠️ **La etapa 2 no transfiere al dominio real.** Solo el 1.2% de RealWaste — residuos degradados de relleno sanitario — se marca como contaminado, frente al 0.0% de las fotos limpias de estudio. Un detector de suciedad que no ve suciedad en un relleno ha aprendido el artefacto de la síntesis, no la contaminación. **No cablear la etapa 2 en automático**: aplica el plan B (preguntar al usuario) hasta que exista un mini-set real con etiqueta limpio/sucio.

## Export LiteRT INT8 (S27)

- Artefactos: contamination.tflite (1.9 MB), material_high.tflite (9.35 MB), material_low.tflite (1.91 MB), material_mid.tflite (4.84 MB).
- Total 18.0 MB — presupuesto 150 MB: DENTRO.

### Pérdida por cuantización INT8

Medida con el intérprete de LiteRT sobre el **mismo split y el mismo run** que el checkpoint float, así que la resta aísla el efecto del INT8 y lo separa de la pérdida de dominio.

| Variante | float top-1 | INT8 top-1 | Δ | float ruta | INT8 ruta | Δ |
|---|---|---|---|---|---|---|
| low | 46.2% | 15.8% | -30.4 pp | 70.6% | 61.1% | -9.5 pp |
| mid | 50.6% | 49.4% | -1.2 pp | 74.2% | 74.4% | +0.2 pp |
| high | 49.3% | 47.3% | -2.0 pp | 69.8% | 67.7% | -2.1 pp |

**La brecha contra control no la causa el INT8.** mid, high cuantiza sin pérdida apreciable de ruta, así que la distancia entre la val interna y el control es de **dominio**, no de precisión numérica. Era la pregunta que la separación float/INT8 venía a responder.

⚠️ **low se degrada gravemente al cuantizar** (más de 10 pp de top-1). Con el mismo pipeline de evaluación para las tres variantes, que unas aguanten y otra no señala al modelo, no a la medición: MobileNetV3-Small (hard-swish y bloques SE) es conocido por cuantizar mal. **Afecta justo a la gama baja**, que es donde el modelo pequeño hace falta. Alternativas: cuantización por canal más agresiva, entrenamiento consciente de cuantización (QAT), o servir a la gama baja el modelo de gama media si la latencia lo permite.

### ⚠️ Los artefactos NO cumplen el contrato de entrada de S15

- Exigido por el contrato: `[1, lado, lado, 3] UINT8 RGB`.
- Declarado por los artefactos: `[1, 3, lado, lado] INT8 (NCHW)`.
- litert-torch exporta con el layout y el tipo del modelo PyTorch de origen. Antes de cablear en la app hay que reexportar con firma UINT8 NHWC o declarar esta firma en el ModelSpec de EDGE.

**Los `.tflite` no se pueden cablear en la app tal cual.** El orden de salida sí es el del contrato; lo que no encaja es el layout y el tipo de entrada. Se detecta aquí, y no al integrar, porque `eval/evaluate_tflite.py` lee la firma real del artefacto en vez de asumirla.

- Validación en dispositivo real (latencia y memoria por gama): pendiente, exige hardware Android — banco de EDGE (issue #25) y S41 de QA.

## Limitaciones y riesgos abiertos

- **El control no contiene BEVERAGE_CARTON ni BATTERY**: el caso estrella (vaso de café) no es verificable con RealWaste; mini-set de control propio coordinándose con REGLAS (#23).
- Garbage v2 en uso con riesgo legal abierto (#77, bloquea lanzamiento, no desarrollo).
- La contaminación se entrenó solo con síntesis; la transferencia a suciedad real solo tiene control indirecto.
- La carpeta trash de Garbage v2 quedó excluida por la auditoría de REGLAS (#23): enseñaba envase-degradado⇒RESIDUAL.
- **RESIDUAL es la clase más débil** (2,8 % de top-1, 37,2 % de ruta): al retirar la carpeta `trash` se le quitaron 350 ejemplos. El error va en la dirección menos grave —residuo señalado como reciclable en vez de al revés— pero es deuda abierta.
- **La gama alta llevaría hoy el peor modelo de los tres.** El contrato asigna EfficientNet-Lite2 a `high`, y EfficientNet-B2 rinde 4,4 pp por debajo de MobileNetV3-Large contra control pese a tener la mejor val. Cambiarlo toca el contrato congelado de S15: decisión de producto con issue de coordinación.
- **Las palancas sensibles a coste de ruta empeoran** (−5,9 pp, 2,7 veces la varianza). Se descarta esa configuración, no la idea: optimizar la ruta en el dominio de entrenamiento, donde ya está al 98 %, solo rigidiza el modelo.
- La reproducibilidad de la augmentación estaba rota (`hash()` de Python, aleatorizado por proceso). Corregido, pero **todo run anterior a esa corrección se midió sin saber la varianza**, incluido el barrido de arquitectura, cuyo ganador se decidió por 0,13 pp — muy por debajo del ruido.