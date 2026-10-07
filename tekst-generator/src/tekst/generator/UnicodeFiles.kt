/*
 * Copyright 2026 Dmitry Nekrasov and tekst library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tekst.generator

import java.io.File

const val CODE_POINT_LIMIT: Int = 0x110000

class GraphemeProperties(
    val clusterBreak: Array<String>,
    val extendedPictographic: BooleanArray,
    val conjunctBreak: Array<String>,
)

fun readGraphemeProperties(ucd: File, version: String): GraphemeProperties {
    val clusterBreak = Array(CODE_POINT_LIMIT) { "Other" }
    val extendedPictographic = BooleanArray(CODE_POINT_LIMIT)
    val conjunctBreak = Array(CODE_POINT_LIMIT) { "None" }
    readDataFile(ucd.resolve("auxiliary/GraphemeBreakProperty.txt"), "# GraphemeBreakProperty-$version.txt") { range, fields ->
        for (codePoint in range) clusterBreak[codePoint] = fields[1]
    }
    readDataFile(ucd.resolve("emoji/emoji-data.txt"), "# Version: $version") { range, fields ->
        if (fields[1] == "Extended_Pictographic") for (codePoint in range) extendedPictographic[codePoint] = true
    }
    readDataFile(ucd.resolve("DerivedCoreProperties.txt"), "# DerivedCoreProperties-$version.txt") { range, fields ->
        if (fields.size == 3 && fields[1] == "InCB") for (codePoint in range) conjunctBreak[codePoint] = fields[2]
    }
    return GraphemeProperties(clusterBreak, extendedPictographic, conjunctBreak)
}

private fun readDataFile(file: File, versionLine: String, action: (IntRange, List<String>) -> Unit) {
    val lines = file.readLines()
    require(lines.take(20).any { it.trim() == versionLine }) { "$file has no \"$versionLine\" line in its header" }
    for (line in lines) {
        val data = line.substringBefore('#').trim()
        if (data.isEmpty()) continue
        val fields = data.split(';').map { it.trim() }
        val bounds = fields[0].split("..").map { it.toInt(16) }
        action(bounds.first()..bounds.last(), fields)
    }
}

class SegmentationTest(val codePoints: List<Int>, val boundaries: List<Boolean>, val comment: String)

fun readBreakTest(file: File, version: String): List<SegmentationTest> {
    val lines = file.readLines()
    require(lines.first() == "# GraphemeBreakTest-$version.txt") { "$file is not the $version test" }
    return lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
        val tokens = line.substringBefore('#').trim().split(' ', '\t').filter { it.isNotEmpty() }
        val rules = Regex("""\[(\d+\.\d+)]""").findAll(line.substringAfter('#')).map { it.groupValues[1] }.toList()
        parseMarkedCodePoints(tokens, line).also {
            require(rules.size == it.boundaries.size) { "Rule numbers do not match the positions in: $line" }
        }.let { SegmentationTest(it.codePoints, it.boundaries, rules.joinToString(" ")) }
    }
}

private fun parseMarkedCodePoints(tokens: List<String>, line: String): SegmentationTest {
    require(tokens.size % 2 == 1) { "Unexpected format: $line" }
    val boundaries = tokens.filterIndexed { i, _ -> i % 2 == 0 }.map {
        when (it) {
            "\u00F7" -> true
            "\u00D7" -> false
            else -> error("Unexpected marker '$it' in: $line")
        }
    }
    val codePoints = tokens.filterIndexed { i, _ -> i % 2 == 1 }.map { it.toInt(16) }
    return SegmentationTest(codePoints, boundaries, "")
}

class EmojiSequence(val codePoints: List<Int>, val status: String)

fun readEmojiTest(file: File, version: String): List<EmojiSequence> {
    val lines = file.readLines()
    val shortVersion = version.removeSuffix(".0")
    require(lines.take(20).any { it.trim() == "# Version: $shortVersion" }) { "$file is not the $version test" }
    return lines.map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.map { data ->
        val (codePoints, status) = data.split(';').map { it.trim() }
        EmojiSequence(codePoints.split(' ').filter { it.isNotEmpty() }.map { it.toInt(16) }, status)
    }
}

fun readCldrTests(dir: File): List<SegmentationTest> =
    dir.listFiles { file -> file.name.startsWith("TestSegmenter-") }!!.sortedBy { it.name }.flatMap { file ->
        val script = file.name.removePrefix("TestSegmenter-").removeSuffix(".txt")
        file.readLines().map { it.removePrefix("\uFEFF") }.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val (word, marked) = line.split(';').map { it.trim() }
            val parts = marked.split('\u00F7').filter { it.isNotEmpty() }
            require(parts.joinToString("") == word) { "The marked word does not match the word in $file: $line" }
            val codePoints = ArrayList<Int>()
            val boundaries = arrayListOf(true)
            for (part in parts) {
                val partCodePoints = part.codePoints().toArray().toList()
                codePoints += partCodePoints
                repeat(partCodePoints.size - 1) { boundaries += false }
                boundaries += true
            }
            SegmentationTest(codePoints, boundaries, script)
        }
    }
