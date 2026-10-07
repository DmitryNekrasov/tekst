/*
 * Copyright 2026 Dmitry Nekrasov and tekst library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tekst

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// These would take hours if a rule looked back over the cluster at each code point, or if a walk back looked back over
// a run of regional indicators at each flag.
class GraphemeLongInputTest {
    @Test
    fun combiningMarks() {
        val string = ("a" + "\u0301".repeat(100_000)).u8
        assertEquals(listOf(0, 200_001), string.iteratedBoundaries())
        assertEquals(1, string.length)
        assertEquals(listOf(0, 200_001), string.boundariesBackward())
        assertEquals(0, string.previousGraphemeBoundary(string.byteCount))
        assertEquals(string.byteCount, string.nextGraphemeBoundary(1))
    }

    @Test
    fun regionalIndicators() {
        val string = "\uD83C\uDDE6".repeat(100_001).u8
        assertEquals(50_001, string.length)
        val boundaries = (0..50_000).map { it * 8 } + 400_004
        assertEquals(boundaries, string.iteratedBoundaries())
        assertEquals(boundaries, string.boundariesBackward())
        assertEquals(400_000, string.previousGraphemeBoundary(string.byteCount))
        assertEquals(8, string.nextGraphemeBoundary(0))
        assertEquals(399_992, string.iterator(399_999).index)
        assertEquals(12, string.takeLast(2).byteCount)
        assertEquals(400_000, string.dropLast(1).byteCount)
    }

    @Test
    fun walksThatTurnInRegionalIndicators() {
        val string = "\uD83C\uDDE6".repeat(100_001).u8
        // Each previous goes back over the bytes that the one before it has seen.
        val forward = string.iterator()
        while (forward.hasNext()) {
            val end = forward.skipNext()
            if (!forward.hasNext()) break
            forward.skipNext()
            assertEquals(end, forward.skipPrevious())
        }
        assertEquals(string.byteCount, forward.index)
        // Each previous goes back over the end of the run.
        val withLetter = ("\uD83C\uDDE6".repeat(100_001) + "x").u8
        val bouncing = withLetter.iterator(withLetter.byteCount)
        repeat(100_000) {
            assertEquals(400_004, bouncing.skipPrevious())
            assertEquals(400_000, bouncing.skipPrevious())
            assertEquals(400_004, bouncing.skipNext())
            assertEquals(400_005, bouncing.skipNext())
        }
    }

    @Test
    fun emojiWithManyExtenders() {
        val string = ("\uD83D\uDED1" + "\u0308".repeat(100_000) + "\u200D\uD83D\uDED1").u8
        assertEquals(1, string.length)
        assertEquals(listOf(0, string.byteCount), string.iteratedBoundaries())
        assertEquals(listOf(0, string.byteCount), string.boundariesBackward())
        assertFalse(string.isGraphemeBoundary(string.byteCount - 4))
    }

    @Test
    fun conjunctWithManyExtenders() {
        val string = ("\u0915\u094D" + "\u0308".repeat(100_000) + "\u0924").u8
        assertEquals(1, string.length)
        assertEquals(listOf(0, string.byteCount), string.iteratedBoundaries())
        assertEquals(listOf(0, string.byteCount), string.boundariesBackward())
        assertFalse(string.isGraphemeBoundary(string.byteCount - 3))
    }

    @Test
    fun conjunctOfManyConsonants() {
        val string = ("\u0915" + "\u094D\u0915".repeat(50_000)).u8
        assertEquals(1, string.length)
        assertEquals(listOf(0, string.byteCount), string.boundariesBackward())
    }

    @Test
    fun consonantAfterManyExtendersWithoutLinker() {
        val string = ("a" + "\u200C".repeat(100_000) + "\u200D\u0915").u8
        assertEquals(2, string.length)
        assertTrue(string.isGraphemeBoundary(string.byteCount - 3))
        assertEquals(listOf(0, string.byteCount - 3, string.byteCount), string.boundariesBackward())
    }

    @Test
    fun longAsciiWithLineBreaks() {
        val string = "ab\r\n".repeat(100_000).u8
        assertEquals(300_000, string.length)
        assertEquals(300_001, string.iteratedBoundaries().size)
        assertEquals(string.iteratedBoundaries(), string.boundariesBackward())
    }

    private fun Utf8String.boundariesBackward(): List<Int> {
        val boundaries = arrayListOf(byteCount)
        val graphemes = iterator(byteCount)
        while (graphemes.hasPrevious()) boundaries += graphemes.skipPrevious()
        return boundaries.asReversed()
    }
}
