import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

// Benchmarks of utf8-string. They run only on request: ./gradlew :utf8-string-benchmarks:benchmark, or
// :utf8-string-benchmarks:jvmBenchmarkAllocations for the bytes allocated per operation on the JVM.
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

// The build only compiles the benchmarks. A Kotlin/Native benchmark is a release executable that takes minutes to link,
// so its benchmark target exists only when a benchmark task is requested.
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
        // Iteration only, to compare variants of the iterator quickly: ./gradlew jvmIterationBenchmark and so on.
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

// JMH with its GC profiler, which reports the bytes allocated per operation (gc.alloc.rate.norm).
tasks.register<JavaExec>("jvmBenchmarkAllocations") {
    val jar = tasks.named("jvmBenchmarkJar")
    classpath(jar)
    mainClass.set("org.openjdk.jmh.Main")
    args("-prof", "gc", "-f", "1", "-wi", "5", "-i", "5", "-w", "1s", "-r", "1s", "utf8string.benchmarks")
}
