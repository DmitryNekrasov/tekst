/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.generator

import java.io.File
import java.util.Locale
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GeneratorTest {
    private val sourceDir = createTempDirectory("generated").toFile()

    @AfterTest
    fun deleteSourceDir() {
        sourceDir.deleteRecursively()
    }

    @Test
    fun checkAcceptsTheFilesWithEitherLineEnding() {
        write(generated)
        assertEquals(emptyList(), staleFiles(sourceDir, generated))
        write(generated.mapValues { it.value.replace("\n", "\r\n") })
        assertEquals(emptyList(), staleFiles(sourceDir, generated))
    }

    @Test
    fun checkRejectsMissingAndEditedFiles() {
        write(generated)
        val (missing, edited) = generated.keys.toList()
        sourceDir.resolve(missing).delete()
        sourceDir.resolve(edited).appendText("// edited\n")
        assertEquals(listOf(missing, edited), staleFiles(sourceDir, generated))
    }

    @Test
    fun outputDoesNotDependOnTheLocale() {
        val default = Locale.getDefault()
        val persian = try {
            Locale.setDefault(Locale.forLanguageTag("fa-IR"))
            generate(unicodeDir)
        } finally {
            Locale.setDefault(default)
        }
        assertEquals(generated, persian)
    }

    private fun write(files: Map<String, String>) {
        for ((path, content) in files) sourceDir.resolve(path).apply { parentFile.mkdirs() }.writeText(content)
    }

    private companion object {
        val unicodeDir = File(System.getProperty("utf8string.unicodeDir"))

        val generated: Map<String, String> by lazy { generate(unicodeDir) }
    }
}
