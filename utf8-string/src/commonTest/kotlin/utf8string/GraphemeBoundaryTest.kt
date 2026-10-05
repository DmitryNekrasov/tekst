/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GraphemeBoundaryTest {
    @Test
    fun conformanceTestsAtEveryIndex() {
        for (test in parseSegmentationTests(GRAPHEME_BREAK_TEST) + parseSegmentationTests(CLDR_GRAPHEME_TEST)) {
            val string = codePointsToString(test.codePoints).u8
            assertBoundariesAtEveryIndex(byteBoundaries(test.codePoints, test.boundaries), string, test.line)
        }
    }

    @Test
    fun randomTextsAtEveryIndex() {
        val corpus = GraphemeCorpus()
        val random = Random(33)
        repeat(RANDOM_TEXT_COUNT / 10) {
            val codePoints = corpus.text(random)
            val boundaries = byteBoundaries(codePoints, GraphemeModel.boundaries(codePoints))
            assertBoundariesAtEveryIndex(boundaries, codePointsToString(codePoints).u8, codePoints.toHex())
        }
    }

    @Test
    fun randomWalks() {
        val corpus = GraphemeCorpus()
        val random = Random(34)
        repeat(RANDOM_TEXT_COUNT / 10) {
            val codePoints = if (random.nextBoolean()) corpus.text(random) + corpus.text(random) else flagRuns(random)
            val string = codePointsToString(codePoints).u8
            val boundaries = byteBoundaries(codePoints, GraphemeModel.boundaries(codePoints))
            assertRandomWalk(boundaries, string, random, codePoints.toHex())
        }
    }

    @Test
    fun walksBackAsForward() {
        val corpus = GraphemeCorpus()
        val random = Random(35)
        repeat(RANDOM_TEXT_COUNT / 10) {
            val codePoints = if (random.nextBoolean()) corpus.text(random) + corpus.text(random) else flagRuns(random)
            val string = codePointsToString(codePoints).u8
            val backward = arrayListOf(string.byteCount)
            val graphemes = string.iterator(string.byteCount)
            while (graphemes.hasPrevious()) {
                val end = graphemes.index
                assertEquals(end - graphemes.skipPrevious(), graphemes.next().byteCount, codePoints.toHex())
                backward += graphemes.skipPrevious()
            }
            assertEquals(string.iteratedBoundaries(), backward.asReversed(), codePoints.toHex())
        }
    }

    @Test
    fun previousReturnsTheSharedAsciiGraphemes() {
        val string = "a\r\n\u00E9".u8
        val graphemes = string.iterator(string.byteCount)
        assertEquals("\u00E9", graphemes.previous().toString())
        assertSame(AsciiGraphemes.crLf, graphemes.previous())
        assertSame(AsciiGraphemes.single['a'.code], graphemes.previous())
    }

    @Test
    fun endsAndIndicesOutside() {
        val string = "a\u0301b".u8
        assertEquals(-1, string.nextGraphemeBoundary(4))
        assertEquals(-1, string.previousGraphemeBoundary(0))
        val calls = listOf<(Int) -> Any>(
            string::isGraphemeBoundary,
            string::nextGraphemeBoundary,
            string::previousGraphemeBoundary,
            string::iterator,
        )
        for (index in listOf(-1, 5, Int.MIN_VALUE, Int.MAX_VALUE)) {
            for (call in calls) {
                val exception = assertFailsWith<IndexOutOfBoundsException> { call(index) }
                assertEquals("index: $index, byteCount: 4", exception.message)
            }
        }
        val empty = "".u8
        assertTrue(empty.isGraphemeBoundary(0))
        assertEquals(-1, empty.nextGraphemeBoundary(0))
        assertEquals(-1, empty.previousGraphemeBoundary(0))
        val graphemes = empty.iterator(0)
        assertFalse(graphemes.hasNext())
        assertFalse(graphemes.hasPrevious())
        assertFailsWith<NoSuchElementException> { graphemes.next() }
        assertFailsWith<NoSuchElementException> { graphemes.previous() }
        assertFailsWith<NoSuchElementException> { graphemes.skipNext() }
        assertFailsWith<NoSuchElementException> { graphemes.skipPrevious() }
    }

    @Test
    fun takeAndDropRandomTexts() {
        val corpus = GraphemeCorpus()
        val random = Random(36)
        repeat(RANDOM_TEXT_COUNT / 10) {
            val codePoints = if (random.nextBoolean()) corpus.text(random) else flagRuns(random)
            val boundaries = byteBoundaries(codePoints, GraphemeModel.boundaries(codePoints))
            val count = boundaries.size - 1
            val n = random.nextInt(count + 2)
            val string = codePointsToString(codePoints).u8
            // Known and unknown lengths take different paths.
            if (random.nextBoolean()) string.length
            val message = "$n of ${codePoints.toHex()}"
            val bytes = string.toByteArray()
            fun part(from: Int, to: Int): Utf8String = bytes.decodeToString(from, to).u8
            val first = boundaries[minOf(n, count)]
            val last = boundaries[maxOf(count - n, 0)]
            assertPart(part(0, first), string.take(n), "take($message)")
            assertPart(part(first, bytes.size), string.drop(n), "drop($message)")
            assertPart(part(last, bytes.size), string.takeLast(n), "takeLast($message)")
            assertPart(part(0, last), string.dropLast(n), "dropLast($message)")
            assertEquals(count, string.length)
        }
    }

    @Test
    fun takeAndDropReturnThisWhenNothingChanges() {
        val string = "a\u0301\uD83C\uDDEA\uD83C\uDDF8\r\n".u8
        for (result in listOf(string.take(3), string.take(Int.MAX_VALUE), string.drop(0))) assertSame(string, result)
        for (result in listOf(string.takeLast(3), string.takeLast(Int.MAX_VALUE), string.dropLast(0))) {
            assertSame(string, result)
        }
        val calls = listOf<(Int) -> Utf8String>(string::take, string::drop, string::takeLast, string::dropLast)
        for (call in calls) {
            val exception = assertFailsWith<IllegalArgumentException> { call(-1) }
            assertEquals("Requested grapheme count -1 is less than zero.", exception.message)
        }
    }

    @Test
    fun partsOfTheEmptyString() {
        val empty = "".u8
        for (n in listOf(0, 1, Int.MAX_VALUE)) {
            for (part in listOf(empty.take(n), empty.drop(n), empty.takeLast(n), empty.dropLast(n))) {
                assertUtf8Equals(empty, part, "a part of the empty string for $n")
                assertEquals(0, part.length)
            }
            assertSame(empty, empty.take(n))
            assertSame(empty, empty.takeLast(n))
        }
        assertFailsWith<IllegalArgumentException> { empty.dropLast(-1) }
    }

    @Test
    fun kernelStaysInItsPartOfTheArray() {
        // Bytes that would change the boundaries of the text if a query read them: before it a regional indicator,
        // Prepend and continuation bytes, after it a combining mark, ZWJ, LF and a lead byte.
        val before = listOf("F0 9F 87 A6", "D8 80", "80", "80 80 80", "0D").map { it.hexToBytes() }
        val after = listOf("CC 81", "E2 80 8D", "0A", "F0", "F0 9F 87 A6").map { it.hexToBytes() }
        val corpus = GraphemeCorpus()
        val random = Random(37)
        repeat(RANDOM_TEXT_COUNT / 100) {
            val codePoints = if (random.nextBoolean()) corpus.text(random) else flagRuns(random)
            val text = codePointsToString(codePoints).u8.toByteArray()
            val prefix = before.random(random)
            val array = prefix + text + after.random(random)
            val start = prefix.size
            val end = start + text.size
            val message = codePoints.toHex()
            fun shiftBack(index: Int): Int = if (index < 0) index else index - start
            for (index in 0..text.size) {
                assertEquals(
                    isGraphemeBoundaryAt(text, 0, text.size, index),
                    isGraphemeBoundaryAt(array, start, end, start + index),
                    "isGraphemeBoundaryAt($index) of $message",
                )
                assertEquals(
                    nextGraphemeBoundaryAt(text, 0, text.size, index),
                    shiftBack(nextGraphemeBoundaryAt(array, start, end, start + index)),
                    "nextGraphemeBoundaryAt($index) of $message",
                )
                assertEquals(
                    previousGraphemeBoundaryAt(text, 0, text.size, index),
                    shiftBack(previousGraphemeBoundaryAt(array, start, end, start + index)),
                    "previousGraphemeBoundaryAt($index) of $message",
                )
            }
        }
    }

    private fun assertBoundariesAtEveryIndex(boundaries: List<Int>, string: Utf8String, message: String) {
        val indices = 0..string.byteCount
        val set = boundaries.toSet()
        assertEquals(
            indices.map { it in set },
            indices.map { string.isGraphemeBoundary(it) },
            "isGraphemeBoundary of $message",
        )
        assertEquals(
            indices.map { index -> boundaries.firstOrNull { it > index } ?: -1 },
            indices.map { string.nextGraphemeBoundary(it) },
            "nextGraphemeBoundary of $message",
        )
        assertEquals(
            indices.map { index -> boundaries.lastOrNull { it < index } ?: -1 },
            indices.map { string.previousGraphemeBoundary(it) },
            "previousGraphemeBoundary of $message",
        )
        assertEquals(
            indices.map { index -> boundaries.last { it <= index } },
            indices.map { string.iterator(it).index },
            "iterator(index) of $message",
        )
    }

    // Moves the iterator at random, with all 4 kinds of steps, and checks each step against the boundaries.
    private fun assertRandomWalk(boundaries: List<Int>, string: Utf8String, random: Random, message: String) {
        val bytes = string.toByteArray()
        val start = random.nextInt(string.byteCount + 1)
        val graphemes = string.iterator(start)
        var at = boundaries.indexOfLast { it <= start }
        var endChecked = false
        repeat(60) {
            assertEquals(boundaries[at], graphemes.index, message)
            assertEquals(at < boundaries.size - 1, graphemes.hasNext(), message)
            assertEquals(at > 0, graphemes.hasPrevious(), message)
            val forward = random.nextBoolean()
            val skip = random.nextBoolean()
            if (forward && at == boundaries.size - 1 || !forward && at == 0) {
                // Once a walk, since an exception takes long on JS.
                if (endChecked) return@repeat
                endChecked = true
                assertFailsWith<NoSuchElementException>(message) {
                    when {
                        forward && skip -> graphemes.skipNext()
                        forward -> graphemes.next()
                        skip -> graphemes.skipPrevious()
                        else -> graphemes.previous()
                    }
                }
                return@repeat
            }
            val grapheme = if (forward) boundaries[at]..<boundaries[at + 1] else boundaries[at - 1]..<boundaries[at]
            at += if (forward) 1 else -1
            if (skip) {
                assertEquals(boundaries[at], if (forward) graphemes.skipNext() else graphemes.skipPrevious(), message)
            } else {
                val expected = Grapheme(bytes, grapheme.first, grapheme.last + 1)
                assertEquals(expected, if (forward) graphemes.next() else graphemes.previous(), message)
            }
        }
    }

    private fun assertPart(expected: Utf8String, actual: Utf8String, message: String) {
        assertUtf8Equals(expected, actual, message)
        assertEquals(expected.length, actual.length, "length of $message")
    }
}

// Runs of regional indicators, each followed by a code point that joins the next run (Prepend), a letter, a mark that
// joins the run, or ZWJ.
internal fun flagRuns(random: Random): IntArray {
    val codePoints = ArrayList<Int>()
    repeat(random.nextInt(1, 5)) {
        repeat(random.nextInt(10)) { codePoints += 0x1F1E6 + random.nextInt(26) }
        codePoints += intArrayOf(0x0600, 0x61, 0x0301, 0x200D).random(random)
    }
    return codePoints.toIntArray()
}

internal fun IntArray.toHex(): String = joinToString(" ") { it.toString(16).uppercase() }

internal fun String.hexToBytes(): ByteArray = split(' ').map { it.toInt(16).toByte() }.toByteArray()
