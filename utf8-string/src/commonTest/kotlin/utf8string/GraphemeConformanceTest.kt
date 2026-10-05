/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphemeConformanceTest {
    @Test
    fun tablesAndTestsHaveTheSameUnicodeVersion() {
        assertEquals(GRAPHEME_BREAK_TEST_VERSION, UNICODE_VERSION)
    }

    @Test
    fun graphemeBreakTest() {
        val tests = parseSegmentationTests(GRAPHEME_BREAK_TEST)
        assertEquals(853, tests.size)
        tests.forEach(::assertSegments)
    }

    @Test
    fun cldrIndicWords() {
        val tests = parseSegmentationTests(CLDR_GRAPHEME_TEST)
        assertEquals(221, tests.size)
        tests.forEach(::assertSegments)
    }

    @Test
    fun eachRgiEmojiSequenceIsOneGrapheme() {
        val sequences = EMOJI_TEST_RGI.flatMap { it.lines() }.filter { it.isNotEmpty() }
        assertEquals(3972, sequences.size)
        for (sequence in sequences) {
            val string = codePointsToString(sequence.codePointsFromHex()).u8
            assertEquals(listOf(0, string.byteCount), string.iteratedBoundaries(), sequence)
            assertEquals(1, string.length, sequence)
        }
    }

    @Test
    fun modelPassesTheTests() {
        for (test in parseSegmentationTests(GRAPHEME_BREAK_TEST) + parseSegmentationTests(CLDR_GRAPHEME_TEST)) {
            assertEquals(test.boundaries.toList(), GraphemeModel.boundaries(test.codePoints).toList(), test.line)
        }
    }

    private fun assertSegments(test: SegmentationTest) {
        val source = codePointsToString(test.codePoints)
        val string = source.u8
        val expected = byteBoundaries(test.codePoints, test.boundaries)
        assertEquals(expected, string.iteratedBoundaries(), test.line)
        assertEquals(expected.size - 1, string.length, test.line)

        var codePoint = 0
        for (grapheme in string) {
            val end = (codePoint + 1..test.codePoints.size).first { test.boundaries[it] }
            val part = test.codePoints.copyOfRange(codePoint, end)
            assertEquals(codePointsToString(part).u8, grapheme.toUtf8String(), test.line)
            assertEquals(codePointsToString(part), grapheme.toString(), test.line)
            codePoint = end
        }
        assertTrue(codePoint == test.codePoints.size, test.line)
    }
}
