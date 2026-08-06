# ml/ — pipeline de datos y modelos

Módulo Python del agente ML (milestone M4), aislado de la app. Corre en el
contenedor definido en `docker/ml.Dockerfile`:

```bash
docker compose run --rm ml bash
```

## Estructura

| Ruta | Contenido |
|---|---|
| `DATA_LICENSES.md` | **Registro legal de procedencia**: licencia, evidencia y veredicto comercial de cada dataset, peso preentrenado y herramienta — manda sobre el resto |
| `DATASETS.md` | Inventario de fuentes públicas, roles y riesgos (S21) |
| `taxonomy/label_mapping.yaml` | Mapeo versionado etiqueta de origen → `WasteMaterial` (S21) |
| `taxonomy/validate_mapping.py` | Validador del mapeo: `python taxonomy/validate_mapping.py` |
| `data/` | Datasets descargados localmente — ignorado por git |

| `ingest/pipeline.py` | S22: extracción, normalización, dedup dHash y particiones deterministas (`python ingest/pipeline.py`) |
| `augment/mobile_domain.py` | S23: augmentación de dominio móvil (`--preview N` genera hoja de contacto) |
| `contaminate/synthesize.py` | S24: síntesis de contaminación con U²-Net (requiere `data/models/u2net.onnx`) |
| `train/train_material.py` | S25: clasificador de material por gama (`--variant low\|mid\|high`, `--smoke`) |
| `train/train_contamination.py` | S26: clasificador binario CLEAN/CONTAMINATED |
| `export/export_litert.py` | S27: cuantización INT8 y export a los nombres del contrato EDGE |

Verificación de reproducibilidad de S22 (dos corridas → manifiestos idénticos):

```bash
docker compose -p botabien-ml run --rm ml sh -c "python ingest/pipeline.py && cp -r data/manifests /tmp/run1 && python ingest/pipeline.py && diff -r /tmp/run1 data/manifests && echo REPRODUCIBLE"
```

S28 publica `REPORTE_METRICAS.md` con la evaluación cruzada sobre RealWaste.

Reglas fijas del pipeline (de `context-for-vibe-coding.md`):

- Ningún dataset se mezcla sin pasar por `taxonomy/label_mapping.yaml`.
- Toda fuente se documenta con su licencia en `DATASETS.md` antes de usarse.
- La exactitud se reporta siempre sobre un dataset no visto en entrenamiento
  (RealWaste es el conjunto de control) e incluye el acierto de ruta además
  del top-1 de material.
- El modelo predice materiales, nunca canecas.
