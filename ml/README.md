# ml/ — pipeline de datos y modelos

Módulo Python del agente ML (milestone M4), aislado de la app. **Docker es el
único entorno**: no hay Python local en la máquina.

```bash
docker compose -p botabien-ml run --rm ml bash                      # CPU
docker compose -f docker-compose.gpu.yml -p botabien-ml-gpu run --rm ml-gpu ...   # GPU
```

El estado de M4, las métricas contra control y los hallazgos están en
[`../CONTEXTO.md` §7](../CONTEXTO.md). Aquí solo vive lo operativo.

## Estructura

| Ruta | Contenido |
|---|---|
| `DATA_LICENSES.md` | **Registro legal de procedencia**: licencia, evidencia y veredicto comercial de cada dataset, peso preentrenado y herramienta. **Manda sobre el resto** |
| `taxonomy/label_mapping.yaml` | Mapeo versionado etiqueta de origen → `WasteMaterial` |
| `taxonomy/validate_mapping.py` | Validador: `python taxonomy/validate_mapping.py` |
| `data/` · `runs/` · `reports/` · `dist/` | Datasets, checkpoints, métricas y exports — **ignorados por git** |

## Fuentes activas y cómo se descargan

Las imágenes **nunca se redistribuyen** desde este repositorio: se descargan de
la fuente original en la máquina de quien ejecuta el pipeline. Ninguna fuente
entra sin registrarse antes en `DATA_LICENSES.md` con su veredicto comercial.

| Fuente | Rol | Descarga |
|---|---|---|
| **Garbage Dataset v2** (~13,3k, 10 clases) | Entrenamiento, columna vertebral | `kaggle datasets download -d sumn2u/garbage-classification-v2` (requiere token de la API de Kaggle) |
| **TrashNet** (2 527, 6 clases) | Entrenamiento, complemento | [garythung/trashnet](https://github.com/garythung/trashnet) · espejo en Hugging Face |
| **TACO** (~1 500 img / 4 784 anotaciones) | Entrenamiento vía recortes de bbox. **Única fuente de `BEVERAGE_CARTON`** | `TACO.zip` desde [Zenodo 3587843](https://doi.org/10.5281/zenodo.3587843) — **solo el paquete de Zenodo**, nunca `download.py` contra Flickr |
| **RealWaste** (4 752, 9 clases) | 🔒 **CONTROL — nunca entrena** | https://archive.ics.uci.edu/static/public/908/realwaste.zip |
| Clothing Dataset · Fresh & Rotten Fruits · Open Images V7 (subset) | Refuerzo de `TEXTILE`, `ORGANIC`, `METAL`, `BEVERAGE_CARTON` | Enlaces y condiciones de filtrado en `DATA_LICENSES.md` |

**Excluidas por licencia** (incompatibles con uso comercial): Garbage
Classification 12c (imágenes © autores originales) y ZeroWaste (CC BY-NC 4.0).
Quedan en `label_mapping.yaml` con `enabled: false` por si su situación cambia.

### Atribución exigida

RealWaste — Single, Iranmanesh & Raad (2023), *Information* 14(12), 633 · TACO —
Proença & Simões (2020), arXiv:2003.06975 · TrashNet — Thung & Yang (2016) ·
Garbage Dataset v2 — Kunwar, S., *The Garbage Dataset (GD)*, arXiv 2602.10500.
Se compilan en el aviso de licencias de la app (coordinación con RELEASE).

## Reglas fijas del pipeline

- **Ningún dataset se mezcla sin pasar por `taxonomy/label_mapping.yaml`.**
- **El modelo predice materiales, nunca canecas.**
- **RealWaste es el conjunto de control: no entrena, no ajusta umbrales y no
  elige checkpoints.** Toda selección se hace sobre val interna.
- La exactitud se reporta siempre sobre un dataset no visto en entrenamiento e
  **incluye el acierto de ruta además del top-1 de material** — la ruta manda.
- Publica siempre la matriz colapsada por caneca junto a la de material.
- Deduplicación perceptual (pHash) sobre la unión de fuentes de entrenamiento
  **antes** de particionar, con semilla fija. RealWaste no participa de la
  deduplicación de train; solo se verifica que no colisione con él.
- `shm_size` acotado a 2 GB **a propósito**: subirlo invita al OOM killer de la
  VM de WSL2. Los runners de CI y los entrenamientos comparten esa RAM (#128).

## Brechas de cobertura conocidas

| Material | Situación |
|---|---|
| `ELECTRONIC` | **Sin fuente apta.** Decisión de Juan: en v1 va a punto de recolección especial **sin detección automática** (issue #54) |
| `BEVERAGE_CARTON` | Crítico y escaso. Es la clase del caso de uso estrella (vaso de café) y **RealWaste no la contiene**, así que hoy no es verificable contra dominio real. Su cobertura se vigila en cada sesión |
