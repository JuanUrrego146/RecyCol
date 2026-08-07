# S25 · Exclusión de la carpeta `trash` de Garbage v2 — experimento decisivo

**Veredicto: el diagnóstico de la auditoría de REGLAS (#23) queda confirmado.**
La ruta contra control sube a **68,5 %**, por encima del criterio de éxito
(≥ 65 %) y por encima del baseline, **conservando y mejorando** la ganancia de
ORGANIC que trajo v2.

Fecha: 07/08/2026 · variante `low` (MobileNetV3-Small 224) · RTX 3060 Ti.

## Diseño

Una sola variable cambiada respecto a `full-v2`:
`--exclude garbage_dataset_v2:RESIDUAL` (350 filas de train, 51 de val). Todo lo
demás idéntico — misma partición, mismas semillas, batch 64, mismos lr, misma
augmentación, criterio de checkpoint por ruta micro. La exclusión es declarativa:
el manifiesto de S22 no se toca y la reproducibilidad de la partición se conserva.

Los tres runs se reevaluaron con el `evaluate_control.py` actual, para que la
ruta macro y la matriz colapsada sean comparables entre sí.

## Resultado contra control (RealWaste, 4 752 imágenes jamás vistas)

| Run | top-1 material | **ruta** | ruta macro |
|---|---|---|---|
| baseline (sin v2) | 39,3 % | 65,9 % | 66,7 % |
| `full-v2` (con la carpeta `trash`) | 42,1 % | 61,4 % | 63,0 % |
| **`no-trash`** | **44,1 %** | **68,5 %** | **69,0 %** |

`no-trash` gana en las tres métricas a la vez: **+2,6 pp de ruta sobre el
baseline y +7,1 pp sobre `full-v2`**, con el mejor top-1 de los tres. Es la
primera vez en M4 que top-1 y ruta suben juntos contra control.

## Por qué: el error caro vuelve a su sitio

Matriz colapsada por caneca — la columna que importa es `RECYCLABLE →
NON_RECYCLABLE`, el reciclable que acaba en la negra:

| | baseline | `full-v2` | **`no-trash`** |
|---|---|---|---|
| **blanca → negra (caro)** | 447 | 920 | **495** |
| blanca acertada | 2 637 | 2 131 | **2 546** |
| verde acertada | 124 | 270 | **306** |
| negra acertada | 372 | 516 | 402 |

`full-v2` duplicaba el error caro (447 → 920). Quitar la carpeta `trash` lo
devuelve a 495 — a 48 casos del baseline — mientras la caneca verde acertada
sube de 124 a 306. Es decir: **se paga un +48 en el error caro a cambio de +182
aciertos de orgánico**, en lugar del +473 que costaba antes.

## El criterio de éxito, punto por punto

| Criterio | Objetivo | Obtenido |
|---|---|---|
| Ruta contra control | ≥ 65 % | **68,5 %** ✅ |
| Conservar la mejora de ORGANIC de v2 | ≥ 31,9 % | **36,1 %** ✅ |

ORGANIC no solo conserva la ganancia: la supera (14,6 % baseline → 31,9 % v2 →
**36,1 %**). La hipótesis de que la carpeta `trash` era la que envenenaba, y no
Garbage v2 en su conjunto, era correcta: **el resto de v2 sí aporta.**

## Ruta por clase contra control

| Material | n | baseline | `full-v2` | **`no-trash`** |
|---|---|---|---|---|
| PLASTIC | 921 | 91,8 % | 70,6 % | 83,0 % |
| PAPER | 500 | 72,8 % | 66,8 % | **79,2 %** |
| CARDBOARD | 461 | 82,0 % | 62,3 % | 74,2 % |
| GLASS | 420 | 89,0 % | 67,4 % | 87,4 % |
| METAL | 790 | 85,6 % | 73,0 % | **85,7 %** |
| ORGANIC | 847 | 14,6 % | 31,9 % | **36,1 %** |
| TEXTILE | 318 | 62,6 % | **77,0 %** | 69,8 % |
| RESIDUAL | 495 | 34,9 % | **54,8 %** | 36,4 % |

Las cinco corrientes de caneca blanca se recuperan casi al nivel del baseline
—PAPER y METAL incluso lo superan— y ORGANIC alcanza su mejor cifra.

**Contrapartida honesta:** RESIDUAL cae de 54,8 % a 36,4 % y TEXTILE de 77,0 % a
69,8 %. Es esperable: se retiraron 350 ejemplos de RESIDUAL del entrenamiento. El
balance global es favorable porque la caneca blanca tiene mucho más soporte en el
control (3 092 de 4 752), pero **es deuda pendiente**: hoy dos de cada tres
residuos reales acaban señalados como reciclables. Para el usuario ese error es
menos grave que el inverso (ensuciar la corriente aprovechable), pero no es
gratis, y la vía para atacarlo sin reintroducir el veneno es material para la
receta final, no para este experimento.

## Estado de RNF-008

**Sigue sin cumplirse**: exige ≥ 85 % de material y ≥ 95 % de ruta; estamos en
44,1 % y 68,5 %. Lo que este run demuestra es que la dirección es la correcta y
que el techo anterior no era del modelo sino de una etiqueta envenenada. La
brecha que queda es de **dominio**, no de arquitectura: en val interna el mismo
checkpoint da 90,8 % de top-1 y 97,8 % de ruta.

## Artefactos

- `ml/runs/material_low/no-trash/` — `best.pt`, `metrics.json`,
  `eval_control.json`, `confusion_control.csv`, `confusion_val.csv`.
- `ml/reports/logs/S25-low-no-trash.log` — log completo del run (exit 0).
- Coste: 15 épocas, ~45 min en la 3060 Ti, VRAM pico **610 MB** de 6,2 GB libres.

## Siguiente

Run `no-trash-cost` en curso: misma exclusión más las dos palancas que nunca se
habían ejecutado — `--route-cost 0.5` (pérdida sensible al coste de caneca) y
`--select route-macro` (checkpoint por ruta promediada por clase, para que
TEXTILE y ORGANIC no decidan solos).
