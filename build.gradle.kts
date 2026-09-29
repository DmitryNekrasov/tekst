import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.4.20"
}

group = "io.github.dmitrynekrasov"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    explicitApi()

    compilerOptions {
        allWarningsAsErrors.set(true)
        // Experimental, the binaries are marked as pre-release.
        freeCompilerArgs.add("-Xcompanion-blocks-and-extensions")
    }

    jvmToolchain(25)
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }

    js {
        nodejs()
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
    }

    // Native - Linux
    linuxX64()
    linuxArm64()

    // Native - macOS
    macosArm64()

    // Native - Windows
    mingwX64()

    // Native - iOS
    iosArm64()
    iosX64()
    iosSimulatorArm64()

    // Native - watchOS
    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()

    // Native - tvOS
    tvosArm64()
    tvosSimulatorArm64()

    // nonJvmMain: the ASCII fast path for every target except the JVM.
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
            implementation(kotlin("test"))
        }
    }
}
