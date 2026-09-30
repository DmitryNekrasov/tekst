import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
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
        // Without it, a subproject's JS module is named after the root project and the path: utf8-string-utf8-string.
        outputModuleName.set("utf8-string")
        nodejs()
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("utf8-string")
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

    // nonJvmMain: the latin1Bytes loop for every target except the JVM.
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

// The JVM tests check the generated grapheme tables against the Unicode files themselves.
tasks.named<Test>("jvmTest") {
    val unicodeDir = rootProject.layout.projectDirectory.dir("unicode")
    inputs.dir(unicodeDir).withPropertyName("unicode").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("utf8string.unicodeDir", unicodeDir.asFile.absolutePath)
}
