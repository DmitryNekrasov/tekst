/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The graphemes of malformed UTF-8 are unspecified. The tests check only that every byte is in exactly one grapheme,
// and that the functions with an index answer within the text.
class GraphemeMalformedTest {
    @Test
    fun malformedSequences() {
        for (hex in listOf(
            "E1", "E2 82", "80", "BF 80", "C0 80", "C1", "ED A0 80", "ED BF BF", "F4 90 80 80", "F5", "FF", "F0 9F 98",
            "F0 3F 3F 3F", "F0 7F 41 41", "F3 E0 80 80", "F3 60 80 80", "61 E1 62", "E1 CC 81", "CC", "61 CC", "0D E2",
            "F0 9F 98 80 80 80", "E0 80 80", "F8 88 80 80 80",
            // Overlong forms of LF after CR, which the automaton joins to it.
            "0D C0 8A", "0D E0 80 8A", "0D F0 80 80 8A",
            // Bytes that the walk back splits differently from the walk forward: a continuation byte after CR, and
            // 2 regional indicators 3 bytes apart.
            "0D 80", "41 0D 80", "C3 80 80", "F0 9F 87 F0 9F 87 A6",
        )) {
            assertCovered(hex.hexToBytes(), hex)
            assertRandomAccessInRange(hex.hexToBytes(), Random(38), hex)
        }
    }

    @Test
    fun randomBytes() {
        val random = Random(30)
        repeat(RANDOM_TEXT_COUNT) {
            val bytes = ByteArray(random.nextInt(1, 17)) {
                // Mostly lead and continuation bytes, which make malformed sequences more often than any byte would.
                (if (random.nextBoolean()) random.nextInt(0x80, 0x100) else random.nextInt(0x100)).toByte()
            }
            assertCovered(bytes, bytes.toHex())
            assertRandomAccessInRange(bytes, random, bytes.toHex())
        }
    }

    private fun assertCovered(bytes: ByteArray, message: String) {
        // A code point count that differs from the byte count keeps length off the ASCII path.
        val string = Utf8String(bytes, -1)
        var byteCount = 0
        var graphemeCount = 0
        for (grapheme in string) {
            assertTrue(grapheme.byteCount > 0, message)
            grapheme.toString()
            assertHoldsBytes(bytes, byteCount, grapheme, message)
            byteCount += grapheme.byteCount
            graphemeCount++
        }
        assertEquals(bytes.size, byteCount, message)
        assertEquals(graphemeCount, string.length, message)
    }

    private fun assertRandomAccessInRange(bytes: ByteArray, random: Random, message: String) {
        val string = Utf8String(bytes, -1)
        val size = bytes.size
        for (index in 0..size) {
            string.isGraphemeBoundary(index)
            val next = string.nextGraphemeBoundary(index)
            assertTrue(if (index == size) next == -1 else next in index + 1..size, "next($index) of $message")
            val previous = string.previousGraphemeBoundary(index)
            assertTrue(if (index == 0) previous == -1 else previous in 0..<index, "previous($index) of $message")
            assertTrue(string.iterator(index).index in 0..index, "iterator($index) of $message")
        }
        var covered = 0
        val backward = string.iterator(size)
        while (backward.hasPrevious()) {
            val grapheme = backward.previous()
            assertTrue(grapheme.byteCount > 0, message)
            grapheme.toString()
            assertHoldsBytes(bytes, backward.index, grapheme, message)
            covered += grapheme.byteCount
            assertEquals(size - covered, backward.index, message)
        }
        val graphemes = string.iterator(random.nextInt(size + 1))
        repeat(20) {
            val index = graphemes.index
            if (random.nextBoolean()) {
                if (graphemes.hasNext()) assertTrue(graphemes.skipNext() > index, message)
            } else {
                if (graphemes.hasPrevious()) assertTrue(graphemes.skipPrevious() < index, message)
            }
        }
        val length = string.length
        for (n in 0..3) {
            assertEquals(size, string.take(n).byteCount + string.drop(n).byteCount, "take($n) of $message")
            assertEquals(size, string.takeLast(n).byteCount + string.dropLast(n).byteCount, "takeLast($n) of $message")
        }
        // The walk back splits malformed bytes in its own way, and length counts the graphemes of the for loop.
        assertEquals(length, string.length, "length after takeLast of $message")
    }

    private fun assertHoldsBytes(bytes: ByteArray, start: Int, grapheme: Grapheme, message: String) {
        val slice = bytes.copyOfRange(start, start + grapheme.byteCount)
        assertUtf8Equals(Utf8String(slice, slice.count { it.toInt() and 0xC0 != 0x80 }), grapheme.toUtf8String(), message)
    }
}
