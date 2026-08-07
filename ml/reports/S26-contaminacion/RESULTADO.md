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

## Decisión tomada: plan B activado (07/08)

Juan activó el plan B. **La etapa 2 automática queda sustituida por una pregunta
al usuario** («¿está sucio? ¿tiene grasa o restos?») **y solo para cartón y
papel**, que es donde la contaminación es irreversible. **Plástico, vidrio y
metal no preguntan nada**: se enjuagan y se reciclan igual.

Consecuencia para ML: **la etapa 2 deja de ser bloqueante para M4.** El modelo
queda entrenado y sus métricas documentadas, pero no se invierte más en él.

### El problema acotado es bastante más fácil

Con el recorte a cartón y papel, esto deja de ser «detectar suciedad en cualquier
residuo» y pasa a ser **«detectar grasa y líquido en fibra de celulosa»**. Es un
problema más pequeño y con una propiedad física aprovechable: **la fibra
absorbe**.

Una mancha de grasa en cartón **no es un parche superpuesto** —que es exactamente
lo que la síntesis fabricaba, y por lo que fracasó— sino un cambio de
**translucidez y saturación del propio material**, con bordes difusos, sin brillo
especular y sin frontera nítida. Esa firma es mucho más estable entre objetos que
«suciedad» en abstracto, porque depende del sustrato y el sustrato ahora está
fijo.

De ahí la intuición concreta: **una síntesis específica de absorción en fibra**
—oscurecer localmente, subir saturación, reducir contraste local y difuminar los
bordes, **sin superponer color opaco**— debería transferir bastante mejor que la
actual. Se puede hacer con las mismas herramientas y ataca justo el atajo que el
modelo aprendió.

Con la salvedad de siempre, que aquí es lo que manda: **sin datos reales
etiquetados no hay forma de saber si funciona**, porque la métrica sintética ya
demostró mentir. Por eso el estado de contaminación en cartón y papel es la
captura prioritaria de la fase RecyCol Entrenamiento (§10 de `CONTEXTO.md`): es
lo único que la síntesis no logró replicar y no existe en ninguna fuente pública.

## Encaje del plan B

El riesgo estaba registrado («la contaminación sintética puede no transferir») y
esta medición es la evidencia que activó su plan B. Encaja con la decisión ya
tomada de que la baja confianza sea el flujo protagonista y de que la app asuma
la duda en primera persona.

**Nada de arquitectura cambia:**

- **El motor de reglas no se toca.** La regla del vaso de café y de la caja de
  pizza vive en el perfil normativo (`contaminatedFallback`), no en el modelo.
- **El contrato EDGE↔ML tampoco.** `contamination.tflite` sigue existiendo con su
  orden `[CLEAN, CONTAMINATED]`, e `inspectContamination` sigue en el puerto.
- **Lo único que cambia es quién rellena `ContaminationState`**: en vez de la
  etapa 2, el usuario. Para plástico, vidrio y metal ni siquiera se pregunta.
- Encaja con el invariante 8: ante la duda, caneca conservadora del perfil con su
  `FallbackReason`.
- En gama baja la etapa 2 ya era solo captura manual dirigida, así que el plan B
  **alinea las tres gamas** en el mismo comportamiento.

Ventaja no menor: elimina el **falso «limpio» silencioso**, que es el error caro
—manda un reciclable sucio a la caneca blanca sin que nadie se entere—. El usuario
sí sabe si su vaso tiene café dentro.

## Qué haría falta si se retoma

Por orden de retorno:

1. **Romper la simetría del par** (unas líneas en `PairDataset`): que limpio y
   sucio no vengan del mismo objeto. Es lo más barato y el mejor diagnóstico —
   si la exactitud sintética se desploma, confirma que el 94 % era el atajo.
2. **Síntesis de absorción en fibra** para cartón y papel, según lo anterior:
   translucidez y saturación en vez de parche opaco.
3. **Bajar el alfa** de 0,80–0,97 a 0,3–0,7 y variar más.
4. **Degradación global** además de la mancha, y componer sobre fondos degradados.
5. **Un mini-set real con etiqueta limpio/sucio** — de evaluación exclusivamente,
   jamás de entrenamiento. **Es la condición para que cualquiera de los puntos
   anteriores sea medible**; sin él la única métrica sería otra vez la sintética,
   que ya demostró mentir. Lo aporta RecyCol Entrenamiento.
6. Reconsiderar el enfoque: quizá la señal útil no sea «sucio/limpio» sino «puedo
   verlo con claridad», que es lo que `frame_quality_gate.py` de CÁMARA ya mide y
   que sigue sin usarse.

## Artefactos

- `ml/runs/contamination/full/` — `best.pt`, `metrics.json`.
- `ml/reports/logs/S26-contaminacion.log` — log completo (exit 0).
- Coste: 8 épocas, ~9 min en la 3060 Ti.
