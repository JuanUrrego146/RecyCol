# S25 · Las palancas de coste de ruta, y la varianza que las hacía indistinguibles

Tres conclusiones, en orden de importancia:

1. **La receta ganadora de M4 es la exclusión sola**: `--exclude
   garbage_dataset_v2:RESIDUAL`, sin más. Ruta contra control **70,6 %** en la
   mejor medición, **69,6 %** de media sobre dos repeticiones.
2. **Los runs no eran reproducibles** — un `hash()` de Python sembraba la
   augmentación — y la varianza real entre dos ejecuciones de la *misma*
   configuración es **±2,16 pp de ruta**. Toda comparación anterior del proyecto
   se hizo sin saberlo.
3. **Las palancas sensibles a coste empeoran**: −5,9 pp, que es **2,7 veces la
   varianza medida**. El efecto es real, no ruido.

Fecha: 07/08/2026 · variante `low` · RTX 3060 Ti.

## El bug de reproducibilidad

`train/train_material.py` sembraba la augmentación de cada época con
`hash((name, epoch))`. `hash()` de Python **está aleatorizado por proceso** salvo
que `PYTHONHASHSEED` esté fijado, y la imagen de ML no lo fija. Comprobado: tres
ejecuciones del mismo `hash()` devuelven `2819`, `56575`, `24311`.

Consecuencia: cada run augmentaba distinto y **dos runs de la misma configuración
no eran comparables**, mientras el módulo declaraba en su docstring
«reproducible por imagen+época». El resto del pipeline sí era determinista: era
el único `hash()` de Python en todo `ml/`. Corregido con md5, igual que ya hacían
`rng_for` y `val_bucket`.

Esto importa porque **es exactamente así como se toman las decisiones de M4**:
comparando runs entre sí contra el control.

## Varianza medida

`no-trash` y `no-trash-seed` son la **misma configuración** ejecutada dos veces:

| Métrica | `no-trash` | `no-trash-seed` | Δ |
|---|---|---|---|
| top-1 | 44,1 % | 46,2 % | 2,08 pp |
| **ruta** | 68,5 % | **70,6 %** | **2,16 pp** |
| ruta macro | 69,0 % | 71,1 % | 2,14 pp |

**Regla operativa que sale de aquí: una diferencia menor de ~2 pp entre dos runs
no significa nada.** Eso afecta retroactivamente al barrido de arquitectura de
`ml/runs/sweep_summary.md`, cuyo «ganador provisional» (EfficientNet-B2, 98,35 %
de ruta en val) aventajaba al segundo en 0,13 pp — muy por debajo del ruido. Ese
ganador **no está establecido**.

## Resultado contra control, todos los runs

| Run | top-1 | **ruta** | ruta macro |
|---|---|---|---|
| baseline (sin v2) | 39,3 % | 65,9 % | 66,7 % |
| `full-v2` | 42,1 % | 61,4 % | 63,0 % |
| `no-trash` | 44,1 % | 68,5 % | 69,0 % |
| **`no-trash-seed`** | **46,2 %** | **70,6 %** | **71,1 %** |
| `no-trash-cost` (palancas) | 41,8 % | 63,6 % | 64,0 % |

## Veredicto sobre las palancas

`no-trash-cost` = misma exclusión + `--route-cost 0.5` + `--select route-macro`.
Es la primera vez que se ejecutan estas dos palancas.

**63,6 % frente a 69,6 % de media sin palancas: −5,9 pp.** Con la varianza en
±2,16 pp, el efecto es 2,7 veces el ruido: **empeoran de verdad.**

El daño se ve en el error caro — reciclable que acaba en la caneca negra:

| Run | blanca → negra | blanca acertada | verde acertada |
|---|---|---|---|
| baseline | 447 | 2 637 | 124 |
| `full-v2` | 920 | 2 131 | 270 |
| `no-trash` | 495 | 2 546 | 306 |
| **`no-trash-seed`** | 516 | 2 518 | **401** |
| `no-trash-cost` | **734** | 2 345 | 255 |

La pérdida sensible a coste, diseñada precisamente para reducir el cruce de
caneca, lo **aumenta** de ~505 a 734.

### Por qué, probablemente

La pérdida de coste optimiza la ruta **en el dominio de entrenamiento**, donde la
ruta ya está en 97–98 %. Ahí no queda margen que ganar: el término solo puede
rigidizar el modelo alrededor de las fronteras del dominio limpio, y eso se paga
al generalizar al control, que es dominio degradado. Es sobreajuste dirigido a un
objetivo que ya estaba saturado.

Esto es coherente con el hallazgo central de M4 — la val interna y el control no
miden lo mismo — y sugiere que **una pérdida sensible a coste solo ayudaría si se
calibrase contra un conjunto del dominio de destino**, cosa que el control no
puede hacer sin dejar de ser control.

**No se descarta la idea, se descarta esta configuración.** Queda sin probar un
peso mucho menor (0,1) y `--select route-macro` por separado; con ±2,16 pp de
ruido, cada una de esas preguntas cuesta al menos dos runs para responderse con
honestidad.

## ORGANIC: la ganancia real

| Run | top-1 de ORGANIC |
|---|---|
| baseline | 14,6 % |
| `full-v2` | 31,9 % |
| `no-trash` | 36,1 % |
| **`no-trash-seed`** | **47,3 %** |
| `no-trash-cost` | 30,1 % |

De 14,6 % a 47,3 % es la mejora más grande de M4 en una clase, y ORGANIC es
caneca propia (verde): cada acierto ahí es un error caro evitado.

## Estado de RNF-008

**Sigue sin cumplirse.** Exige ≥ 85 % de material y ≥ 95 % de ruta; el mejor run
da 46,2 % y 70,6 %. La brecha restante es de **dominio**: el mismo checkpoint da
90,5 % de top-1 y 98,1 % de ruta en val interna.

## Receta final para las variantes `mid` y `high`

```
--exclude garbage_dataset_v2:RESIDUAL
```

Sin `--route-cost`, sin `--select route-macro`. Batch 64 (`low`/`mid`) o 32
(`high`), AMP, 3+12 épocas, lr 1e-3 / 1e-4.
