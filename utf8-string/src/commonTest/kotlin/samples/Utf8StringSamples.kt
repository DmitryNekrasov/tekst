/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

// The API reference shows these samples, so only the invisible characters are escaped.

package samples

import utf8string.toUtf8String
import utf8string.u8
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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
    fun graphemesIsASequence() {
        val text = "Hi 👋🏽 🇪🇸".u8
        assertEquals("Hi 👋🏽", text.graphemes.take(4).joinToString(""))
        assertEquals("🇪🇸", text.graphemes.last().toString())
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
