import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Módulo shared/testing: fakes deterministas de los puertos del contrato M0.
 *
 * Cada agente compila y prueba su módulo usando estos fakes mientras la
 * implementación real del vecino no exista: nadie espera a nadie (RNF-015).
 * Se consume como `testImplementation(project(":shared:testing"))` — o
 * `implementation` en builds de desarrollo que necesiten el pipeline simulado.
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.botabien.shared.testing"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
