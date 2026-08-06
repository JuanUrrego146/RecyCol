# Procedencia y licencias de datos y modelos — BotaBien ML

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
| Garbage Dataset v2 (Kaggle · sumn2u) | MIT declarada en la ficha | [Kaggle](https://www.kaggle.com/datasets/sumn2u/garbage-classification-v2) | **AMBIGUO**: la licencia declarada es permisiva, pero es un agregado curado cuyo linaje incluye datasets previos de Kaggle e imágenes de origen web sin cadena de derechos acreditada. La declaración MIT solo vale si quien la hizo tenía los derechos. Pendiente de decisión de Juan; mientras tanto NO entrena |
| Garbage Classification 12c (Kaggle · mostafaabla) | BD: ODbL · **imágenes: © autores originales**, obtenidas por web scraping; el autor declara uso sin lucro | [Kaggle](https://www.kaggle.com/datasets/mostafaabla/garbage-classification) | **NO APTO**. Excluido del pipeline |
| ZeroWaste (BU, CVPR 2022) | CC BY-NC 4.0 | [Página oficial](https://ai.bu.edu/zerowaste/) | **NO APTO** (NC explícito). Excluido del pipeline |

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

## Consecuencias aplicadas

1. `garbage_classification_12` y `zerowaste` quedan deshabilitados en
   `taxonomy/label_mapping.yaml` (`enabled: false`) y la ingesta de S22 los
   ignora. Sus mapeos se conservan por si su situación legal cambiara.
2. `garbage_dataset_v2` queda deshabilitado como AMBIGUO hasta decisión expresa.
3. TACO entra con **filtrado obligatorio por licencia de imagen** en S22; el
   pipeline registra cuántas imágenes se excluyen y su efecto en el balance.
4. Todo activo nuevo (dataset de e-waste para la issue #54, texturas de
   contaminación para S24, cualquier peso) se registra aquí ANTES de usarse,
   con el mismo formato y veredicto.
5. Las atribuciones exigidas por CC BY / MIT / Apache de los activos APTOS se
   compilarán en el aviso de licencias de la app (coordinación con RELEASE en
   su momento).
