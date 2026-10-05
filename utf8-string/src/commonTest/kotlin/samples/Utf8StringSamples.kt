/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

// The API reference shows these samples, so they hold the characters themselves, except where 2 strings look the same.

package samples

import utf8string.toUtf8String
import utf8string.u8
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class Utf8StringSamples {
    @Test
    fun countAndIterateGraphemes() {
        val text = "Hi 👋🏽 🇪🇸".u8
        assertEquals(6, text.length)
        assertEquals(12, text.toString().length)
        val brackets = StringBuilder()
        for (grapheme in text) brackets.append("[$grapheme]")
        assertEquals("[H][i][ ][👋🏽][ ][🇪🇸]", brackets.toString())
    }

    @Test
    fun lengthIsNotAByteIndex() {
        val text = "Hi 👋🏽".u8
        assertEquals(4, text.length)
        assertEquals(11, text.byteCount) // the end for an index
        assertEquals(3, text.iterator(text.length).index) // compiles, but byte 4 is inside 👋🏽, not at the end
        assertFalse(text.iterator(text.byteCount).hasNext())
    }

    @Test
    fun byteCountCountsUtf8Bytes() {
        val text = "Привет, 😀".u8
        assertEquals(18, text.byteCount) // 2 bytes for each Cyrillic letter, 4 for the emoji
        assertEquals(9, text.length)
        assertEquals(10, text.toString().length)
    }

    @Test
    fun toByteArrayCopiesTheBytes() {
        val text = "a€".u8
        val bytes = text.toByteArray()
        assertContentEquals(byteArrayOf(0x61, 0xE2.toByte(), 0x82.toByte(), 0xAC.toByte()), bytes)
        bytes[0] = 'b'.code.toByte()
        assertEquals("a€", text.toString())
    }

    @Test
    fun copyIntoWritesIntoOneBuffer() {
        val greeting = "Hello, ".u8
        val name = "мир".u8
        val buffer = ByteArray(greeting.byteCount + name.byteCount)
        greeting.copyInto(buffer)
        name.copyInto(buffer, destinationOffset = greeting.byteCount)
        assertEquals("Hello, мир", buffer.decodeToString())
    }

    @Test
    fun lengthCountsGraphemes() {
        val text = "नमस्ते".u8
        assertEquals(3, text.length) // न, म, स्ते
        assertEquals(6, text.toString().length)
        assertEquals(3, "a\r\nb".u8.length) // CR LF is one grapheme
    }

    @Test
    fun forLoopUsesTheIterator() {
        val text = "Hi 👋🏽".u8
        val graphemes = mutableListOf<String>()
        for (grapheme in text) graphemes += grapheme.toString()
        assertEquals(listOf("H", "i", " ", "👋🏽"), graphemes)
        assertEquals("H", text.iterator().next().toString())
    }

    @Test
    fun iteratorStartsAtAByteIndex() {
        val text = "Hi 👋🏽 🇪🇸".u8 // 👋🏽 takes bytes 3 to 10
        val iterator = text.iterator(7)
        assertEquals(3, iterator.index)
        assertEquals("👋🏽", iterator.next().toString())
        assertEquals(11, iterator.index)
    }

    @Test
    fun fitGraphemesIntoAByteLimit() {
        val text = "Hi 👋🏽 🇪🇸".u8 // 👋🏽 takes bytes 3 to 10
        val limit = 10
        val end = text.iterator(minOf(limit, text.byteCount)).index
        assertEquals(3, end) // 👋🏽 does not fit
        val bytes = ByteArray(end)
        text.copyInto(bytes, endIndex = end)
        assertEquals("Hi ", bytes.decodeToString())
    }

    @Test
    fun graphemesIsASequence() {
        val text = "Hi 👋🏽 🇪🇸".u8
        assertEquals("Hi 👋🏽", text.graphemes.take(4).joinToString(""))
        assertEquals("🇪🇸", text.graphemes.last().toString())
    }

    @Test
    fun isGraphemeBoundaryChecksAByteIndex() {
        val text = "Hi 👋🏽 🇪🇸".u8 // 👋 takes bytes 3 to 6, and 🏽 bytes 7 to 10
        assertTrue(text.isGraphemeBoundary(3))
        assertFalse(text.isGraphemeBoundary(7)) // 🏽 starts a code point, but 👋🏽 is one grapheme
        assertFalse(text.isGraphemeBoundary(5)) // inside the code point 👋
        assertTrue(text.isGraphemeBoundary(text.byteCount))
    }

    @Test
    fun nextGraphemeBoundaryFindsTheEnd() {
        val text = "Hi 👋🏽 🇪🇸".u8 // 👋🏽 takes bytes 3 to 10
        assertEquals(11, text.nextGraphemeBoundary(3))
        assertEquals(11, text.nextGraphemeBoundary(7))
        assertEquals(20, text.nextGraphemeBoundary(12)) // the end of 🇪🇸
        assertEquals(-1, text.nextGraphemeBoundary(20))
    }

    @Test
    fun previousGraphemeBoundaryFindsTheStart() {
        val text = "Hi 👋🏽 🇪🇸".u8 // 👋🏽 takes bytes 3 to 10
        assertEquals(3, text.previousGraphemeBoundary(11))
        assertEquals(3, text.previousGraphemeBoundary(7))
        assertEquals(2, text.previousGraphemeBoundary(3))
        assertEquals(-1, text.previousGraphemeBoundary(0))
    }

    @Test
    fun roundAnIndexToAGraphemeBoundary() {
        val text = "Hi 👋🏽 🇪🇸".u8 // 👋🏽 takes bytes 3 to 10
        val index = 7 // from a hit test, for example
        val down = if (text.isGraphemeBoundary(index)) index else text.previousGraphemeBoundary(index)
        val up = if (text.isGraphemeBoundary(index)) index else text.nextGraphemeBoundary(index)
        assertEquals(3, down)
        assertEquals(11, up)
    }

    @Test
    fun takeKeepsTheFirstGraphemes() {
        val text = "Hi 👋🏽 🇪🇸".u8
        assertEquals("Hi 👋🏽".u8, text.take(4))
        assertSame(text, text.take(10))
        assertEquals("Hi 👋", "Hi 👋🏽 🇪🇸".take(5)) // String.take counts UTF-16 chars and splits 👋🏽
    }

    @Test
    fun dropRemovesTheFirstGraphemes() {
        val text = "Hi 👋🏽 🇪🇸".u8
        assertEquals(" 🇪🇸".u8, text.drop(4))
        assertEquals("".u8, text.drop(10))
    }

    @Test
    fun takeLastKeepsTheLastGraphemes() {
        val flags = "🇪🇸🇫🇷🇯🇵".u8
        assertEquals("🇫🇷🇯🇵".u8, flags.takeLast(2))
        assertSame(flags, flags.takeLast(3))
    }

    @Test
    fun dropLastRemovesTheLastGraphemes() {
        val text = "Hi 👋🏽 🇪🇸".u8
        assertEquals("Hi 👋🏽".u8, text.dropLast(2))
        assertEquals("".u8, text.dropLast(10))
    }

    @Test
    fun equalsComparesTheBytes() {
        assertEquals("café".u8, "café".toUtf8String())
        assertNotEquals("café".u8, "cafe\u0301".u8) // é and e with a combining acute are different bytes
        assertNotEquals<Any>("café".u8, "café") // a Utf8String never equals a String
    }

    @Test
    fun hashCodeAgreesWithEquals() {
        val words = listOf("мир", "peace", "мир").map { it.toUtf8String() }
        val counts = words.groupingBy { it }.eachCount()
        assertEquals(2, counts["мир".u8])
        assertEquals(1, counts["peace".u8])
    }

    @Test
    fun toStringDecodesTheBytes() {
        val name = "мир".u8
        assertEquals("мир", name.toString())
        assertEquals("Hello, мир!", "Hello, $name!")
    }
}
