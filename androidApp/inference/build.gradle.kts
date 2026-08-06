/*
 * Módulo androidApp/inference: runtime de inferencia on-device (agente EDGE, M3).
 *
 * Integra LiteRT con delegados NNAPI y GPU y respaldo automático en CPU (S15).
 * Implementa el puerto WasteClassifier del dominio; nunca decide canecas
 * (invariante 2) y no tiene ninguna dependencia de red (RNF-002).
 */
plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
}

android {
    namespace = "com.botabien.android.inference"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // api: los tipos del dominio (ImageFrame, ClassificationResult) forman parte
    // de la superficie pública de este módulo (PixelAccessFrame los extiende).
    api(project(":shared"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.litert)
    implementation(libs.litert.gpu)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
