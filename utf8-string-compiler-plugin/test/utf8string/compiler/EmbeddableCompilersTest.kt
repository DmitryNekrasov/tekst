/*
 * Copyright 2026 Dmitry Nekrasov and tekst library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.compiler

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.assertTrue

// The plugin is compiled against kotlin-compiler, but Gradle runs it in kotlin-compiler-embeddable and
// kotlin-native-compiler-embeddable, which relocate these packages to org.jetbrains.kotlin.*.
private val RELOCATED_PACKAGES = listOf(
    "com.intellij", "javax.inject", "com.google", "com.sampullara", "org.apache", "org.jdom", "org.picocontainer",
    "org.jline", "org.fusesource", "net.jpountz", "one.util.streamex", "it.unimi.dsi.fastutil",
    "kotlinx.collections.immutable", "com.fasterxml", "org.codehaus", "io.opentelemetry", "io.vavr", "org.antlr",
    "org.tukaani.xz",
).map { it.replace('.', '/') + "/" }

class EmbeddableCompilersTest {
    @Test
    fun pluginReferencesOnlyClassesOfTheEmbeddableCompilers() {
        val compilers = System.getProperty("embeddableCompilers").split(File.pathSeparator).map(::ZipFile)
        val classFiles = System.getProperty("pluginClassesDirs").split(File.pathSeparator)
            .flatMap { File(it).walk().filter { file -> file.extension == "class" }.toList() }
        assertTrue(classFiles.isNotEmpty(), "No plugin classes found")

        val problems = sortedSetOf<String>()
        for (classFile in classFiles) {
            for (name in referencedClasses(classFile.readBytes())) {
                when {
                    RELOCATED_PACKAGES.any { name.startsWith(it) } -> problems += "$name is relocated in the embeddable compilers"
                    name.startsWith("org/jetbrains/kotlin/") -> compilers
                        .filter { it.getEntry("$name.class") == null }
                        .forEach { problems += "$name is missing in ${File(it.name).name}" }
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    private fun referencedClasses(classFile: ByteArray): Set<String> {
        val input = DataInputStream(ByteArrayInputStream(classFile))
        input.skipBytes(8) // magic, minor and major version
        val count = input.readUnsignedShort()
        val strings = arrayOfNulls<String>(count)
        val classIndexes = mutableListOf<Int>()
        var index = 1
        while (index < count) {
            when (val tag = input.readUnsignedByte()) {
                1 -> strings[index] = input.readUTF()
                7 -> classIndexes += input.readUnsignedShort()
                8, 16, 19, 20 -> input.skipBytes(2)
                15 -> input.skipBytes(3)
                3, 4, 9, 10, 11, 12, 17, 18 -> input.skipBytes(4)
                5, 6 -> {
                    input.skipBytes(8)
                    index++ // Long and double constants take two entries.
                }
                else -> error("Unknown constant pool tag $tag")
            }
            index++
        }
        val types = Regex("L([\\w/$]+);")
        val fromClassEntries = classIndexes.mapNotNull { strings[it] }.filter { !it.startsWith("[") }
        val fromDescriptors = strings.filterNotNull().flatMap { string -> types.findAll(string).map { it.groupValues[1] } }
        return (fromClassEntries + fromDescriptors).toSet()
    }
}
