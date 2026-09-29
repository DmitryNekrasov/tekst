/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class U8Test {
    @Test
    fun u8EncodesLikeFromString() {
        for (source in listOf("", "ascii", "\u041F\u0440\u0438\u0432\u0435\u0442", "a\u20AC\uD83D\uDE00", "\uD800x")) {
            val expected = Utf8String.fromString(source)
            val actual = source.u8
            assertContentEquals(expected.buffer, actual.buffer)
            assertEquals(expected.codePointCount, actual.codePointCount)
        }
    }

    @Test
    fun u8LiteralKeepsTheArray() {
        val bytes = byteArrayOf(0x61, 0x62)
        val literal = u8Literal(bytes, 2)
        assertSame(bytes, literal.buffer)
        assertEquals(2, literal.codePointCount)
    }

    @Test
    fun u8LiteralLatin1MapsEveryCharToOneByte() {
        val latin1 = CharArray(256) { Char(it) }.concatToString()
        val literal = u8LiteralLatin1(latin1, 7)
        assertContentEquals(ByteArray(256) { it.toByte() }, literal.buffer)
        assertEquals(7, literal.codePointCount)
    }

    @Test
    fun u8LiteralLatin1CreatesAFreshArrayEachTime() {
        val first = u8LiteralLatin1("abc", 3)
        val second = u8LiteralLatin1("abc", 3)
        assertNotSame(first.buffer, second.buffer)
        first.buffer[0] = 0
        assertEquals(0x61, second.buffer[0].toInt())
    }
}
