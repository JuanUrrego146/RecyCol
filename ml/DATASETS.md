# Inventario de datasets — BotaBien ML

Sesión S21 · Requerimientos RNF-016, RNF-017 · Última verificación de licencias: **06/08/2026**.

Este documento es la fuente de verdad sobre qué conjuntos de datos públicos usa el
pipeline de ML, bajo qué licencia, con qué rol y con qué riesgos. Ningún dataset
entra al pipeline sin estar inventariado aquí y sin mapeo en
[`taxonomy/label_mapping.yaml`](taxonomy/label_mapping.yaml).

## Criterio de compatibilidad de licencias

BotaBien es un proyecto académico sin fines comerciales. Se aceptan licencias que
permitan uso, modificación y trabajo derivado con atribución (MIT, CC BY 4.0) y,
**condicionado al carácter no comercial del proyecto**, licencias NC (CC BY-NC 4.0).
Las imágenes de los datasets **nunca se redistribuyen** desde este repositorio: el
repo solo contiene scripts, mapeos y métricas; los datos se descargan de la fuente
original en la máquina de quien ejecuta el pipeline (`ml/data/` está en gitignore).

> **Condición registrada:** si el proyecto llegara a comercializarse, ZeroWaste
> (CC BY-NC) y Garbage Classification 12c (contenido © autores originales) deben
> retirarse del pipeline y reentrenarse los modelos sin ellos. El pipeline de S22
> permite excluir un dataset por configuración precisamente por esto.

## Resumen

| Dataset | Versión/fecha | Imágenes | Clases | Licencia verificada | Rol en el pipeline |
|---|---|---|---|---|---|
| Garbage Dataset v2 | actual en Kaggle (act. 2026) | 13 348 | 10 | MIT | Entrenamiento (principal) |
| Garbage Classification 12c | 2021 | 15 150 | 12 | BD: ODbL · imágenes: © autores originales | Entrenamiento (con advertencia) |
| RealWaste | UCI #908 (2023) | 4 752 | 9 | CC BY 4.0 | **Control — nunca entrena** |
| TrashNet | 2016 | 2 527 | 6 | MIT | Entrenamiento (complemento) |
| TACO | Zenodo 3587843 (2019) | 1 500 (4 784 anotaciones) | 60+ categorías | CC BY 4.0 (Zenodo) · código MIT | Entrenamiento (recortes de bbox) |
| ZeroWaste | CVPR 2022 | 12 125 (3 variantes) | 4 | CC BY-NC 4.0 | Robustez opcional (recortes) — decidir en S22 |

Ningún dataset se descarta por licencia. Los descartes a nivel de **etiqueta**
(clases multimaterial, ambiguas o sin material definido) están justificados uno a
uno en `taxonomy/label_mapping.yaml`.

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
- **Rol:** columna vertebral del entrenamiento: es el conjunto curado más grande
  con las clases alineadas a la taxonomía.
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
- **Rol:** entrenamiento; aporta la separación de vidrio por color (se colapsa a
  `GLASS`) y volumen en textil.
- **Riesgo registrado:** (a) licencia de las imágenes no uniforme — aceptable solo
  bajo el criterio académico de arriba; (b) **solapamiento probable** con Garbage
  Dataset v2 (ecosistema común de datasets de basura en Kaggle). Mitigación
  obligatoria en S22: deduplicación perceptual (pHash) entre todas las fuentes de
  entrenamiento, y jamás usar este par como train/control mutuo.

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
- **Rol:** **opcional, pendiente de decisión en S22.** Su dominio (vista cenital
  de banda transportadora, objetos aplastados y solapados) está muy lejos del móvil
  en mano; su valor es robustez extra en plástico deformado, cartón y metal vía
  recortes. Se incorpora solo si el balance de clases de S22 lo justifica; si no,
  se descarta por desalineación de dominio (no por licencia) y se registra aquí.

---

## Solapamiento entre fuentes y reglas de higiene

1. **Garbage v2 ↔ Garbage 12c:** comparten ecosistema y posiblemente miles de
   imágenes (ambos beben de datasets previos de Kaggle). S22 aplica deduplicación
   perceptual (pHash con umbral por distancia de Hamming) sobre la unión de todas
   las fuentes de entrenamiento antes de particionar.
2. **TrashNet:** es ancestro frecuente de datasets de Kaggle; se deduplica igual.
3. **Las particiones train/val se hacen después de deduplicar**, con semilla fija.
4. **RealWaste no participa** ni del entrenamiento ni de la deduplicación de
   train (solo se verifica que ninguna imagen de train colisione con él).

## Brechas de cobertura de la taxonomía

| Material | Situación | Acción |
|---|---|---|
| `ELECTRONIC` | **Sin fuente**: ninguno de los seis datasets tiene clase de aparatos eléctricos/electrónicos | Registrada issue de coordinación: decidir entre incorporar una fuente de e-waste con licencia compatible o declarar que el modelo v1 no predice `ELECTRONIC` (el contrato lo tolera: el enum es cerrado pero el modelo puede cubrir un subconjunto) |
| `BEVERAGE_CARTON` | Crítico y escaso: solo TACO (Drink carton + Paper cup, decenas de instancias) | S22 mide el conteo real tras recortes; S23 compensa con augmentación agresiva de esa clase; si sigue siendo insuficiente se evalúa síntesis dedicada en S24. Es la clase del caso de uso estrella (vaso de café) y su cobertura se monitorea en cada sesión |
| Resto (9) | Cubiertos por 2+ fuentes independientes | — |

## Qué NO contiene este inventario

- Datasets con licencia desconocida o cerrada: no se aceptan.
- Recolección o etiquetado manual propio: fuera del alcance del proyecto por
  decisión registrada en `context-for-vibe-coding.md`.
- Imágenes dentro del repositorio: `ml/data/` está ignorado por git.
