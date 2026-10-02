import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }

    jvmToolchain(25)
    jvm()
    js {
        nodejs()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }

    macosArm64()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":utf8-string"))
            implementation(libs.kotlinx.benchmark.runtime)
        }
        jvmMain.dependencies {
            implementation(libs.icu4j)
        }
    }
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

// A Kotlin/Native benchmark takes minutes to link, so its target exists only when a benchmark task is requested.
val benchmarksRequested = gradle.startParameter.taskNames.any { it.substringAfterLast(':').contains("enchmark") }

benchmark {
    targets {
        register("jvm")
        register("js")
        register("wasmJs")
        if (benchmarksRequested) {
            register("macosArm64")
            register("linuxX64")
        }
    }
    configurations {
        named("main") {
            warmups = 5
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "s"
        }
        register("iteration") {
            include("GraphemeBenchmark.iterate")
            param("corpus", "ascii", "cyrillic", "cjk")
            warmups = 5
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "s"
        }
        // C2 compiles Utf8String.fromString in one of two ways from one JVM to the next, so the JVM runs 5 forks.
        register("comparison") {
            include("GraphemeBenchmark.iterate$")
            include("GraphemeBenchmark.lengthOfNewString")
            include("BreakIteratorBenchmark")
            include("IntlSegmenterBenchmark")
            warmups = 5
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "s"
            advanced("jvmForks", 5)
        }
    }
}

// The module has no tests.
tasks.withType<AbstractTestTask>().configureEach {
    enabled = false
}

tasks.register<JavaExec>("jvmBenchmarkAllocations") {
    val jar = tasks.named("jvmBenchmarkJar")
    classpath(jar)
    mainClass.set("org.openjdk.jmh.Main")
    args("-prof", "gc", "-f", "1", "-wi", "5", "-i", "5", "-w", "1s", "-r", "1s", "utf8string.benchmarks")
}
