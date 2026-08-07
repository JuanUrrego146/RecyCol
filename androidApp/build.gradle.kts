/*
 * Módulo androidApp: shell de la aplicación Android.
 * La UI real (design system, pantallas) es ámbito del agente FRONT (androidApp/ui/);
 * cámara e inferencia, de los agentes CAM y EDGE. S01 solo deja el módulo compilando.
 */
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.botabien.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.botabien.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    lint {
        // Versiones de AGP y de las dependencias están fijadas a propósito en
        // gradle/libs.versions.toml (ámbito de CORE); subirlas es una decisión
        // de esa issue, no algo que deba tumbar "Calidad" en cada release nueva
        // río arriba. Suprimidas aquí, no ignoradas: el catálogo sigue siendo
        // la fuente de verdad de qué versión se usa.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion")

        // Ícono de lanzador: identidad visual (logo, icono, splash) en curso
        // en #142, agente FRONT. Suprimido hasta que ese trabajo aterrice.
        disable += "MissingApplicationIcon"
    }
}

dependencies {
    implementation(project(":shared"))
    // Provisional (modelo de trabajo M0: nadie espera a nadie): la UI se cablea
    // sobre los fakes deterministas hasta que RULES (S30) y DATA (S36) publiquen
    // sus implementaciones; entonces esta dependencia sale del APK.
    implementation(project(":shared:testing"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // Ciclo de vida para el visor de cámara (LocalLifecycleOwner sin API obsoleta)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)

    // Cámara: agente CAM (androidApp/camera/), RF-009
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    // Fakes deterministas del contrato M0: las pruebas validan contra los puertos
    testImplementation(project(":shared:testing"))

    // Capa de datos (S36): driver SQLite, preferencias e inyección de dependencias
    implementation(libs.sqldelight.android.driver)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // Pantalla de sesión (S38): ViewModel en Compose e inyección en composables
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.koin.androidx.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
