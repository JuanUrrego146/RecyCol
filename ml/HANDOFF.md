# Handoff del agente ML — estado real de M4

Escrito el 07/08/2026 en parada de emergencia. Rama `ml/S22-pipeline-ingesta`,
PR #114 (draft). Los artefactos pesados (`ml/runs/`, `ml/data/`) **no están en
git por diseño**: viven en el worktree `C:\Users\Juan\Documents\GitHub\BotaBien-ml`.

## En qué punto quedó M4

Hecho y publicado:

- S22 ingesta, particiones deterministas, dedup. Pool: **train 17 176 · val 3 088 ·
  control RealWaste 4 752**.
- S25 baseline `low` (MobileNetV3-Small) y run `full-v2` (con Garbage v2), ambos
  evaluados contra control. Reportes en `ml/reports/S25-baseline-low/` y
  `ml/reports/S25-full-v2/`.
- Barrido de arquitectura/lr: `ml/runs/sweep_summary.md`. Ganador provisional en
  val interna: **EfficientNet-B2** (ruta 98,4 % · top-1 92,8 %).
- Auditoría de REGLAS (#23) aplicada al código en el commit `c36da7f`.

Pendiente, en este orden:

1. **Reentrenar sin la carpeta `trash` de Garbage v2** y evaluar contra control.
   Es la prioridad 1: demuestra o refuta el diagnóstico de abajo.
2. Variantes `mid` y `high` con la receta final.
3. S26 contaminación: **se perdió en un crash de Docker**, hay que relanzarlo.
   Los pares sintéticos sí existen (`ml/data/derived/contamination/pairs.csv`).
4. S27 export INT8 con pérdida de exactitud medida. Banco de validación de
   INFERENCIA en `androidApp/inference/README.md`.
5. S28 `ml/REPORTE_METRICAS.md` (`eval/build_report.py` ya está escrito).

## Números que importan

| Run | val material | val ruta | **control material** | **control ruta** |
|---|---|---|---|---|
| baseline `low` (sin v2) | 88,9 % | 98,4 % | 39,3 % | 65,9 % |
| `full-v2` (con v2) | 91,0 % | 97,7 % | 42,1 % | **61,4 %** |

La val interna mejora y el control empeora: esa divergencia es el hallazgo
central de M4. RNF-008 (≥85 % material, ≥95 % ruta) **no se cumple hoy**.

## Qué se cayó del orquestador

`m4_final.ps1` encadenaba reentrenamiento → evaluación → export → reporte sin
intervención. Falló así:

1. Arrancó a las 01:55 con un bucle de espera `while (docker ps --filter
   "name=ml-gpu" -q) { sleep 60 }` para dejar terminar la tanda S26.
2. Docker Desktop se cayó. `docker ps` empezó a devolver **error** en vez de una
   lista de contenedores — y una salida vacía por error es indistinguible de
   «no hay contenedores». El bucle interpretó «GPU libre» y siguió.
3. Con `$ErrorActionPreference = "Continue"` y **sin comprobar un solo exit
   code**, los diez pasos siguientes fallaron al instante contra el daemon
   muerto. La cadena entera se «completó» en 5 segundos y escribió
   `M4-FINAL-COMPLETO` en el log sin haber entrenado nada.
4. El commit final no falló ruidosamente: no había nada que commitear.

Los errores reales solo estaban en `m4_final.err.log`, que nadie miraba. **Ocho
horas de GPU perdidas.** Quien retome esto: el orquestador nuevo debe comprobar
el exit code de cada paso, distinguir «Docker caído» de «GPU libre», y escribir
FALLO en el log que sí se vigila.

Logs del incidente:
`%LOCALAPPDATA%\Temp\claude\C--Users-Juan-Documents-GitHub-BotaBien\3710cd9f-5e44-4b3a-825b-0d433812cd0f\scratchpad\`
(`m4_final.ps1`, `m4_final.log`, `m4_final.err.log`).

Docker Desktop quedó **arrancado y verificado** al escribir esto: `torch.cuda`
ve la RTX 3060 Ti dentro del contenedor.

## Comando que relanza el trabajo

Desde `C:\Users\Juan\Documents\GitHub\BotaBien-ml`, con Docker Desktop vivo:

```powershell
# 1. Ablación pura — solo se quita trash de v2. Compara contra full-v2.
docker compose -f docker-compose.gpu.yml -p botabien-ml-gpu run --rm ml-gpu `
    python train/train_material.py --variant low --run-name v2-clean `
    --exclude garbage_dataset_v2:RESIDUAL --workers 3 --require-gpu
docker compose -f docker-compose.gpu.yml -p botabien-ml-gpu run --rm ml-gpu `
    python eval/evaluate_control.py --variant low --run v2-clean --workers 2

# 2. Receta final — exclusión + pérdida por coste de ruta + selección macro.
foreach ($v in @("low", "mid", "high")) {
    docker compose -f docker-compose.gpu.yml -p botabien-ml-gpu run --rm ml-gpu `
        python train/train_material.py --variant $v --run-name full-v2-clean `
        --exclude garbage_dataset_v2:RESIDUAL --route-cost 0.5 --select route-macro `
        --workers 3 --require-gpu
    if ($LASTEXITCODE -ne 0) { throw "fallo entrenando $v" }
    docker compose -f docker-compose.gpu.yml -p botabien-ml-gpu run --rm ml-gpu `
        python eval/evaluate_control.py --variant $v --run full-v2-clean --workers 2
    if ($LASTEXITCODE -ne 0) { throw "fallo evaluando $v" }
}

# 3. Contaminación (S26, perdida en el crash), export INT8 y reporte final.
docker compose -f docker-compose.gpu.yml -p botabien-ml-gpu run --rm ml-gpu `
    python train/train_contamination.py --workers 2
docker compose -p botabien-ml run --rm ml python export/export_litert.py --all --run full-v2-clean
docker compose -p botabien-ml run --rm ml python eval/build_report.py
```

Coste aproximado en la 3060 Ti: `low` ~50 min, `mid` ~37 min, `high` ~41 min por
run, más unos minutos por evaluación de control.

**Criterio de éxito del paso 1:** si la ruta contra control recupera **≥65 %**
conservando la mejora de ORGANIC que trajo v2 (31,9 % frente al 14,6 % del
baseline), el diagnóstico de abajo queda demostrado.

Los flags `--exclude`, `--select route-macro` y `route_macro` en el reporte de
control son de este handoff y **todavía no se han ejecutado ni una vez**: el
primer run que los use debe mirarse con ojo crítico.

## Trampas ya descubiertas — no volver a pisarlas

**1. La carpeta `trash` de Garbage v2 está envenenada.** Son envases sucios
etiquetados como residual. Enseñan «envase degradado ⇒ RESIDUAL». Como el
control es material real degradado, el modelo dispara reciclables a la caneca
negra: explica íntegro el −4,5 pp de ruta de `full-v2`. Se excluye con
`--exclude garbage_dataset_v2:RESIDUAL` (350 filas en train, 51 en val). El
manifiesto de S22 **no se toca** — la exclusión es declarativa y queda anotada
en el `metrics.json` de cada run, para no romper la reproducibilidad de la
partición.

**2. Optimizar ruta, no top-1.** El **44 % de los errores de material son
gratis**: plástico↔metal↔vidrio↔cartón caen todos en la caneca blanca. Lo que
el usuario sufre es el cruce blanca↔negra/verde. De ahí `--route-cost` (pérdida
sensible a coste) y `--select route-macro` (checkpoint por ruta promediada por
clase: en micro, TEXTILE y ORGANIC deciden solos y tapan el hundimiento de una
clase entera).

**3. El control no se toca. Nunca.** RealWaste no entrena, no ajusta umbrales y
**no elige checkpoints**. Es la única evidencia de generalización que queda; en
cuanto se use para seleccionar algo, deja de serlo. Toda selección se hace sobre
val interna. Ninguna métrica medida sobre datos de entrenamiento cuenta como
evidencia.

**4. La val interna miente sobre el mundo real.** 98,4 % de ruta en val frente a
65,9 % contra control. Cualquier decisión tomada solo con val es sospechosa por
defecto.

**5. Publicar siempre la matriz colapsada por caneca** junto a la de material.
Es lo que el usuario vive; `evaluate_control.py` ya la emite.

**6. Docker es el único entorno.** No hay Python ni SDK local en la máquina.
Todo pasa por `docker compose -p botabien-ml` (CPU) o el overlay
`docker-compose.gpu.yml -p botabien-ml-gpu`. `shm_size` está acotado a 2 GB a
propósito: subirlo invita al OOM killer de la VM de WSL2, que ya tumbó el
barrido una vez.

## Sin usar todavía

- `frame_quality_gate.py` de CÁMARA, para caracterizar la degradación del
  control y comprobar si el filtro de calidad rescataría parte de la brecha.
- Banco de validación de INFERENCIA (`androidApp/inference/README.md`) para
  medir la pérdida del INT8 contra el runtime real (S27, issue #25).

## Riesgos abiertos

- Garbage v2 tiene un riesgo legal sin cerrar (#77): bloquea lanzamiento, no
  desarrollo. El proyecto es **comercial**, no académico.
- El control no contiene BEVERAGE_CARTON ni BATTERY: el caso estrella (vaso de
  café) no es verificable con RealWaste. Mini-set propio pendiente con REGLAS.
- La contaminación se entrenó solo con síntesis; la transferencia a suciedad
  real solo tiene control indirecto.
