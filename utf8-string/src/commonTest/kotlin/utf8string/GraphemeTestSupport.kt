/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SegmentationTest(val codePoints: IntArray, val boundaries: BooleanArray, val line: String)

internal fun parseSegmentationTests(text: String): List<SegmentationTest> = text.lines().filter { it.isNotEmpty() }.map { line ->
    val tokens = line.substringBefore('#').trim().split(' ').filter { it.isNotEmpty() }
    SegmentationTest(
        codePoints = IntArray(tokens.size / 2) { tokens[2 * it + 1].toInt(16) },
        boundaries = BooleanArray(tokens.size / 2 + 1) { tokens[2 * it] == "/" },
        line = line,
    )
}

internal fun String.codePointsFromHex(): IntArray = split(' ').filter { it.isNotEmpty() }.map { it.toInt(16) }.toIntArray()

internal fun IntArray.toHex(): String = joinToString(" ") { it.toString(16) }

internal fun codePointsToString(codePoints: IntArray): String = buildString {
    for (codePoint in codePoints) {
        if (codePoint < 0x10000) {
            append(Char(codePoint))
        } else {
            append(Char(0xD800 + ((codePoint - 0x10000) shr 10)))
            append(Char(0xDC00 + ((codePoint - 0x10000) and 0x3FF)))
        }
    }
}

internal fun utf8Length(codePoint: Int): Int = when {
    codePoint < 0x80 -> 1
    codePoint < 0x800 -> 2
    codePoint < 0x10000 -> 3
    else -> 4
}

// The UTF-16 index where each code point starts and where the text ends, and the UTF-8 byte index at each of these
// UTF-16 indices.
internal fun charAndByteIndices(codePoints: IntArray): Pair<IntArray, IntArray> {
    val charIndices = IntArray(codePoints.size + 1)
    for ((i, codePoint) in codePoints.withIndex()) charIndices[i + 1] = charIndices[i] + if (codePoint < 0x10000) 1 else 2
    val byteIndices = IntArray(charIndices.last() + 1)
    for ((i, codePoint) in codePoints.withIndex()) {
        byteIndices[charIndices[i + 1]] = byteIndices[charIndices[i]] + utf8Length(codePoint)
    }
    return charIndices to byteIndices
}

// The UTF-8 bytes of a code point, surrogates included.
internal fun utf8Bytes(codePoint: Int): ByteArray = when (utf8Length(codePoint)) {
    1 -> byteArrayOf(codePoint.toByte())
    2 -> byteArrayOf((0xC0 or (codePoint shr 6)).toByte(), (0x80 or (codePoint and 0x3F)).toByte())
    3 -> byteArrayOf(
        (0xE0 or (codePoint shr 12)).toByte(),
        (0x80 or ((codePoint shr 6) and 0x3F)).toByte(),
        (0x80 or (codePoint and 0x3F)).toByte(),
    )

    else -> byteArrayOf(
        (0xF0 or (codePoint shr 18)).toByte(),
        (0x80 or ((codePoint shr 12) and 0x3F)).toByte(),
        (0x80 or ((codePoint shr 6) and 0x3F)).toByte(),
        (0x80 or (codePoint and 0x3F)).toByte(),
    )
}

internal fun Utf8String.iteratedBoundaries(): List<Int> {
    val boundaries = arrayListOf(0)
    for (grapheme in this) boundaries += boundaries.last() + grapheme.byteCount
    return boundaries
}

internal fun byteBoundaries(codePoints: IntArray, boundaries: BooleanArray): List<Int> {
    val result = ArrayList<Int>()
    var offset = 0
    for (i in boundaries.indices) {
        if (boundaries[i]) result += offset
        if (i < codePoints.size) offset += utf8Length(codePoints[i])
    }
    return result
}

// UAX #29 as the standard states it, with none of the library's automaton, table or fast paths.
// It classifies code points with the generated class runs.
internal object GraphemeModel {
    private class ClassProperties(val clusterBreak: String, val extendedPictographic: Boolean, val conjunctBreak: String)

    private val classProperties = GRAPHEME_CLASS_PROPERTIES.lines().filter { it.isNotEmpty() }.map {
        val (clusterBreak, extendedPictographic, conjunctBreak) = it.split(' ')
        ClassProperties(clusterBreak, extendedPictographic.toBoolean(), conjunctBreak)
    }
    private val runs = GRAPHEME_CLASS_RUNS.lines().filter { it.isNotEmpty() }.map { it.split(' ') }
    val runStarts: IntArray = IntArray(runs.size) { runs[it][0].toInt(16) }
    val runClasses: IntArray = IntArray(runs.size) { runs[it][1].toInt() }
    val classCount: Int get() = classProperties.size

    fun classOf(codePoint: Int): Int {
        var low = 0
        var high = runStarts.size - 1
        while (low < high) {
            val middle = (low + high + 1) ushr 1
            if (runStarts[middle] <= codePoint) low = middle else high = middle - 1
        }
        return runClasses[low]
    }

    // With legacyConjuncts, GB9c is the rule of Unicode 15.1 to 17.0, which also needs a consonant before the linker.
    fun boundaries(codePoints: IntArray, legacyConjuncts: Boolean = false): BooleanArray {
        val text = codePoints.map { classProperties[classOf(it)] }
        return BooleanArray(text.size + 1) { i -> i == 0 || i == text.size || isBoundary(text, i, legacyConjuncts) }
    }

    private fun isBoundary(text: List<ClassProperties>, i: Int, legacyConjuncts: Boolean): Boolean {
        val previous = text[i - 1].clusterBreak
        val next = text[i].clusterBreak
        return when {
            previous == "CR" && next == "LF" -> false // GB3
            previous == "CR" || previous == "LF" || previous == "Control" -> true // GB4
            next == "CR" || next == "LF" || next == "Control" -> true // GB5
            previous == "L" && (next == "L" || next == "V" || next == "LV" || next == "LVT") -> false // GB6
            (previous == "LV" || previous == "V") && (next == "V" || next == "T") -> false // GB7
            (previous == "LVT" || previous == "T") && next == "T" -> false // GB8
            next == "Extend" || next == "ZWJ" -> false // GB9
            next == "SpacingMark" -> false // GB9a
            previous == "Prepend" -> false // GB9b
            text[i].conjunctBreak == "Consonant" && joinsConjunct(text, i, legacyConjuncts) -> false // GB9c
            text[i].extendedPictographic && previous == "ZWJ" && followsPictographic(text, i - 2) -> false // GB11
            previous == "Regional_Indicator" && next == "Regional_Indicator" && regionalIndicatorsBefore(text, i) % 2 == 1 -> false
            else -> true // GB999
        }
    }

    // GB9c: InCB=Linker InCB=Extend* x InCB=Consonant, or before Unicode 18.0
    // InCB=Consonant [InCB=Extend InCB=Linker]* InCB=Linker [InCB=Extend InCB=Linker]* x InCB=Consonant.
    private fun joinsConjunct(text: List<ClassProperties>, i: Int, legacy: Boolean): Boolean {
        var j = i - 1
        if (!legacy) {
            while (j >= 0 && text[j].conjunctBreak == "Extend") j--
            return j >= 0 && text[j].conjunctBreak == "Linker"
        }
        var linker = false
        while (j >= 0 && (text[j].conjunctBreak == "Extend" || text[j].conjunctBreak == "Linker")) {
            if (text[j].conjunctBreak == "Linker") linker = true
            j--
        }
        return linker && j >= 0 && text[j].conjunctBreak == "Consonant"
    }

    // GB11: ExtPict Extend* ZWJ x ExtPict, where i is the index before the ZWJ.
    private fun followsPictographic(text: List<ClassProperties>, i: Int): Boolean {
        var j = i
        while (j >= 0 && text[j].clusterBreak == "Extend") j--
        return j >= 0 && text[j].extendedPictographic
    }

    private fun regionalIndicatorsBefore(text: List<ClassProperties>, i: Int): Int {
        var j = i - 1
        while (j >= 0 && text[j].clusterBreak == "Regional_Indicator") j--
        return i - 1 - j
    }
}

// An oracle of Unicode 17.0 (older) may differ only by the GB9c rule of 18.0. Returns whether it differed.
internal fun checkAgainstOracle(codePoints: IntArray, ours: List<Int>, oracle: BooleanArray, older: Boolean): Boolean {
    if (ours == byteBoundaries(codePoints, oracle)) return false
    val message = codePoints.toHex()
    assertTrue(older, "The oracle disagrees: $message")
    assertEquals(byteBoundaries(codePoints, GraphemeModel.boundaries(codePoints)), ours, "Ours is not Unicode 18.0: $message")
    val unicode17 = GraphemeModel.boundaries(codePoints, legacyConjuncts = true)
    assertTrue(oracle.contentEquals(unicode17), "The oracle is not Unicode 17.0: $message")
    return true
}

// Code points spread evenly over the grapheme classes, so that every rule meets every class.
internal class GraphemeCorpus(excluded: List<IntRange> = emptyList()) {
    private val ranges: List<IntArray> = List(GraphemeModel.classCount) { cls ->
        val bounds = ArrayList<Int>()
        val starts = GraphemeModel.runStarts
        for (i in starts.indices) {
            if (GraphemeModel.runClasses[i] != cls) continue
            val end = if (i + 1 < starts.size) starts[i + 1] - 1 else 0x10FFFF
            var pieces = listOf(starts[i]..end)
            for (hole in excluded + listOf(0xD800..0xDFFF)) {
                pieces = pieces.flatMap { listOf(it.first..minOf(it.last, hole.first - 1), maxOf(it.first, hole.last + 1)..it.last) }
                    .filter { !it.isEmpty() }
            }
            for (piece in pieces) bounds += listOf(piece.first, piece.last)
        }
        bounds.toIntArray()
    }
    private val classes = ranges.indices.filter { ranges[it].isNotEmpty() }.toIntArray()

    private fun codePoint(random: Random, cls: Int): Int {
        val bounds = ranges[cls]
        val range = random.nextInt(bounds.size / 2)
        return random.nextInt(bounds[2 * range], bounds[2 * range + 1] + 1)
    }

    // A few classes give long runs such as RI RI RI or linker chains. ASCII with its neighbors covers the fast paths.
    fun text(random: Random): IntArray {
        val length = random.nextInt(1, 17)
        return when (random.nextInt(3)) {
            0 -> IntArray(length) { codePoint(random, classes[random.nextInt(classes.size)]) }
            1 -> {
                val alphabet = IntArray(random.nextInt(1, 4)) { classes[random.nextInt(classes.size)] }
                IntArray(length) { codePoint(random, alphabet[random.nextInt(alphabet.size)]) }
            }

            else -> IntArray(length) {
                if (random.nextInt(3) == 0) ASCII_NEIGHBORS[random.nextInt(ASCII_NEIGHBORS.size)] else random.nextInt(0x80)
            }
        }
    }

    private companion object {
        val ASCII_NEIGHBORS = intArrayOf(0x0308, 0x200D, 0x0903, 0x0600, 0x094D, 0x0D, 0x0A, 0x85, 0xAD, 0xA9)
    }
}

// The code points whose class properties differ between the UCD files of Unicode 17.0 and 18.0, which an oracle of 17.0
// must not see.
internal val CHANGED_SINCE_UNICODE_17: List<IntRange> = listOf(
    0x05C8..0x05C9, 0x0B53..0x0B54, 0x1ADE..0x1ADF, 0x1AEC..0x1AF0, 0x1CF5..0x1CF6, 0x10ECB..0x10ECF, 0x10EF0..0x10EF9,
    0x11A3A..0x11A3A, 0x11B0A..0x11B0A, 0x11DF0..0x11DF1, 0x1D127..0x1D128, 0x1D250..0x1D252, 0x1D25B..0x1D25C,
    0x1D25F..0x1D25F, 0x1D280..0x1D281, 0x1F1AE..0x1F1AE, 0x1F7DA..0x1F7DB, 0x1F7F1..0x1F7FF,
)

internal fun isChangedSinceUnicode17(codePoint: Int): Boolean = CHANGED_SINCE_UNICODE_17.any { codePoint in it }
