# Inventario de datasets — BotaBien ML

Sesión S21 · Requerimientos RNF-016, RNF-017 · Última verificación de licencias: **06/08/2026**.

Este documento es la fuente de verdad sobre qué conjuntos de datos públicos usa el
pipeline de ML, bajo qué licencia, con qué rol y con qué riesgos. Ningún dataset
entra al pipeline sin estar inventariado aquí y sin mapeo en
[`taxonomy/label_mapping.yaml`](taxonomy/label_mapping.yaml).

## Criterio de compatibilidad de licencias

**La app se comercializará: solo entran activos aptos para uso comercial.** El
registro legal de procedencia, con evidencia y veredicto por activo (datasets,
pesos preentrenados y herramientas del pipeline), es
[`DATA_LICENSES.md`](DATA_LICENSES.md) — ese archivo manda sobre este. Un
dataset NO APTO o AMBIGUO no se usa en ninguna etapa aunque mejore las métricas.
Las imágenes de los datasets **nunca se redistribuyen** desde este repositorio:
el repo solo contiene scripts, mapeos y métricas; los datos se descargan de la
fuente original en la máquina de quien ejecuta el pipeline (`ml/data/` está en
gitignore).

## Resumen

| Dataset | Versión/fecha | Imágenes | Clases | Veredicto comercial | Rol en el pipeline |
|---|---|---|---|---|---|
| RealWaste | UCI #908 (2023) | 4 752 | 9 | APTO (CC BY 4.0) | **Control — nunca entrena** |
| TrashNet | 2016 | 2 527 | 6 | APTO (MIT, fotos propias de los autores) | Entrenamiento |
| TACO | Zenodo 3587843 (2019) | 1 500 (4 784 anotaciones) | 60+ categorías | APTO SOLO FILTRADO por licencia de imagen | Entrenamiento (recortes de bbox del subconjunto comercial) |
| Garbage Dataset v2 | actual en Kaggle (act. 2026) | 13 348 | 10 | **APTO CONDICIONAL — riesgo legal abierto** (bloque CAUTION en `DATA_LICENSES.md`) | Entrenamiento principal por decisión de Juan; revisión legal antes de comercializar |
| Clothing Dataset (Grigorev) | 2020 | 5 000+ | 20 → `TEXTILE` | APTO (CC0 1.0) | Entrenamiento (refuerzo de textil) |
| Fresh & Rotten Fruits (Mendeley) | 2022 | 3 200 originales | 16 → `ORGANIC` | APTO (CC BY 4.0) | Entrenamiento (refuerzo de orgánico) |
| Open Images V7 (subset dirigido) | V7 | recortes de 2 clases | → `METAL`, `BEVERAGE_CARTON` | APTO CON FILTRADO por imagen | Entrenamiento (refuerzo del caso estrella) |
| Garbage Classification 12c | 2021 | 15 150 | 12 | **NO APTO** (imágenes © autores originales) | **Excluido** |
| ZeroWaste | CVPR 2022 | 12 125 (3 variantes) | 4 | **NO APTO** (CC BY-NC 4.0) | **Excluido** |

Veredictos, evidencia y justificación por activo en
[`DATA_LICENSES.md`](DATA_LICENSES.md). Los descartes a nivel de **etiqueta**
(clases multimaterial, ambiguas o sin material definido) están justificados uno a
uno en `taxonomy/label_mapping.yaml`; los datasets excluidos conservan su mapeo
con `enabled: false` por si su situación legal cambiara.

---

## 1. Garbage Dataset v2 (Kaggle · sumn2u)

- **URL:** https://www.kaggle.com/datasets/sumn2u/garbage-classification-v2
- **Licencia:** MIT (declarada en la ficha de Kaggle; verificada 06/08/2026).
- **Contenido:** 13 348 imágenes en 10 clases: battery (756), biological (699),
  cardboard (1 411), clothes (1 892), glass (1 736), metal (930), paper (1 336),
  plastic (1 597), shoes (1 449), trash (453).
- **Cita:** Kunwar, S. — *Managing Household Waste Through Transfer Learning* y
  *The Garbage Dataset (GD): A Multi-Class Image Benchmark for Automated Waste
  Segregation*.
- **Descarga:** `kaggle datasets download -d sumn2u/garbage-classification-v2`
  (requiere token de la API de Kaggle; la CLI se añade fijada en S22).
- **Rol:** entrenamiento principal, **en uso condicional con riesgo legal
  abierto** por decisión de Juan (06/08/2026). El paper del dataset (*The
  Garbage Dataset (GD)*, arXiv 2602.10500) declara CC BY 4.0, describe captura
  propia vía la app DWaste + web curada con verificación de copyright, y niega
  incorporar datasets previos; aun así la parte de origen web no tiene cadena
  de derechos imagen a imagen. **Revisión legal pendiente antes del lanzamiento
  comercial** — ver el bloque CAUTION y el análisis de linaje completo en
  `DATA_LICENSES.md`, y la issue etiquetada `riesgo-legal`.
- **Nota:** el plan de trabajo lo estimaba en ~19,7k imágenes; la versión vigente
  publica 13 348. El conteo real se registra en la ingesta (S22).

## 2. Garbage Classification, 12 clases (Kaggle · mostafaabla)

- **URL:** https://www.kaggle.com/datasets/mostafaabla/garbage-classification
- **Licencia:** «Database: Open Database (ODbL) · Contents: © Original Authors»
  (ficha de Kaggle, verificada 06/08/2026). Las imágenes fueron recolectadas en su
  mayoría por *web scraping*; el autor declara uso para investigación sin lucro y
  retiro a petición del propietario.
- **Contenido:** 15 150 imágenes en 12 clases: battery, biological, brown-glass,
  green-glass, white-glass, cardboard, clothes, metal, paper, plastic, shoes, trash.
- **Procedencia declarada:** la clase clothes y ~22 % de shoes provienen del
  *Clothing dataset* (Kaggle); ~29 % de las otras 9 clases proviene del *Garbage
  Classification* de asdasdasasdas; el resto es scraping.
- **Descarga:** `kaggle datasets download -d mostafaabla/garbage-classification`.
- **Rol:** **EXCLUIDO por licencia (NO APTO comercial).** Las imágenes son
  © de sus autores originales, obtenidas por web scraping, con declaración
  expresa de uso sin lucro. Incompatible con la comercialización de la app.
  Su mapeo se conserva deshabilitado (`enabled: false`) por si su situación
  legal cambiara.

## 3. RealWaste (UCI Machine Learning Repository #908)

- **URL:** https://archive.ics.uci.edu/dataset/908/realwaste
- **Licencia:** CC BY 4.0 (ficha de UCI, verificada 06/08/2026).
- **Contenido:** 4 752 imágenes 524×524 tomadas en el punto de recepción de un
  relleno sanitario (Wollongong, Australia): Cardboard (461), Food Organics (411),
  Glass (420), Metal (790), Miscellaneous Trash (495), Paper (500), Plastic (921),
  Textile Trash (318), Vegetation (436).
- **Cita:** Single, S., Iranmanesh, S., Raad, R. (2023). *RealWaste: A Novel
  Real-Life Data Set for Landfill Waste Classification Using Deep Learning*.
  Information 14(12), 633.
- **Descarga:** https://archive.ics.uci.edu/static/public/908/realwaste.zip
- **Rol:** **conjunto de control. Queda excluido del entrenamiento y de cualquier
  ajuste de hiperparámetros.** Es la única fuente con residuos reales degradados
  fotografiados fuera de estudio, y no comparte origen con ningún otro dataset del
  inventario (recolección propia de los autores), así que no hay riesgo de fuga.
  La evaluación cruzada de S28 se reporta sobre RealWaste.

## 4. TrashNet (Thung & Yang, Stanford)

- **URL:** https://github.com/garythung/trashnet · espejo
  https://huggingface.co/datasets/garythung/trashnet
- **Licencia:** MIT (LICENSE del repositorio, verificada 06/08/2026).
- **Contenido:** 2 527 imágenes sobre fondo blanco: glass (501), paper (594),
  cardboard (403), plastic (482), metal (410), trash (137).
- **Cita:** Thung, G., Yang, M. (2016). *Classification of Trash for
  Recyclability Status*.
- **Descarga:** dataset redimensionado desde Hugging Face; el original (~3,5 GB)
  desde el Drive enlazado en el repo.
- **Rol:** complemento de entrenamiento. Su fondo limpio y uniforme está lejos del
  dominio móvil real: la augmentación de S23 es la que lo acerca. Es además el
  dataset clásico del área, útil para comparar con literatura.

## 5. TACO — Trash Annotations in Context

- **URL:** https://github.com/pedropro/TACO · DOI
  https://doi.org/10.5281/zenodo.3587843
- **Licencia:** el paquete de Zenodo (TACO.zip, 2,7 GB, imágenes + anotaciones
  COCO) está publicado bajo CC BY 4.0 (verificada 06/08/2026). El código es MIT.
  Las fotos originales provienen de Flickr con licencia libre declarada por imagen
  en las anotaciones; se respeta la atribución por imagen que exija su licencia.
- **Contenido:** ~1 500 imágenes de basura en contexto real (calles, bosque,
  playas) con 4 784 segmentaciones en 60 categorías / 28 supercategorías.
- **Cita:** Proença, P.F., Simões, P. (2020). *TACO: Trash Annotations in Context
  for Litter Detection*. arXiv:2003.06975.
- **Descarga:** `TACO.zip` desde Zenodo (autocontenido) o `python download.py` del
  repo oficial.
- **Rol:** entrenamiento vía **recortes de bounding box** (es un dataset de
  detección, no de clasificación): cada anotación se recorta y se etiqueta según el
  mapeo por categoría. Aporta el dominio más parecido al de la app (objetos en
  contexto real, fondos sucios) y es la **única fuente de `BEVERAGE_CARTON`**
  (categorías Drink carton y Paper cup).
- **Restricción comercial:** las imágenes heredan la licencia individual de
  Flickr registrada en las anotaciones. La ingesta de S22 **solo admite las de
  licencia apta para uso comercial** (CC0, CC BY, CC BY-SA) y excluye NC/ND,
  registrando cuántas se pierden por clase.

## 6. ZeroWaste (Boston University, CVPR 2022)

- **URL:** https://ai.bu.edu/zerowaste/
- **Licencia:** CC BY-NC 4.0 (página oficial, verificada 06/08/2026). **Solo uso
  no comercial** — ver la condición registrada al inicio.
- **Contenido:** 12 125 imágenes en 3 variantes (ZeroWaste-f 4 503, -s 6 212,
  -w 1 410) de una banda transportadora de planta de reciclaje, con segmentación en
  4 clases: rigid_plastic, soft_plastic, cardboard, metal.
- **Cita:** Bashkirova, D. et al. (2022). *ZeroWaste Dataset: Towards Deformable
  Object Segmentation in Cluttered Scenes*. CVPR 2022.
- **Descarga:** ZIPs de Zenodo enlazados desde la página del proyecto (7,0 GB +
  9,9 GB + 3,0 GB).
- **Rol:** **EXCLUIDO por licencia (NO APTO comercial: CC BY-NC 4.0).** Era
  opcional por desalineación de dominio (vista cenital de banda transportadora);
  la restricción NC lo saca del pipeline con independencia de esa discusión.
  Mapeo conservado deshabilitado.

## 7–9. Fuentes añadidas en la auditoría comercial

Clothing Dataset (CC0, → `TEXTILE`), Fresh & Rotten Fruits de Mendeley
(CC BY 4.0, → `ORGANIC`) y el subset dirigido de Open Images V7 (filtrado por
licencia de imagen, → `ELECTRONIC`/`METAL`/`BEVERAGE_CARTON`). Detalle,
evidencia y condiciones de cada una en
[`DATA_LICENSES.md`](DATA_LICENSES.md) § «Fuentes nuevas para clases débiles»;
mapeo en `taxonomy/label_mapping.yaml` (versión 3).

---

## Solapamiento entre fuentes y reglas de higiene

1. S22 aplica deduplicación perceptual (pHash con umbral por distancia de
   Hamming) sobre la unión de todas las fuentes de entrenamiento activas antes
   de particionar. Si Garbage v2 se rehabilita, la deduplicación contra TrashNet
   es obligatoria (TrashNet es ancestro frecuente de los agregados de Kaggle).
2. **Las particiones train/val se hacen después de deduplicar**, con semilla fija.
3. **RealWaste no participa** ni del entrenamiento ni de la deduplicación de
   train (solo se verifica que ninguna imagen de train colisione con él).

## Brechas de cobertura de la taxonomía (tras la auditoría del 06/08/2026)

Pool activo: Garbage v2 (condicional) + TrashNet + TACO filtrado + Clothing +
Rotten Fruits + Open Images filtrado; RealWaste solo control.

| Material | Situación | Acción |
|---|---|---|
| `BEVERAGE_CARTON` | **Sigue siendo la clase más débil**: TACO filtrado (decenas de instancias) + Open Images «Coffee cup» (requiere curación manual: mezcla tazas de cerámica) | S22 mide conteos reales; S23 augmentación agresiva; S24 evalúa síntesis. **Recomendada la captura propia quirúrgica (300–500 fotos, 3–5 h)** registrada en `DATA_LICENSES.md` |
| `ELECTRONIC` | **Detección automática diferida a v2** (decisión de Juan, 06/08/2026). En v1 la ruta existe vía selección manual y desambiguación por baja confianza (CUS-006). El modelo mantiene los 11 logits del contrato EDGE (la clase simplemente nunca gana) | Clases de Open Images anotadas en el mapeo para reactivar en v2 (#54) |
| `BATTERY` | Garbage v2 (756) + TACO marginal; depende del dataset condicional | Si Garbage v2 cae en la revisión legal, esta clase pierde su fuente principal |
| `ORGANIC`, `TEXTILE` | Cubiertas con las fuentes nuevas + Garbage v2; brecha de dominio en Clothing/Fruits (objetos no desechados) | Augmentación S23; RealWaste mide la transferencia |
| `PLASTIC`, `PAPER`, `CARDBOARD`, `GLASS`, `METAL`, `RESIDUAL` | Cubiertas por 3+ fuentes | Balance real se mide en S22 |

**Dependencia registrada:** ~70 % del pool de entrenamiento es Garbage v2, que
está en uso condicional con riesgo legal abierto. La suerte de RNF-008 depende
de esa revisión o del dataset propio.

## Qué NO contiene este inventario

- Datasets con licencia desconocida o cerrada: no se aceptan.
- Recolección o etiquetado manual propio: fuera del alcance del proyecto por
  decisión registrada en `context-for-vibe-coding.md`.
- Imágenes dentro del repositorio: `ml/data/` está ignorado por git.
