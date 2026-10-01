/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class Utf8StringTest {
    @Test
    fun encodesEachSequenceLength() {
        assertEncodes("", "", 0)
        assertEncodes("\u0000", "00", 1)
        assertEncodes("\u007F", "7F", 1)
        assertEncodes("\u0080", "C2 80", 1)
        assertEncodes("\u07FF", "DF BF", 1)
        assertEncodes("\u0800", "E0 A0 80", 1)
        assertEncodes("\uFFFF", "EF BF BF", 1)
        assertEncodes("\uD800\uDC00", "F0 90 80 80", 1)
        assertEncodes("\uDBFF\uDFFF", "F4 8F BF BF", 1)
        assertEncodes("a\u20AC\uD83D\uDE00", "61 E2 82 AC F0 9F 98 80", 3)
    }

    @Test
    fun replacesUnpairedSurrogates() {
        assertEncodes("\uD800", "EF BF BD", 1)
        assertEncodes("\uDC00", "EF BF BD", 1)
        assertEncodes("a\uD800", "61 EF BF BD", 2)
        assertEncodes("\uDC00\uD800", "EF BF BD EF BF BD", 2)
        assertEncodes("\uD800\uD800\uDC00", "EF BF BD F0 90 80 80", 2)
        assertEncodes("\uD800\uDC00\uDC00", "F0 90 80 80 EF BF BD", 2)
    }

    @Test
    fun encodesLongMixedString() {
        val source = "ascii ".repeat(100) + "\u043F\u0440\u0438\u0432\u0435\u0442 " +
            "\u65E5".repeat(50) + "\uD83D\uDE00".repeat(20)
        assertMatchesStdlib(source)
    }

    @Test
    fun matchesStdlibOnRandomWellFormedStrings() {
        val random = Random(42)
        repeat(10_000) {
            val source = buildString {
                if (random.nextBoolean()) repeat(random.nextInt(20)) { append(Char(random.nextInt(0x80))) }
                repeat(random.nextInt(12)) {
                    when (random.nextInt(4)) {
                        0 -> append(Char(random.nextInt(0x80)))
                        1 -> append(Char(random.nextInt(0x80, 0x800)))
                        2 -> append(Char(random.nextInt(0x800, 0xF800).let { if (it < 0xD800) it else it + 0x800 }))
                        else -> appendSurrogatePair(random.nextInt(0x10000, 0x110000))
                    }
                }
            }
            assertMatchesStdlib(source)
        }
    }

    // Every other test compares through equals, so it must tell apart the bytes and the code point count.
    @Test
    fun equalsComparesBytesAndCodePointCount() {
        val ab = Utf8String(byteArrayOf(0x61, 0x62), 2)
        assertEquals(ab, Utf8String(byteArrayOf(0x61, 0x62), 2))
        assertEquals(ab.hashCode(), Utf8String(byteArrayOf(0x61, 0x62), 2).hashCode())
        assertNotEquals(ab, Utf8String(byteArrayOf(0x61, 0x63), 2))
        assertNotEquals(ab, Utf8String(byteArrayOf(0x61), 1))
        assertNotEquals(ab, Utf8String(byteArrayOf(0x61, 0x62), 1))
        assertNotEquals<Any>(ab, "ab")
    }

    @Test
    fun zeroContentHashIsStoredAsNonZero() {
        // The content hash of these bytes is 0, which the cache reserves for a hash that is not computed yet.
        assertEquals(0, byteArrayOf(-31).contentHashCode())
        val zero = Utf8String(byteArrayOf(-31), 1)
        assertNotEquals(0, zero.hashCode())
        assertEquals(zero.hashCode(), zero.hashCode())
        assertEquals(zero.hashCode(), Utf8String(byteArrayOf(-31), 1).hashCode())
    }

    @Test
    fun toStringDecodesTheBytes() {
        val sources = listOf("", "ascii", "\u041F\u0440\u0438\u0432\u0435\u0442", "a\u20AC\uD83D\uDE00", "e\u0301\r\n")
        for (source in sources) assertEquals(source, Utf8String.fromString(source).toString())
        // An unpaired surrogate was encoded as U+FFFD.
        assertEquals("a\uFFFDb", Utf8String.fromString("a\uD800b").toString())
    }

    @Test
    fun toByteArrayReturnsACopy() {
        val string = Utf8String.fromString("ab")
        val bytes = string.toByteArray()
        assertNotSame(bytes, string.toByteArray())
        bytes[0] = 0x7A
        assertEquals(Utf8String.fromString("ab"), string)
    }

    @Test
    fun copyIntoWritesTheBytesIntoTheDestination() {
        val string = Utf8String.fromString("a\u20AC")
        val destination = ByteArray(6)
        assertSame(destination, string.copyInto(destination))
        assertEquals("61 E2 82 AC 00 00", destination.toHex())
        // A range counts bytes, so it can split a code point.
        string.copyInto(destination, destinationOffset = 4, startIndex = 1, endIndex = 3)
        assertEquals("61 E2 82 AC E2 82", destination.toHex())
        assertFailsWith<IndexOutOfBoundsException> { string.copyInto(ByteArray(3)) }
    }

    private fun assertEncodes(source: String, expectedHex: String, expectedCodePoints: Int) {
        val expected = expectedHex.split(' ').filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()
        val actual = Utf8String.fromString(source)
        assertUtf8Equals(Utf8String(expected, expectedCodePoints), actual, expectedHex)
        assertContentEquals(expected, actual.toByteArray(), expectedHex)
    }

    // Well-formed strings only: the stdlib replaces unpaired surrogates differently on the JVM.
    private fun assertMatchesStdlib(source: String) {
        val bytes = source.encodeToByteArray()
        // Each code point has exactly one byte that is not a continuation byte (10xxxxxx).
        val expected = Utf8String(bytes, bytes.count { it.toInt() and 0xC0 != 0x80 })
        val actual = Utf8String.fromString(source)
        assertUtf8Equals(expected, actual, bytes.toHex())
        assertEquals(source, actual.toString(), bytes.toHex())
    }

    private fun StringBuilder.appendSurrogatePair(codePoint: Int) {
        append(Char(0xD800 + ((codePoint - 0x10000) shr 10)))
        append(Char(0xDC00 + ((codePoint - 0x10000) and 0x3FF)))
    }
}
