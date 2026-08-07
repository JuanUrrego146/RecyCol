# Instrucciones para agentes

**Lee [`CONTEXTO.md`](CONTEXTO.md) antes de escribir una sola línea de código.**
Es el documento único del proyecto: qué es RecyCol, reglas de trabajo del
enjambre, estado real por milestone, arquitectura e invariantes, contratos entre
agentes, decisiones de producto ya tomadas, estado de ML y riesgos abiertos.

No hay un segundo documento de contexto. Si algo no está en `CONTEXTO.md`, está
enlazado desde su sección final «Documentos formales».

Antes de empezar una issue:

1. Lee `CONTEXTO.md` completo.
2. Comprueba en su §4 cuál es tu ámbito de archivos exclusivo.
3. Implementa exactamente los RF y CUS que cita la issue: ni una feature de más.
4. Trabaja contra los fakes de `shared/testing/` si dependes de otro agente.
5. Publica el hito en el tablero de estado, issue **#123** (qué / dónde / qué sigue).

No modifiques archivos fuera de tu ámbito sin una issue de coordinación.
