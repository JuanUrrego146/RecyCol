# ml/ — pipeline de datos y modelos

Módulo Python del agente ML (milestone M4), aislado de la app. Corre en el
contenedor definido en `docker/ml.Dockerfile`:

```bash
docker compose run --rm ml bash
```

## Estructura

| Ruta | Contenido |
|---|---|
| `DATASETS.md` | Inventario de fuentes públicas, licencias verificadas, roles y riesgos (S21) |
| `taxonomy/label_mapping.yaml` | Mapeo versionado etiqueta de origen → `WasteMaterial` (S21) |
| `taxonomy/validate_mapping.py` | Validador del mapeo: `python taxonomy/validate_mapping.py` |
| `data/` | Datasets descargados localmente — ignorado por git |

Las sesiones siguientes añaden: ingesta y particiones reproducibles (S22),
augmentación de dominio móvil (S23), síntesis de contaminación (S24),
entrenamiento (S25–S26), cuantización y export a LiteRT (S27) y el reporte de
métricas de evaluación cruzada (S28, `REPORTE_METRICAS.md`).

Reglas fijas del pipeline (de `context-for-vibe-coding.md`):

- Ningún dataset se mezcla sin pasar por `taxonomy/label_mapping.yaml`.
- Toda fuente se documenta con su licencia en `DATASETS.md` antes de usarse.
- La exactitud se reporta siempre sobre un dataset no visto en entrenamiento
  (RealWaste es el conjunto de control) e incluye el acierto de ruta además
  del top-1 de material.
- El modelo predice materiales, nunca canecas.
