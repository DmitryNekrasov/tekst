/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.generator

import java.io.File
import kotlin.system.exitProcess

const val UNICODE_VERSION: String = "18.0.0"
const val CLDR_VERSION: String = "48"

fun main(args: Array<String>) {
    val unicodeDir = File(args[0])
    val sourceDir = File(args[1])
    val marker = if (args.getOrNull(2) == "--check") File(args[3]) else null

    val files = generate(unicodeDir)
    if (marker == null) {
        for ((path, content) in files) sourceDir.resolve(path).writeText(content)
        println("Wrote ${files.keys.joinToString()} in $sourceDir")
        return
    }
    val stale = staleFiles(sourceDir, files)
    if (stale.isNotEmpty()) {
        System.err.println("Stale generated files in $sourceDir: ${stale.joinToString()}. Run ./gradlew generateUnicodeData.")
        exitProcess(1)
    }
    marker.parentFile.mkdirs()
    marker.writeText("")
}

// A checkout with CRLF line endings, as core.autocrlf makes on Windows, holds the same files.
fun staleFiles(sourceDir: File, files: Map<String, String>): List<String> = files.filter { (path, content) ->
    sourceDir.resolve(path).let { !it.exists() || it.readText().replace("\r\n", "\n") != content }
}.keys.toList()

fun generate(unicodeDir: File): Map<String, String> {
    val versionDir = unicodeDir.resolve(UNICODE_VERSION)
    val classes = classify(readGraphemeProperties(versionDir.resolve("ucd"), UNICODE_VERSION))
    val automaton = buildBreakAutomaton()
    verifyAutomaton(automaton)
    verifyAsciiFastPaths(classes, automaton)
    val boundaryTables = buildBoundaryTables(automaton)
    verifyBoundaryTables(classes, automaton, boundaryTables)

    val breakTest = readBreakTest(versionDir.resolve("ucd/auxiliary/GraphemeBreakTest.txt"), UNICODE_VERSION)
    val cldrTest = readCldrTests(unicodeDir.resolve("cldr-$CLDR_VERSION"))
    for (test in breakTest + cldrTest) {
        check(automaton.boundaries(test.codePoints.map { classes[it] }) == test.boundaries) {
            "The automaton fails the test ${test.codePoints.map { "%04X".format(it) }} (${test.comment})"
        }
    }
    val emojiTest = readEmojiTest(versionDir.resolve("emoji/emoji-test.txt"), UNICODE_VERSION)
    for (sequence in emojiTest) {
        check(automaton.boundaries(sequence.codePoints.map { classes[it] }).count { it } == 2) {
            "The emoji sequence ${sequence.codePoints.map { "%04X".format(it) }} is not one cluster"
        }
    }
    val table = buildClassTable(classes)

    return mapOf(
        "commonMain/kotlin/utf8string/GraphemeData.kt" to graphemeData(classes, automaton, boundaryTables, table),
        "commonTest/kotlin/utf8string/GraphemeBreakTestData.kt" to breakTestData(breakTest, cldrTest),
        "commonTest/kotlin/utf8string/GraphemeClassTestData.kt" to classTestData(classes),
        "commonTest/kotlin/utf8string/EmojiTestData.kt" to emojiTestData(emojiTest),
    )
}

private fun graphemeData(
    classes: IntArray,
    automaton: BreakAutomaton,
    boundaryTables: BoundaryTables,
    table: ClassTable,
): String {
    val file = KotlinFile(
        listOf(
            "unicode/$UNICODE_VERSION/ucd/auxiliary/GraphemeBreakProperty.txt",
            "unicode/$UNICODE_VERSION/ucd/emoji/emoji-data.txt",
            "unicode/$UNICODE_VERSION/ucd/DerivedCoreProperties.txt",
            "and the rules of UAX #29 revision 49",
        ),
    )
    file.stringConstant("The Unicode version of the grapheme cluster tables.", "UNICODE_VERSION", UNICODE_VERSION)
    file.intConstant(
        "Grapheme classes are code points that the rules cannot tell apart: one combination of Grapheme_Cluster_Break, " +
                "Extended_Pictographic and Indic_Conjunct_Break.",
        "GRAPHEME_CLASS_COUNT",
        GraphemeClass.entries.size,
    )
    file.intConstant("The class of every code point outside the table.", "GRAPHEME_CLASS_OTHER", GraphemeClass.Other.ordinal)
    file.intConstant(
        "The class of regional indicators, which make flags in pairs.",
        "GRAPHEME_CLASS_REGIONAL_INDICATOR",
        GraphemeClass.RegionalIndicator.ordinal,
    )
    check(classes.max() < 'Z' - 'A')
    file.stringConstant(
        "The class of each code point, as 'A' plus the class, in blocks of 64 code points that overlap.",
        "GRAPHEME_CLASS_DATA",
        String(CharArray(table.data.size) { 'A' + table.data[it] }),
    )
    check(table.index.max() + BLOCK_SIZE <= table.data.size && table.data.size < 0xD800)
    file.stringConstant(
        "The offset of each block in GRAPHEME_CLASS_DATA. Below U+20000 the block of a code point is cp shr 6; the 64 " +
                "blocks of U+E0000..U+E0FFF follow. Every other code point is Other.",
        "GRAPHEME_CLASS_INDEX",
        String(CharArray(table.index.size) { table.index[it].toChar() }),
    )
    // A row offset is the state times the number of classes, so that the automaton adds instead of multiplying.
    val classCount = GraphemeClass.entries.size
    val rows = String(CharArray(automaton.transitions.size) { i ->
        val transition = automaton.transitions[i]
        val boundary = if (transition >= BreakAutomaton.BOUNDARY) 0x8000 else 0
        (boundary + (transition and (BreakAutomaton.BOUNDARY - 1)) * classCount).toChar()
    })
    file.stringConstant(
        "The break automaton of ${automaton.stateCount} states, each a row of GRAPHEME_CLASS_COUNT chars. For class c, " +
                "the char at row offset r + c is the row offset of the next state, plus 0x8000 when a cluster boundary " +
                "comes before c; the next state is then the start state of c.",
        "GRAPHEME_TRANSITIONS",
        rows,
    )
    file.stringConstant(
        "The row offset of the state of a cluster that starts with each class.",
        "GRAPHEME_START_STATES",
        String(CharArray(automaton.startStates.size) { (automaton.startStates[it] * classCount).toChar() }),
    )
    file.intConstant(
        "Bit c is set when the state after a code point of class c is the start state of c, whatever text comes before.",
        "GRAPHEME_SYNC_CLASSES",
        (0..<classCount).filter { boundaryTables.sync[it] }.sumOf { 1 shl it },
        hex = true,
    )
    check(automaton.stateCount * classCount <= 0x3FFF)
    val pairs = String(CharArray(classCount * classCount) { pair ->
        val context = if (boundaryTables.context[pair]) 0x8000 else 0
        val safeState = boundaryTables.safeStates[pair]
        (context + if (safeState >= 0) 0x4000 + safeState * classCount else 0).toChar()
    })
    file.stringConstant(
        "For classes a and b, the char at a * GRAPHEME_CLASS_COUNT + b has 0x8000 when whether a cluster boundary comes " +
                "between a and b depends on the text before a, and 0x4000 when the state after b does not, plus the row " +
                "offset of that state.",
        "GRAPHEME_PAIR_ROWS",
        pairs,
    )
    return file.toString()
}

private fun breakTestData(breakTest: List<SegmentationTest>, cldrTest: List<SegmentationTest>): String {
    val file = KotlinFile(
        listOf(
            "unicode/$UNICODE_VERSION/ucd/auxiliary/GraphemeBreakTest.txt",
            "unicode/cldr-$CLDR_VERSION/TestSegmenter-*.txt",
        ),
    )
    file.stringConstant("The Unicode version of GRAPHEME_BREAK_TEST.", "GRAPHEME_BREAK_TEST_VERSION", UNICODE_VERSION)
    file.stringConstant(
        "The tests of GraphemeBreakTest.txt, one per line: the code points in hex, '/' where a cluster boundary is " +
                "expected and 'x' where there is none, then '#' and the numbers of the rules that decide each position.",
        "GRAPHEME_BREAK_TEST",
        breakTest.joinToString("") { markedLine(it) + "\n" },
        lines = true,
    )
    file.stringConstant(
        "The words of the CLDR $CLDR_VERSION TestSegmenter files for Indic scripts, in the same format with the script " +
                "after '#'.",
        "CLDR_GRAPHEME_TEST",
        cldrTest.joinToString("") { markedLine(it) + "\n" },
        lines = true,
    )
    return file.toString()
}

private fun markedLine(test: SegmentationTest): String = buildString {
    for ((i, boundary) in test.boundaries.withIndex()) {
        append(if (boundary) '/' else 'x')
        if (i < test.codePoints.size) append(' ').append("%04X".format(test.codePoints[i])).append(' ')
    }
    append(" # ").append(test.comment)
}

private fun classTestData(classes: IntArray): String {
    val file = KotlinFile(
        listOf(
            "unicode/$UNICODE_VERSION/ucd/auxiliary/GraphemeBreakProperty.txt",
            "unicode/$UNICODE_VERSION/ucd/emoji/emoji-data.txt",
            "unicode/$UNICODE_VERSION/ucd/DerivedCoreProperties.txt",
        ),
    )
    file.stringConstant(
        "The Grapheme_Cluster_Break, Extended_Pictographic and Indic_Conjunct_Break values of each class, one class per " +
                "line in class order.",
        "GRAPHEME_CLASS_PROPERTIES",
        GraphemeClass.entries.joinToString("") { "${it.clusterBreak} ${it.extendedPictographic} ${it.conjunctBreak}\n" },
        lines = true,
    )
    val runs = StringBuilder()
    for (codePoint in 0..<CODE_POINT_LIMIT) {
        if (codePoint == 0 || classes[codePoint] != classes[codePoint - 1]) {
            // Not %d, which writes the digits of the default locale.
            runs.append(codePoint.toString(16).uppercase()).append(' ').append(classes[codePoint]).append('\n')
        }
    }
    file.stringConstant(
        "The class of every code point as runs, one per line: the first code point of the run in hex and its class.",
        "GRAPHEME_CLASS_RUNS",
        runs.toString(),
        lines = true,
    )
    return file.toString()
}

private fun emojiTestData(emojiTest: List<EmojiSequence>): String {
    val file = KotlinFile(listOf("unicode/$UNICODE_VERSION/emoji/emoji-test.txt"))
    val rgi = emojiTest.filter { it.status == "fully-qualified" || it.status == "component" }
    file.linesConstant(
        "The fully-qualified and component sequences of emoji-test.txt, which make the RGI emoji set, in hex. UAX #29 says " +
                "that each sequence in the RGI emoji set is a single grapheme cluster.",
        "EMOJI_TEST_RGI",
        rgi.map { sequence -> sequence.codePoints.joinToString(" ") { "%04X".format(it) } },
    )
    return file.toString()
}
