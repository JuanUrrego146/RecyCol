# Instrucciones para agentes

Lee `context-for-vibe-coding.md` antes de escribir una sola línea de código.
Contiene las reglas obligatorias del proyecto: stack, convenciones, estructura de
módulos, invariantes de arquitectura, contratos entre agentes y definición de "hecho".

Antes de empezar una issue:

1. Lee `context-for-vibe-coding.md` completo.
2. Lee `docs/arquitectura.md` para entender dónde encaja tu módulo.
3. Comprueba en `plan/plan_de_trabajo.md` cuál es tu ámbito de archivos exclusivo.
4. Implementa exactamente los RF y CUS que cita la issue: ni una feature de más.
5. Trabaja contra los fakes de `shared/testing/` si tu módulo depende de otro agente.

No modifiques archivos fuera de tu ámbito sin una issue de coordinación.

Notas de CI: los checks corren en runners self-hosted del proyecto
(`.github/runner/README.md`); nada se fusiona a `main` sin el check
«Compilar y probar» en verde (protección de rama activa). Si los runners
propios están caídos: `gh workflow run ci-respaldo.yml --ref <rama>`.
