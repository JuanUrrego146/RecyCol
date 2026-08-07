# Runner self-hosted de GitHub Actions para RecyCol (agente QA, coordinación en el PR).
#
# Deriva de la imagen de build del proyecto (CORE, docker/android-build.Dockerfile):
# mismo JDK 17, Gradle 8.13 y Android SDK fijados — lo que verifica CI es
# byte a byte lo mismo que un build local en contenedor.
#
# La versión del agente está fijada; para actualizarla, cambiar RUNNER_VERSION
# y reconstruir (ver README.md de este directorio).
FROM recycol/android-build:1.0.0

ARG RUNNER_VERSION=2.336.0

USER root

# Dependencias del agente (.NET runtime autocontenido: necesita ICU y krb5)
RUN apt-get update \
    && apt-get install -y --no-install-recommends libicu-dev libkrb5-3 zlib1g jq git \
    && rm -rf /var/lib/apt/lists/*

# El agente vive en /opt/actions-runner; se monta un volumen encima para que
# la configuración (.runner/.credentials) sobreviva a recreaciones del contenedor.
RUN mkdir -p /opt/actions-runner \
    && curl -fsSL "https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/actions-runner-linux-x64-${RUNNER_VERSION}.tar.gz" \
       | tar -xz -C /opt/actions-runner \
    && chown -R gradle:gradle /opt/actions-runner

# La caché de Gradle vive en un volumen montado aquí; el directorio debe existir
# en la imagen con dueño gradle para que el volumen herede los permisos.
RUN mkdir -p /opt/gradle-cache && chown gradle:gradle /opt/gradle-cache

COPY --chmod=0755 entrypoint.sh /usr/local/bin/entrypoint.sh

USER gradle
WORKDIR /opt/actions-runner

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
