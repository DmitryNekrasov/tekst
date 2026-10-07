import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.dokka)
    alias(libs.plugins.nmcp)
    `maven-publish`
}

kotlin {
    explicitApi()

    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

    compilerOptions {
        allWarningsAsErrors.set(true)
    }

    jvmToolchain(25)
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }

    js {
        // Without it, a subproject's JS module is named after the root project and the path: tekst-root-tekst.
        outputModuleName.set("tekst")
        nodejs()
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("tekst")
        nodejs()
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
    }

    linuxX64()
    linuxArm64()

    macosArm64()

    mingwX64()

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()

    tvosArm64()
    tvosSimulatorArm64()

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("nonJvm") {
                withJs()
                withWasmJs()
                withWasmWasi()
                withNative()
            }
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.icu4j)
        }
    }
}

tasks.named<Test>("jvmTest") {
    val unicodeDir = rootProject.layout.projectDirectory.dir("unicode")
    inputs.dir(unicodeDir).withPropertyName("unicode").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("tekst.unicodeDir", unicodeDir.asFile.absolutePath)
}

dokka {
    dokkaPublications.html {
        failOnWarning.set(true)
        outputDirectory.set(rootDir.resolve("docs"))
    }

    dokkaSourceSets.named("commonMain") {
        samples.from("src/commonTest/kotlin/samples")
        sourceLink {
            localDirectory.set(rootDir)
            val ref = if (version.toString().endsWith("-SNAPSHOT")) "main" else "v$version"
            remoteUrl("https://github.com/DmitryNekrasov/tekst/tree/$ref")
        }
    }
}

// Maven Central requires a javadoc jar, and the API reference is on GitHub Pages, so the jar is empty. One jar per
// publication, so that the publications do not share a signature task.
publishing {
    publications.withType<MavenPublication>().configureEach {
        val publication = name
        artifact(tasks.register<Jar>("${publication}JavadocJar") {
            archiveClassifier.set("javadoc")
            archiveAppendix.set(publication)
        })
        pom {
            name.set("tekst")
            description.set(
                "A Kotlin Multiplatform library for working with text: grapheme boundaries, and a string of " +
                    "graphemes stored as UTF-8 bytes",
            )
        }
    }
}
