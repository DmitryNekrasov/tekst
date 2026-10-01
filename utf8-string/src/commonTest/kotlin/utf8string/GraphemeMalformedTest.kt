/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Only the internal constructor can make a Utf8String of malformed UTF-8. Its graphemes are unspecified, but iteration
// must end, stay within the bytes and put every byte into exactly one grapheme.
class GraphemeMalformedTest {
    @Test
    fun malformedSequences() {
        for (hex in listOf(
            "E1", "E2 82", "80", "BF 80", "C0 80", "C1", "ED A0 80", "ED BF BF", "F4 90 80 80", "F5", "FF", "F0 9F 98",
            "F0 3F 3F 3F", "F0 7F 41 41", "F3 E0 80 80", "F3 60 80 80", "61 E1 62", "E1 CC 81", "CC", "61 CC", "0D E2",
            "F0 9F 98 80 80 80", "E0 80 80", "F8 88 80 80 80",
            // Overlong forms of LF after CR, which the automaton joins to it.
            "0D C0 8A", "0D E0 80 8A", "0D F0 80 80 8A",
        )) {
            assertCovered(hex.split(' ').map { it.toInt(16).toByte() }.toByteArray(), hex)
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
            // Each grapheme holds the next bytes of the string.
            val slice = bytes.copyOfRange(byteCount, byteCount + grapheme.byteCount)
            assertUtf8Equals(Utf8String(slice, slice.count { it.toInt() and 0xC0 != 0x80 }), grapheme.toUtf8String(), message)
            byteCount += grapheme.byteCount
            graphemeCount++
        }
        assertEquals(bytes.size, byteCount, message)
        assertEquals(graphemeCount, string.length, message)
    }
}
