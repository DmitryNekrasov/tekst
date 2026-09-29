import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.PLUGIN_CLASSPATH_CONFIGURATION_NAME
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// Tests of the compiler plugin on every target of the library, without going through the Gradle plugin.
kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.addAll(
            // utf8-string is compiled with companion blocks, so its binaries are pre-release.
            "-Xskip-prerelease-check",
            // The tests use non-constant receivers and unpaired surrogates on purpose.
            "-Xwarning-level=U8_NOT_CONSTANT:warning",
            "-Xwarning-level=U8_UNPAIRED_SURROGATE:warning",
        )
    }

    jvmToolchain(25)
    jvm()

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

    sourceSets {
        commonTest.dependencies {
            implementation(project(":utf8-string"))
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, project(":utf8-string-compiler-plugin"))
    add(NATIVE_COMPILER_PLUGIN_CLASSPATH_CONFIGURATION_NAME, project(":utf8-string-compiler-plugin"))
}

// The tests pass with or without the plugin, so check the compiled test code of every target for the calls it generates.
val verifyU8Rewritten = tasks.register("verifyU8Rewritten") {
    val testOutputs = kotlin.targets
        .filter { it.platformType != KotlinPlatformType.common }
        .map { target -> target.name to target.compilations.getByName("test").output.classesDirs }
    testOutputs.forEach { (_, dirs) -> inputs.files(dirs) }
    dependsOn(kotlin.targets.filter { it.platformType != KotlinPlatformType.common }.map { it.compilations.getByName("test").compileTaskProvider })

    doLast {
        val notRewritten = testOutputs.filter { (_, dirs) -> dirs.asFileTree.none(::containsU8LiteralCall) }.map { it.first }
        check(notRewritten.isEmpty()) { "The compiled test code has no u8Literal call on: $notRewritten" }
    }
}

tasks.named("check") {
    dependsOn(verifyU8Rewritten)
}

fun containsU8LiteralCall(file: File): Boolean {
    val contents = if (file.extension == "klib") {
        ZipFile(file).use { zip -> zip.entries().asSequence().map { zip.getInputStream(it).readBytes() }.toList() }
    } else {
        listOf(file.readBytes())
    }
    return contents.any { String(it, Charsets.ISO_8859_1).contains("u8Literal") }
}
