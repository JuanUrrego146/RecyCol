# Procedencia y licencias de datos y modelos — RecyCol ML

**Criterio vigente: USO COMERCIAL.** La app se comercializará; todo activo del
pipeline (datasets, pesos preentrenados, herramientas que generan datos) debe
permitir uso comercial o queda fuera, aunque mejore las métricas. Este archivo
es el registro de procedencia exigido por ese requisito: fuente, licencia,
enlace, evidencia y veredicto de cada activo. Verificación en fuente original:
**06/08/2026**.

Regla de decisión:

- **APTO** — licencia que permite uso comercial con obligaciones cumplibles
  (atribución, aviso de licencia).
- **NO APTO** — licencia NC/no comercial, o contenido sin derechos acreditados.
  No se usa en ninguna etapa del pipeline.
- **AMBIGUO** — licencia declarada permisiva pero cadena de derechos dudosa.
  No se usa hasta decisión expresa de Juan (con asesoría legal si procede).

## Datasets

| Activo | Licencia verificada | Evidencia | Veredicto comercial |
|---|---|---|---|
| RealWaste | CC BY 4.0 | [Ficha UCI #908](https://archive.ics.uci.edu/dataset/908/realwaste) | **APTO** (atribución a Single, Iranmanesh y Raad; fotos propias de los autores, cadena de derechos limpia) |
| TrashNet | MIT | [Repo garythung/trashnet](https://github.com/garythung/trashnet) | **APTO** (fotos tomadas por los propios autores; MIT permite uso comercial con aviso) |
| TACO | Paquete Zenodo CC BY 4.0 · código MIT · **licencia por imagen** heredada de Flickr | [Zenodo 3587843](https://doi.org/10.5281/zenodo.3587843) · [Repo pedropro/TACO](https://github.com/pedropro/TACO) | **APTO SOLO FILTRADO**: usar únicamente las imágenes cuya licencia individual (registrada en las anotaciones) permita uso comercial (CC0, CC BY, CC BY-SA). Excluir NC y ND en la ingesta de S22, dejando el conteo de excluidas registrado |
| Garbage Dataset v2 (Kaggle · sumn2u) | MIT en la ficha de Kaggle · **CC BY 4.0 en el paper** (arXiv 2602.10500 / JBCS) | [Kaggle](https://www.kaggle.com/datasets/sumn2u/garbage-classification-v2) · [Paper GD](https://arxiv.org/html/2602.10500v2) | **APTO CONDICIONAL — RIESGO LEGAL ABIERTO** (decisión de Juan 06/08/2026: se usa para no frenar el camino crítico; revisión legal pendiente ANTES de comercializar). Ver advertencia y análisis de linaje abajo |
| Garbage Classification 12c (Kaggle · mostafaabla) | BD: ODbL · **imágenes: © autores originales**, obtenidas por web scraping; el autor declara uso sin lucro | [Kaggle](https://www.kaggle.com/datasets/mostafaabla/garbage-classification) | **NO APTO**. Excluido del pipeline |
| ZeroWaste (BU, CVPR 2022) | CC BY-NC 4.0 | [Página oficial](https://ai.bu.edu/zerowaste/) | **NO APTO** (NC explícito). Excluido del pipeline |

### Garbage Dataset v2 — advertencia y análisis de linaje

> [!CAUTION]
> **<ins>RIESGO LEGAL ABIERTO — BLOQUEANTE PARA EL LANZAMIENTO COMERCIAL.</ins>**
> Este dataset entrena los modelos por decisión de Juan (06/08/2026) para no
> frenar el camino crítico, pero **la licencia permisiva declarada NO tiene
> cadena de derechos acreditada imagen a imagen**: una parte del contenido
> proviene de «repositorios públicos y web scraping curados» y la declaración
> del autor solo vale para lo que era suyo. **<ins>Ningún modelo entrenado con
> este dataset puede publicarse comercialmente sin revisión legal previa</ins>**
> (issue de seguimiento etiquetada `riesgo-legal` en el repositorio). Plan de
> salida si la revisión falla: reentrenar sin él (el pipeline lo excluye con
> `enabled: false`) y compensar con dataset propio — ver «Opción: dataset
> propio» al final.

Análisis de linaje (verificado 06/08/2026 sobre el paper [*The Garbage Dataset
(GD)*, arXiv 2602.10500](https://arxiv.org/html/2602.10500v2), publicado en el
Journal of the Brazilian Computer Society):

- **Doble declaración de licencia:** la ficha de Kaggle dice MIT; el paper dice
  CC BY 4.0. Ambas permiten uso comercial, pero la inconsistencia es en sí una
  señal de gestión informal de derechos. A efectos de atribución se cumple la
  más exigente (CC BY 4.0: cita al autor y al paper).
- **Composición declarada** (el paper NO publica fracciones numéricas por
  origen; la Figura 6 las muestra solo como gráfico): (1) fotos propias vía la
  app DWaste del autor — derechos limpios; (2) imágenes de repositorios
  públicos y web scraping «curadas» — **origen del riesgo**; (3) contribuciones
  de la comunidad — derechos dependientes de cada contribuyente.
- **Mitigantes documentados:** partió de 20 212 imágenes y quedó en 12 259 tras
  limpieza que el paper describe como deduplicación, verificación de integridad
  y «copyright checks»; cada imagen fue verificada por al menos tres
  voluntarios; el paper declara que GD **no incorpora datasets previos** (revisa
  TrashNet, TACO, etc. solo como estado del arte). Publicación revisada por
  pares con DOI: hay diligencia debida documentable y un responsable citable.
- **Peso en el pool:** con el pool comercial activo (GD v2 ~13,3k + TrashNet
  2,5k + TACO filtrado ~3k recortes), GD v2 aporta **~70 % de las imágenes de
  entrenamiento**. Excluirlo deja ~30 % del pool: por eso su caída sin
  reemplazo hace inalcanzable RNF-008 y por eso existe el plan de dataset
  propio como salida definitiva.

## Pesos preentrenados y modelos base

| Activo | Licencia de distribución | Evidencia | Veredicto comercial |
|---|---|---|---|
| MobileNetV3-Small / Large 0.75 (pesos ImageNet) | torchvision: BSD-3-Clause · checkpoints oficiales de Google (TF/Keras): Apache 2.0 | [torchvision LICENSE](https://github.com/pytorch/vision/blob/main/LICENSE) · [keras-applications](https://github.com/keras-team/keras) | **APTO con nota** (ver abajo). Preferir los checkpoints Apache 2.0 de Google |
| EfficientNet-Lite2 (pesos ImageNet) | Apache 2.0 (repo oficial tensorflow/tpu; también vía timm, Apache 2.0) | [tensorflow/tpu — efficientnet-lite](https://github.com/tensorflow/tpu/tree/master/models/official/efficientnet/lite) | **APTO con nota** |
| U²-Net (segmentación para síntesis de contaminación, S24) | Apache 2.0 (repo y pesos publicados en él) | [xuebinqin/U-2-Net](https://github.com/xuebinqin/U-2-Net) | **APTO con nota**. Solo se usa **fuera de la app**, como herramienta del pipeline: sus salidas son derivados de imágenes ya licenciadas de nuestro dataset, y el modelo no se empaqueta ni distribuye |
| LiteRT (runtime, ámbito EDGE) | Apache 2.0 | [google-ai-edge](https://github.com/google-ai-edge/LiteRT) | APTO (se registra aquí por completitud) |

**Nota sobre pesos preentrenados («APTO con nota»):** las licencias de
*distribución* de los pesos (BSD-3/Apache 2.0) permiten uso comercial. Existe un
debate jurídico no resuelto sobre pesos entrenados en datasets con términos de
acceso solo-investigación (ImageNet para los backbones; DUTS-TR para U²-Net).
La práctica de industria generalizada es tratarlos como utilizables
comercialmente (Google, Meta y otros los publican y usan en productos), y esos
términos vinculan a quien descargó el dataset, no automáticamente a quien usa
los pesos. Riesgo residual bajo, pero **la aceptación es decisión de Juan**; la
alternativa conservadora (entrenar backbones desde cero o usar pesos entrenados
en datasets abiertamente licenciados) cuesta exactitud y tiempo de cómputo.

## Fuentes nuevas para clases débiles (auditoría 06/08/2026)

Búsqueda dirigida para `ORGANIC`, `TEXTILE`, `BATTERY`, `ELECTRONIC` y
`BEVERAGE_CARTON` (decisión de Juan: una sola pasada, solo licencias aptas):

| Activo | Licencia verificada | Evidencia | Veredicto comercial |
|---|---|---|---|
| Clothing Dataset (Grigorev) | **CC0 1.0** (dominio público) | [Repo](https://github.com/alexeygrigorev/clothing-dataset) | **APTO** — 5 000+ imágenes, 20 clases de ropa contribuidas voluntariamente. Cubre `TEXTILE` |
| Fresh & Rotten Fruits (Mendeley, Jahangirnagar Univ.) | **CC BY 4.0** | [Ficha Mendeley bdd69gyhv8](https://data.mendeley.com/datasets/bdd69gyhv8/1) | **APTO** — 3 200 imágenes originales (8 frutas × fresco/podrido; se usan las originales, no sus augmentadas). Cubre `ORGANIC` |
| Open Images V7 (subset dirigido) | Anotaciones CC BY 4.0 · imágenes listadas CC BY 2.0 con advertencia de verificación individual | [Ficha oficial](https://storage.googleapis.com/openimages/web/factsfigures_v7.html) | **APTO CON FILTRADO por imagen** (mismo mecanismo que TACO). Recortes de bbox: Tin can → `METAL`; Coffee cup → `BEVERAGE_CARTON` **con curación manual por muestreo** (mezcla vasos con tazas de cerámica). Las clases de electrónica quedan anotadas en el mapeo para v2 (la detección de `ELECTRONIC` se difirió por decisión de Juan) |
| RecyBat24 (Scientific Data 2025) | CC BY-**NC-ND** 4.0 | [Paper](https://pmc.ncbi.nlm.nih.gov/articles/PMC12098665/) | **NO APTO** (NC-ND). Descartado |
| E-waste de Roboflow Universe («E-Waste Dataset» ~19,6k; «Balanced E-Waste» ~7,2k) y Kaggle (akshat103) | **Por verificar** (Roboflow bloquea la consulta automatizada; cada ficha declara su licencia) | [Roboflow](https://universe.roboflow.com/electronic-waste-detection/balanced-e-waste-dataset) · [Kaggle](https://www.kaggle.com/datasets/akshat103/e-waste-image-dataset) | **CANDIDATOS** — no entran al pipeline hasta verificar licencia en su ficha y registrarla aquí. Solo se necesitarían si Open Images resulta insuficiente para `ELECTRONIC` |
| Datasets Tetra Pak / drink carton de Roboflow Universe | **Por verificar** (ídem) | [Búsqueda](https://universe.roboflow.com/search?q=class%3A%22drink+carton%22) | **CANDIDATOS** para `BEVERAGE_CARTON`; misma condición |

## Opción registrada: dataset propio (salida definitiva al riesgo legal)

Juan contempla construir un dataset propio. Coste estimado con captura móvil
clasificada en carpetas (etiquetado implícito, sin herramienta de anotación):

- **Cobertura completa (11 clases):** 500–1 000 fotos/clase = 5 500–11 000
  fotos. A 15–30 s por foto con variedad real de fondos, luz y estados del
  residuo: **25–90 h de captura** + 15–20 h de control de calidad y
  deduplicación. Elimina de raíz todo riesgo de procedencia y es el único
  camino con derechos 100 % propios.
- **Quirúrgico (solo `BEVERAGE_CARTON`, la clase estrella):** 300–500 fotos de
  vasos de café y envases Tetra Pak en estados limpio/contaminado: **3–5 h de
  captura**. Máximo retorno por hora invertida; recomendado hacerlo pronto
  aunque no se haga el completo, porque ninguna fuente pública apta cubre bien
  esta clase.

## Consecuencias aplicadas

1. `garbage_classification_12` y `zerowaste` quedan deshabilitados en
   `taxonomy/label_mapping.yaml` (`enabled: false`) y la ingesta de S22 los
   ignora. Sus mapeos se conservan por si su situación legal cambiara.
2. `garbage_dataset_v2` está **habilitado por decisión de Juan (06/08/2026)**
   con el riesgo legal abierto documentado arriba y una issue `riesgo-legal`
   bloqueante para el lanzamiento comercial, no para el desarrollo.
3. TACO y Open Images entran con **filtrado obligatorio por licencia de
   imagen** en S22; el pipeline registra cuántas imágenes se excluyen y su
   efecto en el balance.
4. Todo activo nuevo (dataset de e-waste para la issue #54, texturas de
   contaminación para S24, cualquier peso) se registra aquí ANTES de usarse,
   con el mismo formato y veredicto.
5. Las atribuciones exigidas por CC BY / MIT / Apache de los activos APTOS se
   compilarán en el aviso de licencias de la app (coordinación con RELEASE en
   su momento).
