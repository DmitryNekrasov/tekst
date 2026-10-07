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
import kotlin.test.assertTrue

class CharSequenceGraphemeBoundaryTest {
    @Test
    fun conformanceTestsAtEveryIndex() {
        for (test in parseSegmentationTests(GRAPHEME_BREAK_TEST) + parseSegmentationTests(CLDR_GRAPHEME_TEST)) {
            val text = codePointsToString(test.codePoints)
            assertBoundariesAtEveryIndex(charBoundaries(test.codePoints, test.boundaries), text, test.line)
        }
    }

    @Test
    fun eachRgiEmojiSequenceIsOneGrapheme() {
        for (sequence in EMOJI_TEST_RGI.flatMap { it.lines() }.filter { it.isNotEmpty() }) {
            val text = codePointsToString(sequence.codePointsFromHex())
            assertEquals(text.length, text.nextGraphemeBoundary(0), sequence)
            assertEquals(0, text.previousGraphemeBoundary(text.length), sequence)
            for (index in 1..<text.length) assertFalse(text.isGraphemeBoundary(index), "$index of $sequence")
        }
    }

    @Test
    fun randomTextsAtEveryIndex() {
        val corpus = GraphemeCorpus()
        val random = Random(41)
        repeat(RANDOM_TEXT_COUNT / 10) {
            val codePoints = if (random.nextBoolean()) corpus.text(random) else flagRuns(random)
            val boundaries = charBoundaries(codePoints, GraphemeModel.boundaries(codePoints))
            assertBoundariesAtEveryIndex(boundaries, codePointsToString(codePoints), codePoints.toHex())
        }
    }

    @Test
    fun randomTextsAsUtf8String() {
        val corpus = GraphemeCorpus()
        val random = Random(42)
        repeat(RANDOM_TEXT_COUNT / 10) {
            val codePoints = if (random.nextBoolean()) corpus.text(random) else flagRuns(random)
            assertSameAsUtf8String(codePointsToString(codePoints), codePoints.toHex())
        }
    }

    @Test
    fun loneSurrogatesCountAsReplacementCharacters() {
        val corpus = GraphemeCorpus()
        val random = Random(43)
        repeat(RANDOM_TEXT_COUNT / 10) {
            val codePoints = if (random.nextBoolean()) corpus.text(random) else flagRuns(random)
            val text = withLoneSurrogates(codePoints, random)
            val hex = IntArray(text.length) { text[it].code }.toHex()
            assertSameAsUtf8String(text, hex)
            assertEquals((0..text.length).filter { text.isGraphemeBoundary(it) }, walkBoundaries(text), hex)
        }
        val lowFirst = Char(0xDDEA) + "\uD83C\uDDF8"
        assertEquals(listOf(0, 1, 3), (0..lowFirst.length).filter { lowFirst.isGraphemeBoundary(it) })
        val highLast = "\uD83C\uDDEA" + Char(0xD83C)
        assertEquals(listOf(0, 2, 3), (0..highLast.length).filter { highLast.isGraphemeBoundary(it) })
    }

    @Test
    fun queriesStayInTheirRange() {
        // Chars that would change the boundaries of the text if a query read them.
        val before = listOf("\uD83C\uDDE6", "\u0600", "\r")
        val after = listOf("\u0301", "\u200D", "\n", "\uD83C\uDDE6")
        val corpus = GraphemeCorpus()
        val random = Random(44)
        repeat(RANDOM_TEXT_COUNT / 100) {
            val codePoints = if (random.nextBoolean()) corpus.text(random) else flagRuns(random)
            val text = codePointsToString(codePoints)
            val prefix = before.random(random)
            val whole = prefix + text + after.random(random)
            assertRangeAsCopy(whole, prefix.length, prefix.length + text.length, codePoints.toHex())
        }
    }

    @Test
    fun rangesThatCutSurrogatePairs() {
        val corpus = GraphemeCorpus()
        val random = Random(45)
        repeat(RANDOM_TEXT_COUNT / 100) {
            val codePoints = if (random.nextBoolean()) corpus.text(random) else flagRuns(random)
            val text = codePointsToString(codePoints)
            val start = random.nextInt(text.length + 1)
            val end = random.nextInt(start, text.length + 1)
            val message = "$start..<$end of ${codePoints.toHex()}"
            assertRangeAsCopy(text, start, end, message)
            assertSameAsUtf8String(text.substring(start, end), message)
        }
        val flags = "\uD83C\uDDEA\uD83C\uDDF8"
        assertTrue(flags.isGraphemeBoundary(1, 0, 1))
        assertEquals(2, flags.nextGraphemeBoundary(1, 1, 4))
        assertEquals(2, flags.previousGraphemeBoundary(4, 1, 4))
        assertFalse(flags.isGraphemeBoundary(3, 1, 4))
    }

    @Test
    fun rangesThatCutTheContextOfARule() {
        val cases = listOf(
            Triple("D A", 0, 1),
            Triple("D A", 1, 2),
            Triple("1F600", 0, 1),
            Triple("1F600", 1, 2),
            Triple("1F600", 0, 0),
            Triple("1F600", 1, 1),
            Triple("1F1E6 1F1E6 1F1E6 1F1E6", 2, 6),
            Triple("1F469 200D 1F4BB", 1, 5),
            Triple("1F469 200D 1F4BB", 2, 5),
            Triple("915 94D 937", 1, 3),
            Triple("600 61", 1, 2),
        )
        for ((hex, start, end) in cases) {
            assertRangeAsCopy(codePointsToString(hex.codePointsFromHex()), start, end, "$start..<$end of $hex")
        }
    }

    @Test
    fun everyCharSequenceGivesTheSameBoundaries() {
        val text = codePointsToString(EDITOR_EXAMPLE.codePointsFromHex()) + " \u0915\u094D\u0924 e\u0301\r\n"
        val receivers = listOf<CharSequence>(StringBuilder(text), CharsView(text.toCharArray()))
        for ((n, receiver) in receivers.withIndex()) {
            for (index in 0..text.length) {
                val message = "$index of receiver $n"
                assertEquals(text.isGraphemeBoundary(index), receiver.isGraphemeBoundary(index), message)
                assertEquals(text.nextGraphemeBoundary(index), receiver.nextGraphemeBoundary(index), message)
                assertEquals(text.previousGraphemeBoundary(index), receiver.previousGraphemeBoundary(index), message)
            }
        }
    }

    @Test
    fun endsAndArgumentsOutside() {
        val text = "a\u0301b"
        assertEquals(-1, text.nextGraphemeBoundary(3))
        assertEquals(-1, text.previousGraphemeBoundary(0))
        assertEquals(-1, text.nextGraphemeBoundary(2, 0, 2))
        assertEquals(-1, text.previousGraphemeBoundary(2, 2, 3))
        assertTrue("".isGraphemeBoundary(0))
        assertEquals(-1, "".nextGraphemeBoundary(0))
        assertEquals(-1, "".previousGraphemeBoundary(0))
        assertTrue(text.isGraphemeBoundary(1, 1, 1))
        assertEquals(-1, text.nextGraphemeBoundary(1, 1, 1))
        assertEquals(-1, text.previousGraphemeBoundary(1, 1, 1))

        val calls = listOf<(Int, Int, Int) -> Any>(
            { index, start, end -> text.isGraphemeBoundary(index, start, end) },
            { index, start, end -> text.nextGraphemeBoundary(index, start, end) },
            { index, start, end -> text.previousGraphemeBoundary(index, start, end) },
        )
        for (call in calls) {
            for (index in listOf(-1, 4, Int.MIN_VALUE, Int.MAX_VALUE)) {
                val exception = assertFailsWith<IndexOutOfBoundsException> { call(index, 0, 3) }
                assertEquals("index: $index, startIndex: 0, endIndex: 3", exception.message)
            }
            val outsideRange = assertFailsWith<IndexOutOfBoundsException> { call(3, 1, 2) }
            assertEquals("index: 3, startIndex: 1, endIndex: 2", outsideRange.message)
            // The bounds are checked first, then their order, and then the index.
            for ((start, end) in listOf(-1 to 3, 0 to 4, Int.MIN_VALUE to 0, 0 to Int.MAX_VALUE, -1 to -2, 5 to 4)) {
                val exception = assertFailsWith<IndexOutOfBoundsException> { call(0, start, end) }
                assertEquals("startIndex: $start, endIndex: $end, length: 3", exception.message)
            }
            for ((index, start, end) in listOf(Triple(1, 2, 1), Triple(3, 4, 3), Triple(0, 0, -1), Triple(5, 2, 1))) {
                val inverted = assertFailsWith<IllegalArgumentException> { call(index, start, end) }
                assertEquals("startIndex: $start > endIndex: $end", inverted.message)
            }
        }
        val wholeText = assertFailsWith<IndexOutOfBoundsException> { text.nextGraphemeBoundary(4) }
        assertEquals("index: 4, startIndex: 0, endIndex: 3", wholeText.message)
    }

    // Single queries, and walks with startIndex at the last boundary, since with startIndex 0 each call would read the
    // run of flags again.
    @Test
    fun longInputs() {
        val marks = "a" + "\u0301".repeat(100_000)
        assertEquals(marks.length, marks.nextGraphemeBoundary(0))
        assertEquals(marks.length, marks.nextGraphemeBoundary(50_000))
        assertEquals(0, marks.previousGraphemeBoundary(marks.length))
        assertFalse(marks.isGraphemeBoundary(50_000))

        val flags = "\uD83C\uDDE6".repeat(100_001)
        assertEquals(4, flags.nextGraphemeBoundary(0))
        assertEquals(100_004, flags.nextGraphemeBoundary(100_001))
        assertEquals(100_000, flags.previousGraphemeBoundary(100_001))
        assertEquals(200_000, flags.previousGraphemeBoundary(flags.length))
        assertTrue(flags.isGraphemeBoundary(100_000))
        assertFalse(flags.isGraphemeBoundary(100_002))
        assertEquals((0..50_000).map { it * 4 } + 200_002, walkBoundaries(flags))

        val emoji = "\uD83D\uDED1" + "\u0308".repeat(100_000) + "\u200D\uD83D\uDED1"
        assertEquals(emoji.length, emoji.nextGraphemeBoundary(0))
        assertEquals(0, emoji.previousGraphemeBoundary(emoji.length))
        assertFalse(emoji.isGraphemeBoundary(emoji.length - 2))

        val conjunct = "\u0915\u094D" + "\u0308".repeat(100_000) + "\u0924"
        assertEquals(0, conjunct.previousGraphemeBoundary(conjunct.length))
        assertFalse(conjunct.isGraphemeBoundary(conjunct.length - 1))

        val chain = "\u0915" + "\u094D\u0915".repeat(50_000)
        assertEquals(chain.length, chain.nextGraphemeBoundary(0))
        assertEquals(0, chain.previousGraphemeBoundary(chain.length))

        val withoutLinker = "a" + "\u200C".repeat(100_000) + "\u200D\u0915"
        assertTrue(withoutLinker.isGraphemeBoundary(withoutLinker.length - 1))
        assertEquals(withoutLinker.length - 1, withoutLinker.previousGraphemeBoundary(withoutLinker.length))
    }

    @Test
    fun editorExample() {
        val text = codePointsToString(EDITOR_EXAMPLE.codePointsFromHex())
        val boundaries = listOf(0, 11, 15, 19, 23, 37, 44, 52, 57, 63, 66, 75, 80, 95, 105, 112)
        assertEquals(boundaries, (0..text.length).filter { text.isGraphemeBoundary(it) })
        assertEquals(boundaries.drop(1), boundaries.dropLast(1).map { text.nextGraphemeBoundary(it) })
        assertEquals(boundaries.dropLast(1), boundaries.drop(1).map { text.previousGraphemeBoundary(it) })
    }

    private fun assertBoundariesAtEveryIndex(boundaries: List<Int>, text: CharSequence, message: String) {
        val indices = 0..text.length
        val set = boundaries.toSet()
        assertEquals(
            indices.map { it in set },
            indices.map { text.isGraphemeBoundary(it) },
            "isGraphemeBoundary of $message",
        )
        assertEquals(
            indices.map { index -> boundaries.firstOrNull { it > index } ?: -1 },
            indices.map { text.nextGraphemeBoundary(it) },
            "nextGraphemeBoundary of $message",
        )
        assertEquals(
            indices.map { index -> boundaries.lastOrNull { it < index } ?: -1 },
            indices.map { text.previousGraphemeBoundary(it) },
            "previousGraphemeBoundary of $message",
        )
        assertEquals(boundaries, walkBoundaries(text), "walk of $message")
    }

    private fun walkBoundaries(text: CharSequence): List<Int> {
        val boundaries = arrayListOf(0)
        var index = 0
        while (index < text.length) {
            index = text.nextGraphemeBoundary(index, startIndex = index, endIndex = text.length)
            boundaries += index
        }
        return boundaries
    }

    private fun assertSameAsUtf8String(text: String, message: String) {
        val string = text.u8
        val byteIndices = IntArray(text.length + 1) { -1 }
        var byteIndex = 0
        var i = 0
        while (i < text.length) {
            byteIndices[i] = byteIndex
            val char = text[i]
            val pair = char.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()
            // utf8Length gives a lone surrogate 3 bytes, as many as the U+FFFD that .u8 writes for it.
            byteIndex += if (pair) 4 else utf8Length(char.code)
            i += if (pair) 2 else 1
        }
        byteIndices[text.length] = byteIndex
        assertEquals(string.byteCount, byteIndex, "byteCount of $message")
        fun byteIndexOf(charIndex: Int): Int = if (charIndex < 0) -1 else byteIndices[charIndex]
        for (charIndex in 0..text.length) {
            val index = byteIndices[charIndex]
            if (index < 0) continue
            val at = "$charIndex of $message"
            assertEquals(string.isGraphemeBoundary(index), text.isGraphemeBoundary(charIndex), "isGraphemeBoundary($at)")
            assertEquals(
                string.nextGraphemeBoundary(index),
                byteIndexOf(text.nextGraphemeBoundary(charIndex)),
                "nextGraphemeBoundary($at)",
            )
            assertEquals(
                string.previousGraphemeBoundary(index),
                byteIndexOf(text.previousGraphemeBoundary(charIndex)),
                "previousGraphemeBoundary($at)",
            )
        }
    }

    // The queries go through a RangeGuard, so a read outside the range fails even when it does not change a result.
    private fun assertRangeAsCopy(whole: String, start: Int, end: Int, message: String) {
        val part = whole.substring(start, end)
        val guarded = RangeGuard(whole, start, end)
        fun shiftBack(index: Int): Int = if (index < 0) index else index - start
        for (index in 0..part.length) {
            assertEquals(
                part.isGraphemeBoundary(index),
                guarded.isGraphemeBoundary(start + index, start, end),
                "isGraphemeBoundary($index) of $message",
            )
            assertEquals(
                part.nextGraphemeBoundary(index),
                shiftBack(guarded.nextGraphemeBoundary(start + index, start, end)),
                "nextGraphemeBoundary($index) of $message",
            )
            assertEquals(
                part.previousGraphemeBoundary(index),
                shiftBack(guarded.previousGraphemeBoundary(start + index, start, end)),
                "previousGraphemeBoundary($index) of $message",
            )
        }
    }

    // At most one lone surrogate between 2 code points, and no code point starts with a low surrogate or ends with a
    // high one, so no pair forms.
    private fun withLoneSurrogates(codePoints: IntArray, random: Random): String {
        val text = StringBuilder()
        for (codePoint in codePoints) {
            if (random.nextInt(3) == 0) text.appendLoneSurrogate(random)
            text.append(codePointsToString(intArrayOf(codePoint)))
        }
        if (random.nextInt(3) == 0) text.appendLoneSurrogate(random)
        return text.toString()
    }

    private fun StringBuilder.appendLoneSurrogate(random: Random) {
        val low = random.nextBoolean()
        append(Char((if (low) 0xDC00 else 0xD800) + random.nextInt(0x400)))
    }

    private class CharsView(private val chars: CharArray) : CharSequence {
        override val length: Int
            get() = chars.size

        override fun get(index: Int): Char = chars[index]

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            CharsView(chars.copyOfRange(startIndex, endIndex))
    }

    // A text whose chars outside start..<end cannot be read, and which cannot be copied, as the KDoc promises a query.
    private class RangeGuard(private val text: String, private val start: Int, private val end: Int) : CharSequence {
        override val length: Int
            get() = text.length

        override fun get(index: Int): Char {
            check(index in start..<end) { "read at $index, outside $start..<$end" }
            return text[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            error("subSequence($startIndex, $endIndex) copies the text")

        override fun toString(): String = error("toString() copies the text")
    }

    private companion object {
        const val EDITOR_EXAMPLE = "1F468 200D 1F469 200D 1F467 200D 1F466 1F44B 1F3FD 1F1EA 1F1F8 1F1EB 1F1F7 " +
            "1F3F4 E0067 E0062 E0073 E0063 E0074 E007F 1F469 1F3FD 200D 1F4BB 1F9D1 200D 1F91D 200D 1F9D1 " +
            "2764 FE0F 200D 1F525 1F3F3 FE0F 200D 1F308 31 FE0F 20E3 1FAF1 1F3FB 200D 1FAF2 1F3FF " +
            "1F43B 200D 2744 FE0F 1F9D1 1F3FB 200D 2764 FE0F 200D 1F48B 200D 1F9D1 1F3FC " +
            "1F3C3 1F3FD 200D 2640 FE0F 200D 27A1 FE0F 1F441 FE0F 200D 1F5E8 FE0F"
    }
}
