import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Módulo shared: Kotlin Multiplatform, sin dependencias de plataforma (RNF-005).
 *
 * Targets activos: Android (consumido por :androidApp) y JVM (pruebas rápidas y CI).
 * Los targets iOS se activan en S43 (agente RELEASE, requiere host macOS); la
 * portabilidad se protege desde ya con la tarea `verifyPlatformIsolation`, que
 * rechaza cualquier import de plataforma dentro de este módulo.
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
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.botabien.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/*
 * Regla de aislamiento de plataforma (RNF-005): falla el build si aparece un
 * import de Android, AndroidX o del runtime de inferencia en el código de shared.
 * Forma parte de `check`, así que CI la ejecuta siempre.
 */
val verifyPlatformIsolation by tasks.registering {
    group = "verification"
    description = "Falla si shared/ importa android.*, androidx.* o el runtime de inferencia."

    val sourceDir = layout.projectDirectory.dir("src")
    inputs.dir(sourceDir)

    doLast {
        val forbidden = Regex("""^\s*import\s+(android\.|androidx\.|com\.google\.ai\.edge\.)""")
        val offenders = sourceDir.asFileTree.matching { include("**/*.kt") }.files
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (forbidden.containsMatchIn(line)) "${file.relativeTo(projectDir)}:${index + 1} → ${line.trim()}"
                    else null
                }
            }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "shared/ no puede depender de APIs de plataforma (RNF-005). Imports prohibidos:\n" +
                    offenders.joinToString("\n")
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyPlatformIsolation)
}
