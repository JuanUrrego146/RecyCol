# Entorno de build Android/KMP de RecyCol.
# Todas las versiones están fijadas: mismo resultado en cualquier máquina y en CI.
#   - Gradle 8.13 + JDK 17 (imagen oficial de Gradle, base Eclipse Temurin)
#   - Android SDK: platform 35, build-tools 34.0.0 (las que usa AGP 8.7)
FROM gradle:8.13-jdk17

ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk

# Versión fijada de las command-line tools del SDK (build 12.0, revisión 11076708)
ARG CMDLINE_TOOLS_ZIP=commandlinetools-linux-11076708_latest.zip

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools \
    && curl -fsSL "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}" -o /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools \
    && mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest \
    && rm /tmp/cmdline-tools.zip

ENV PATH=${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}

RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;android-35" \
        "build-tools;34.0.0" \
    && chown -R gradle:gradle ${ANDROID_SDK_ROOT}

USER gradle
WORKDIR /workspace
