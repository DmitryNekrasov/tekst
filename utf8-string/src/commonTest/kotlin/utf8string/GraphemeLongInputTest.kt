/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.test.Test
import kotlin.test.assertEquals

// These would take hours if a rule looked back over the cluster at each code point.
class GraphemeLongInputTest {
    @Test
    fun combiningMarks() {
        val string = ("a" + "\u0301".repeat(100_000)).u8
        assertEquals(listOf(0, 200_001), string.iteratedBoundaries())
        assertEquals(1, string.length)
    }

    @Test
    fun regionalIndicators() {
        val string = "\uD83C\uDDE6".repeat(100_001).u8
        assertEquals(50_001, string.length)
        assertEquals((0..50_000).map { it * 8 } + 400_004, string.iteratedBoundaries())
    }

    @Test
    fun emojiWithManyExtenders() {
        val string = ("\uD83D\uDED1" + "\u0308".repeat(100_000) + "\u200D\uD83D\uDED1").u8
        assertEquals(1, string.length)
        assertEquals(listOf(0, string.byteCount), string.iteratedBoundaries())
    }

    @Test
    fun conjunctWithManyExtenders() {
        val string = ("\u0915\u094D" + "\u0308".repeat(100_000) + "\u0924").u8
        assertEquals(1, string.length)
        assertEquals(listOf(0, string.byteCount), string.iteratedBoundaries())
    }

    @Test
    fun longAsciiWithLineBreaks() {
        val string = "ab\r\n".repeat(100_000).u8
        assertEquals(300_000, string.length)
        assertEquals(300_001, string.iteratedBoundaries().size)
    }
}
