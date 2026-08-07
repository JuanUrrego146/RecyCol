# Runners self-hosted de BotaBien

CI del repositorio corre en **dos runners propios dockerizados** en la máquina de
Juan (aprobado por Juan el 06/08/2026, ámbito QA), con GitHub-hosted como respaldo
manual. Motivo: la cola de runners hospedados llegó a horas con siete agentes
trabajando en paralelo.

## Qué son

- Imagen `botabien/actions-runner:<version>` = `botabien/android-build:1.0.0`
  (CORE: JDK 17, Gradle 8.13, Android SDK 35/34.0.0, todo fijado) + agente
  oficial de GitHub Actions (`actions/runner`, versión fijada en el Dockerfile).
  **Lo que verifica CI es exactamente el mismo entorno que un build local en
  contenedor.**
- Dos servicios (`botabien-runner-1/2`) con tope de 5 GB de RAM y 3 CPUs cada
  uno (la VM de Docker tiene ~11,6 GB / 6 CPUs; ML entrena en GPU, que no se toca).
- Etiquetas: `self-hosted`, `linux`, `docker`, `botabien`. `ci.yml` selecciona
  `[self-hosted, linux, docker]`.
- **Caché de Gradle persistente** por runner (volúmenes `runnerN-gradle-cache`
  montados como `GRADLE_USER_HOME`): la primera ejecución descarga dependencias,
  las siguientes son incrementales. El heap de CI va limitado a 3g vía
  `gradle.properties` del volumen (precede al `-Xmx4g` del proyecto).

## Levantar

Desde `.github/runner/` (los tokens de registro caducan en 1 h y son de un solo
uso; **solo hacen falta la primera vez** o tras borrar los volúmenes `runnerN-agent`):

```bash
export RUNNER_TOKEN_1=$(gh api -X POST repos/JuanUrrego146/BotaBien/actions/runners/registration-token --jq .token)
export RUNNER_TOKEN_2=$(gh api -X POST repos/JuanUrrego146/BotaBien/actions/runners/registration-token --jq .token)
docker compose -f docker-compose.runners.yml up -d --build
```

Verificar que están en línea:

```bash
gh api repos/JuanUrrego146/BotaBien/actions/runners --jq '.runners[] | .name + " " + .status'
```

## Tumbar / reiniciar

```bash
docker compose -f docker-compose.runners.yml down     # quedan "offline" en GitHub, la config persiste
docker compose -f docker-compose.runners.yml up -d    # vuelven sin token
```

Baja definitiva (además borra el registro en GitHub y los volúmenes):

```bash
gh api repos/JuanUrrego146/BotaBien/actions/runners --jq '.runners[] | (.id|tostring) + " " + .name'
gh api -X DELETE repos/JuanUrrego146/BotaBien/actions/runners/<id>
docker compose -f docker-compose.runners.yml down -v
```

## Si falla

0. **Job en `failure` SIN ningún paso fallido** → el contenedor murió por OOM
   contra su `mem_limit` (incidente del 07/08: un pipeline frío necesita
   ~6-7 GB; con 5 GB moría en `mergeDebugGlobalSynthetics`). Verificar con
   `docker inspect botabien-runner-N --format '{{.State.OOMKilled}}'`; si el
   patrón reaparece, subir `mem_limit` o correr un solo runner. No confundir
   con caché corrupta: reproducir primero en local.
1. **Jobs en cola y runners offline** → `docker ps` y `docker logs botabien-runner-1`.
   Reinicio rápido: `docker compose -f docker-compose.runners.yml restart`.
2. **Runner caído y hay prisa** → respaldo en GitHub-hosted:
   `gh workflow run ci-respaldo.yml --ref <rama>` — produce el mismo check
   «Compilar y probar», así que la protección de rama queda satisfecha igual.
3. **Build sin memoria (exit 137)** → un solo job por vez: parar `runner-2`.
4. **Caché corrupta** → `docker volume rm botabien-runner_runnerN-gradle-cache`
   (nombre real con `docker volume ls`); la siguiente ejecución la reconstruye.
5. **Actualizar el agente** → subir `RUNNER_VERSION` en el Dockerfile y la
   etiqueta `image:` del compose, `up -d --build`. El agente además se
   auto-actualiza en caliente cuando GitHub lo exige.

## Seguridad

El repo es privado y sin forks externos: no ejecutan código de desconocidos.
Los contenedores no guardan ningún secreto de larga vida — el token de registro
es de un solo uso y caduca en 1 h; las credenciales que emite GitHub solo
autorizan a recoger jobs de ESTE repositorio. No añadir secretos del repo que
el build no necesite.
