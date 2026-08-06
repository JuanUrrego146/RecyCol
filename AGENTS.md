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

## Operación del enjambre (vigente desde el 06/08/2026)

Reglas nacidas de incidentes reales del primer día. No son opcionales.

### Git y aislamiento
- **Un worktree por agente, obligatorio**: `git worktree add ../BotaBien-<agente> <rama>`.
  **Prohibido trabajar en el clon compartido**: el 06/08 produjo un commit con
  trabajo mezclado de tres agentes y un falso diagnóstico de catálogo roto.
- **Prohibido `git add -A`**: se añade siempre por rutas explícitas del propio ámbito.
- Una rama por issue (`<agente>/S<NN>-<slug>`). Los stacks de PRs se aterrizan
  **de abajo hacia arriba** y la rama base del stack apunta **siempre a `main`**
  (lección de la cascada perdida de M2, PR #98).
- Email de commit: el noreply de GitHub (`200016968+JuanUrrego146@users.noreply.github.com`);
  el Gmail personal hace rechazar el push.
- «Cierra #N» en español **no** cierra issues automáticamente: al fusionar,
  cerrar la issue a mano con un comentario que referencie el PR.

### CI y fusiones
- **CI corre en un runner self-hosted dockerizado** en la máquina de Juan
  (decisión del 06/08, lo opera QA) que reutiliza `docker/android-build.Dockerfile`:
  CI y build local son el mismo entorno. Verificar en local antes de empujar:
  `docker compose run --rm android-build`.
- **La protección de `main` sigue activa y el check «Compilar y probar» en verde
  es obligatorio para fusionar.** Sin excepciones, tampoco para administradores.
- **QA fusiona los PRs rutinarios** (verde + dentro del ámbito del autor).
  CORE fusiona verdes estancados y cierra sus issues durante sus rondas de guardia.
- No relanzar checks en masa: congeló la cola 90 minutos el 06/08. Re-runs escalonados.

### Cadena de mando
- **Juan autoriza** toda decisión de producto, arquitectura que afecte a varios
  agentes, y riesgo (legal, licencias, rendimiento).
- **CORE es el interlocutor de Juan** y el guardián de los contratos compartidos
  (`shared/domain/`, `shared/testing/`, raíz de Gradle, workflow de CI, catálogo
  de versiones). Cambios en esos ámbitos pasan por issue de coordinación **antes**
  de tocar los archivos.
- QA tiene autoridad de fusión rutinaria; ante la duda, la escala a CORE.

### Regla anti-parón
- **Nadie termina su turno con trabajo pendiente de su milestone.** Al terminar
  un PR se arranca la siguiente issue en el mismo turno. Nadie espera CI,
  notificaciones ni fusiones: CORE fusiona y cierra. Un agente sin push propio
  en ~30 minutos con issues abiertas recibe reactivación de CORE, y reincidir
  escala a Juan.
