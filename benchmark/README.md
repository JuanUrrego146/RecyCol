# Banco de latencia por gama — BotaBien

Ámbito del agente QA (M8). Este directorio define **cómo se mide y se reporta**
la latencia de clasificación por gama de dispositivo. El módulo instrumentado
real (Macrobenchmark sobre `androidApp`) se crea en **S41**, cuando exista la
inferencia de M3; desde ya, EDGE (S20) y QA (S41) publican resultados en el
formato común de `latency-report.schema.json`.

## Qué gobierna estas mediciones

- **RNF-001** — Objetivo ≤ 2 s extremo a extremo en gama media y ≤ 4 s en gama
  baja. Es una **meta de diseño, no un bloqueo**: si un dispositivo no llega,
  la app degrada funciones auxiliares.
- **Requisito duro** — La clasificación por cámara **funciona en las tres
  gamas sin excepción**. Un reporte de latencia de una gama donde la
  clasificación no funcione es un fallo de verificación, sea cual sea el número.
- **RNF-003** — Los requerimientos medibles se verifican con mediciones
  publicadas, no con estimaciones.

## Protocolo de medición

1. **Build**: `release` sin depurador conectado, instalado desde APK. Nunca se
   mide sobre build `debug` (el intérprete y las comprobaciones lo distorsionan).
2. **Calentamiento**: se descartan las primeras `warmupRuns` inferencias (el
   micro-benchmark de arranque de `DeviceTierPolicy` y la compilación de
   delegados contaminan las primeras muestras).
3. **Muestra**: mínimo 30 mediciones por escenario (`measuredRuns ≥ 30`); se
   reportan p50, p90 y media. Nunca se reporta una sola ejecución.
4. **Escenarios por gama**: cada dispositivo se mide con la variante de modelo
   y el delegado que `DeviceTierPolicy` le asigna realmente, y además con
   respaldo en CPU para conocer el peor caso.
5. **Etapas**: se desglosa el extremo a extremo en calidad de frame,
   preprocesado, inferencia de material, inspección de contaminación (si
   aplica) y motor de reglas. El extremo a extremo se mide aparte, no se suma.
6. **Condiciones**: modo avión activado (RNF-002), batería > 50 %, sin carga,
   pantalla encendida, temperatura estable (se descartan corridas con
   *thermal throttling* detectado).

## Formato de resultados

Todo reporte es un JSON que valida contra `latency-report.schema.json`
(CI lo verifica con las muestras de `samples/`). Los reportes reales se
publican en `results/<iso-fecha>-<dispositivo>.json` y se citan en la issue o
el PR correspondiente.

`samples/` contiene únicamente muestras **sintéticas** para validar el esquema;
no son mediciones y no deben citarse como evidencia.

## Dispositivos de referencia

La matriz concreta se fija en S41 con los dispositivos disponibles. Guía de
selección por gama:

| Gama | Perfil de hardware orientativo |
|---|---|
| Baja | 2–3 GB RAM, sin delegado NNAPI/GPU útil, API 26–28 |
| Media | 4–6 GB RAM, GPU o NNAPI disponible, API 29–33 |
| Alta | ≥ 8 GB RAM, NPU/GPU, API ≥ 33 |

La gama la decide `DeviceTierPolicy` en el dispositivo, no esta tabla: si la
política clasifica distinto de lo esperado, se reporta la gama observada.
