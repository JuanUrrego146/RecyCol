# S26 · Clasificador de contaminación — entrena bien y no transfiere

**Veredicto: la etapa 2 no es utilizable tal cual.** El modelo separa casi
perfectamente los pares sintéticos de S24 (94,0 % de exactitud, recall 92,2 %) y
declara **limpio el 98,75 % de RealWaste**, que son residuos reales degradados
fotografiados en un relleno sanitario. Ha aprendido a reconocer el artefacto de
síntesis, no la suciedad.

Fecha: 07/08/2026 · MobileNetV3-Small binario 224 · RTX 3060 Ti · 8 épocas.
El run se había perdido en el crash de Docker del 07/08; este es el relanzamiento.

## Lo que sí funciona

| Métrica (val sintética, 1 274 muestras) | Valor |
|---|---|
| Exactitud @0,5 | 94,0 % |
| Umbral elegido | **0,62** |
| Recall de CONTAMINATED | 92,2 % |
| Precisión | 96,2 % |

El umbral se elige como el más exigente que aún alcanza el 92 % de recall,
porque el error caro de esta etapa es el falso «limpio»: manda un reciclable
sucio a la caneca blanca. Con la corrección de esta sesión, si ningún umbral
llegara al mínimo el selector devuelve el de mayor recall y marca
`meets_min_recall: false` — aquí sí se alcanza, así que la marca es `true`.

Pares: 4 045 · train 6 814 muestras / val 1 274, partición por md5 del par
(determinista, sin solape entre limpio y sucio del mismo objeto).

## Lo que no funciona: el control indirecto

No existe etiqueta real de limpio/sucio en ninguna fuente pública del inventario,
así que la transferencia se mide con un proxy: **TrashNet/val** son fotos de
estudio mayormente limpias y **RealWaste** son residuos reales de relleno
sanitario. Si el modelo hubiera aprendido *contaminación*, RealWaste debería
puntuar claramente más alto.

| Conjunto | Fracción marcada CONTAMINATED |
|---|---|
| val (limpio de estudio) | **0,0 %** |
| control RealWaste (degradado real) | **1,25 %** |

La dirección es correcta —1,25 % > 0 %— pero la magnitud es despreciable. Un
detector de suciedad que no ve suciedad en un relleno sanitario no está midiendo
suciedad.

### Por qué, casi con seguridad

La síntesis de S24 genera contaminación **superponiendo un artefacto** sobre un
objeto limpio segmentado con U²-Net. Esa señal es localizada, de textura y color
consistentes, y sobre un fondo de estudio. La suciedad real es otra cosa:
decoloración global, deformación, desgaste, restos adheridos, iluminación pobre.
El modelo alcanza 94 % aprendiendo el atajo — «¿hay una mancha superpuesta con
esta pinta?» — que es exactamente lo que la métrica sintética premia.

Es el mismo patrón que el hallazgo central de M4 con el clasificador de material:
**el dominio de entrenamiento y el dominio real no miden lo mismo**, y una
métrica interna alta es sospechosa por defecto.

## Consecuencia de producto — decisión de Juan

El riesgo estaba registrado («la contaminación sintética puede no transferir») con
un plan B ya previsto: **reducir la etapa 2 a una pregunta explícita al usuario,
conservando el flujo de UX**. Esta medición es la evidencia que activa esa
decisión, y encaja con la decisión ya tomada de que la baja confianza sea el flujo
protagonista y de que la app asuma la duda en primera persona.

Afecta directamente al caso estrella: la regla del vaso de café y de la caja de
pizza exigen inspección del interior, y hoy **no hay detector fiable que la
resuelva automáticamente**. La regla en sí no cambia — vive en el perfil
normativo, no en el modelo — cambia quién responde a la pregunta «¿está limpio?».

**No se toca el contrato EDGE↔ML.** `contamination.tflite` sigue existiendo con su
orden `[CLEAN, CONTAMINATED]`; lo que se decide es si la app lo consulta o
pregunta al usuario.

## Qué haría falta para arreglarlo de verdad

Por orden de retorno:

1. **Un mini-set real de evaluación** con etiqueta limpio/sucio, de evaluación
   exclusivamente y jamás de entrenamiento. Sin él, esta etapa no tiene métrica
   honesta y cualquier mejora es invisible. Encaja con el mini-set de ≈400 fotos
   que ya está especificado para el caso estrella.
2. **Síntesis más agresiva y variada**: decoloración global, deformación, desgaste
   y no solo manchas superpuestas — atacando el atajo concreto que el modelo
   aprendió.
3. Reconsiderar el enfoque: quizá la señal útil no sea «sucio/limpio» sino
   «puedo verlo con claridad», que es lo que el `frame_quality_gate.py` de CÁMARA
   ya mide y que aún no se ha usado.

## Artefactos

- `ml/runs/contamination/full/` — `best.pt`, `metrics.json`.
- `ml/reports/logs/S26-contaminacion.log` — log completo (exit 0).
- Coste: 8 épocas, ~9 min en la 3060 Ti.
