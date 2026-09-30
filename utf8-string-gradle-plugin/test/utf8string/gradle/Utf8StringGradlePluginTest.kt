/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Utf8StringGradlePluginTest {
    @TempDir
    lateinit var projectDir: File

    private val localMavenRepository = System.getProperty("localMavenRepository")

    @Test
    fun rewritesLiteralsInAJvmProject() {
        writeProject(kotlinVersion = BuildConfig.KOTLIN_VERSION)
        projectDir.resolve("src/main/kotlin/Literals.kt").apply { parentFile.mkdirs() }.writeText(
            """
            import utf8string.u8

            fun greeting() = "Hello, UTF-8".u8
            """.trimIndent(),
        )

        runner("compileKotlin").build()

        val classes = projectDir.resolve("build/classes/kotlin/main")
        val facade = String(classes.resolve("LiteralsKt.class").readBytes(), Charsets.ISO_8859_1)
        val holder = String(classes.resolve("U8Literals\$LiteralsKt.class").readBytes(), Charsets.ISO_8859_1)
        assertTrue("u8LiteralLatin1" in holder, "The literal is not rewritten")
        assertFalse("getU8" in facade, "The literal is still encoded at run time")
    }

    @Test
    fun failsWithAnotherKotlinVersion() {
        writeProject(kotlinVersion = "2.4.0")
        val result = runner("help").buildAndFail()
        assertContains(result.output, "requires Kotlin ${BuildConfig.KOTLIN_VERSION}, but the project uses Kotlin 2.4.0")
    }

    private fun writeProject(kotlinVersion: String) {
        val repositories = """
            maven { url = uri("${localMavenRepository.replace("\\", "/")}") }
            mavenCentral()
        """
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    $repositories
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    $repositories
                }
            }
            rootProject.name = "fixture"
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "$kotlinVersion"
                id("${BuildConfig.KOTLIN_PLUGIN_ID}") version "${BuildConfig.COMPILER_PLUGIN_VERSION}"
            }

            dependencies {
                implementation("${BuildConfig.COMPILER_PLUGIN_GROUP}:utf8-string:${BuildConfig.COMPILER_PLUGIN_VERSION}")
            }

            kotlin {
                compilerOptions {
                    // utf8-string is compiled with companion blocks, so its binaries are pre-release.
                    freeCompilerArgs.add("-Xskip-prerelease-check")
                }
            }
            """.trimIndent(),
        )
    }

    private fun runner(vararg arguments: String): GradleRunner =
        GradleRunner.create().withProjectDir(projectDir).withArguments(*arguments, "--stacktrace")
}
