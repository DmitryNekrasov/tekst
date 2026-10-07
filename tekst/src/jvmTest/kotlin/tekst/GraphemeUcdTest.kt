/*
 * Copyright 2026 Dmitry Nekrasov and tekst library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tekst

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

// Reads the Unicode files itself, which catches a bug that the generated tables and the generated test data share.
class GraphemeUcdTest {
    private val unicodeDir = File(System.getProperty("tekst.unicodeDir")).resolve(UNICODE_VERSION)

    @Test
    fun eachClassHasOneCombinationOfProperties() {
        val properties = readProperties()
        val classProperties = GRAPHEME_CLASS_PROPERTIES.lines().filter { it.isNotEmpty() }
        val classes = HashMap<String, Int>()
        for (codePoint in 0..0x10FFFF) {
            val bytes = utf8Bytes(codePoint)
            val cls = graphemeClassAt(bytes, 0, bytes.size, GraphemeTables.classes, GraphemeTables.index)
            if (properties[codePoint] != classProperties[cls] || classes.getOrPut(properties[codePoint]) { cls } != cls) {
                fail("U+%04X: class %d is %s, the UCD says %s".format(codePoint, cls, classProperties[cls], properties[codePoint]))
            }
        }
        assertEquals(GRAPHEME_CLASS_COUNT, classes.size)
    }

    @Test
    fun everyEmojiTestSequenceIsOneGrapheme() {
        var count = 0
        for (line in unicodeDir.resolve("emoji/emoji-test.txt").readLines()) {
            val data = line.substringBefore('#').trim()
            if (data.isEmpty()) continue
            val string = codePointsToString(data.substringBefore(';').trim().codePointsFromHex()).u8
            assertEquals(listOf(0, string.byteCount), string.iteratedBoundaries(), line)
            count++
        }
        assertEquals(5244, count)
    }

    private fun readProperties(): Array<String> {
        val clusterBreak = Array(0x110000) { "Other" }
        val pictographic = BooleanArray(0x110000)
        val conjunctBreak = Array(0x110000) { "None" }
        forEachRange("ucd/auxiliary/GraphemeBreakProperty.txt") { range, fields -> range.forEach { clusterBreak[it] = fields[1] } }
        forEachRange("ucd/emoji/emoji-data.txt") { range, fields ->
            if (fields[1] == "Extended_Pictographic") range.forEach { pictographic[it] = true }
        }
        forEachRange("ucd/DerivedCoreProperties.txt") { range, fields ->
            if (fields.size == 3 && fields[1] == "InCB") range.forEach { conjunctBreak[it] = fields[2] }
        }
        return Array(0x110000) { "${clusterBreak[it]} ${pictographic[it]} ${conjunctBreak[it]}" }
    }

    private fun forEachRange(path: String, action: (IntRange, List<String>) -> Unit) {
        for (line in unicodeDir.resolve(path).readLines()) {
            val fields = line.substringBefore('#').split(';').map { it.trim() }
            if (fields[0].isEmpty()) continue
            val bounds = fields[0].split("..").map { it.toInt(16) }
            action(bounds.first()..bounds.last(), fields)
        }
    }
}
