import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Kotlin Gradle plugin is already on the classpath via the root buildscript,
    // so it must be applied without a version here.
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        jvmMain.dependencies {
            // currentOs brings the desktop runtime (skiko) + foundation + ui + material.
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.compose.material3:material3:${libs.versions.compose.multiplatform.get()}")
            implementation("org.jetbrains.compose.material:material-icons-extended:${libs.versions.compose.multiplatform.get()}")

            // Navigation (multiplatform, same as Mihon Android).
            implementation(libs.bundles.voyager)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            // Image loading (covers + reader pages) from Suwayomi REST endpoints.
            implementation("org.jetbrains.compose.components:components-resources:${libs.versions.compose.multiplatform.get()}")
            implementation(libs.coil.compose)
            implementation(libs.coil.core)
            implementation(libs.coil.network.okhttp)
        }
    }
}

// Manual smoke test for the Suwayomi bridge (requires a running server on localhost:4567).
tasks.register<JavaExec>("smokeTest") {
    group = "verification"
    description = "Runs SuwayomiClient against a local Suwayomi-Server"
    val jvmTarget = kotlin.targets.getByName("jvm")
    val mainCompilation = jvmTarget.compilations.getByName("main")
    classpath(mainCompilation.output.allOutputs, mainCompilation.runtimeDependencyFiles)
    mainClass.set("mihon.desktop.bridge.SmokeTestKt")
}

/**
 * Drop-in dir for the Suwayomi-Server jar (and, optionally, a `suwayomi-runtime/` JRE) that the app
 * embeds at launch (see SuwayomiServerManager). Any `Suwayomi-Server*.jar` placed here is copied into
 * the packaged app's resources directory during [packageAppImage] so the bundle is self-contained.
 */
val suwayomiBundleDir = layout.projectDirectory.dir("resources")

// Copy any bundled Suwayomi-Server jar (drop it in desktop/resources/) into the app-image's runtime
// resources dir before packaging, so the app can launch an embedded server when no env override is set.
val copySuwayomiJar = tasks.register<Copy>("copySuwayomiJar") {
    from(suwayomiBundleDir) { include("Suwayomi-Server*.jar") }
    into(layout.buildDirectory.dir("compose/binaries/main/app/mihon-desktop/app"))
}
// `packageAppImage` is registered lazily by the Compose plugin, so wire the dependency after evaluation.
afterEvaluate {
    tasks.findByName("packageAppImage")?.dependsOn(copySuwayomiJar)
}

compose.desktop {
    application {
        mainClass = "mihon.desktop.MainKt"

        nativeDistributions {
            // AppImage bundles the JVM runtime via jpackage and needs no system packaging tooling,
            // unlike Deb (requires dpkg-deb/fakeroot). The runtime image is built by createRuntimeImage.
            // AppImage = portable single-file runtime; Deb = system package; the tar.gz portable
            // bundle is produced by the CI workflow from the packaged app-image directory.
            targetFormats(TargetFormat.AppImage, TargetFormat.Deb)
            packageName = "mihon-desktop"
            packageVersion = "1.0.0"
            description = "Mihon Desktop — Suwayomi-Server client for Linux"
            vendor = "Mihon"
            // Linux packaging metadata used by jpackage when generating the .deb.
            linux {
                iconFile.set(rootProject.layout.projectDirectory.file(".github/assets/logo.png"))
            }

            // Mark the resources dir as app resources so SuwayomiServerManager.locateServerJar() finds
            // any bundled Suwayomi jar at runtime when the app-image runs.
            appResourcesRootDir.set(suwayomiBundleDir)

            // Extra JDK modules that jpackage's minimal jlinked runtime would otherwise strip. The
            // GraphQL/HTTP bridge (SuwayomiClient) needs java.net.http; the Suwayomi child process
            // launcher needs java.lang.Process (already in java.base). Add the modules actually used at
            // runtime so the packaged app can run standalone.
            modules("java.net.http")
            modules("java.management")
        }
    }
}
