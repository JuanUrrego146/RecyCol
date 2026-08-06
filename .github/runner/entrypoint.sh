#!/usr/bin/env bash
# Arranque del runner self-hosted de BotaBien.
#
# Primera vez (volumen del agente vacío): registra el runner con RUNNER_TOKEN
# (token de registro de un solo uso, caduca en 1 h — NO es un PAT; el contenedor
# no guarda ningún secreto de larga vida). Las credenciales que emite GitHub
# quedan en el volumen y los reinicios posteriores no necesitan token.
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/JuanUrrego146/BotaBien}"
RUNNER_NAME="${RUNNER_NAME:-botabien-runner}"
RUNNER_LABELS="${RUNNER_LABELS:-self-hosted,linux,docker,botabien}"

cd /opt/actions-runner

# Límite de heap para builds de CI: por debajo del -Xmx4g del proyecto para que
# dos runners quepan en la VM de Docker sin ahogarla. GRADLE_USER_HOME/gradle.properties
# tiene precedencia sobre el gradle.properties del proyecto.
if [ -n "${GRADLE_USER_HOME:-}" ] && [ ! -f "${GRADLE_USER_HOME}/gradle.properties" ]; then
  mkdir -p "${GRADLE_USER_HOME}"
  printf 'org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8\n' > "${GRADLE_USER_HOME}/gradle.properties"
fi

if [ ! -f .runner ]; then
  if [ -z "${RUNNER_TOKEN:-}" ]; then
    echo "ERROR: runner sin configurar y sin RUNNER_TOKEN. Genera uno con:" >&2
    echo "  gh api -X POST repos/JuanUrrego146/BotaBien/actions/runners/registration-token --jq .token" >&2
    exit 1
  fi
  ./config.sh --unattended --replace \
    --url "${REPO_URL}" \
    --token "${RUNNER_TOKEN}" \
    --name "${RUNNER_NAME}" \
    --labels "${RUNNER_LABELS}" \
    --work _work
fi

exec ./run.sh
