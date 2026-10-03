/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

// The API reference shows these samples, so they hold the characters themselves, except where 2 strings look the same.

package samples

import utf8string.u8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GraphemeSamples {
    @Test
    fun graphemeIsOneCharacter() {
        val family = "👨‍👩‍👧".u8
        val grapheme = family.graphemes.single()
        assertEquals(8, grapheme.toString().length) // 3 people and 2 zero width joiners, 8 UTF-16 chars
        assertEquals(family, grapheme.toUtf8String())
    }

    @Test
    fun toUtf8StringCopiesTheGrapheme() {
        val text = "🇪🇸 Hola".u8
        val flag = text.iterator().next().toUtf8String()
        assertEquals("🇪🇸".u8, flag)
        assertEquals(8, flag.byteCount) // the 8 bytes of the flag, not the 13 of the text
    }

    @Test
    fun equalsComparesTheBytes() {
        val a = "a\u0301".u8.iterator().next()
        val b = "xa\u0301".u8.graphemes.last()
        assertEquals(a, b)
        assertNotEquals(a, "á".u8.iterator().next()) // the precomposed á has different bytes
        assertNotEquals<Any>(a, "a\u0301".u8) // a Grapheme never equals a Utf8String
    }

    @Test
    fun hashCodeAgreesWithEquals() {
        val graphemes = "👋🏽 👋🏿 👋🏽".u8.graphemes.toSet()
        assertEquals(3, graphemes.size) // 👋🏽, the space and 👋🏿
    }

    @Test
    fun toStringDecodesTheGrapheme() {
        val brackets = "Hi 👋🏽".u8.graphemes.joinToString("") { "[$it]" }
        assertEquals("[H][i][ ][👋🏽]", brackets)
    }

    @Test
    fun hasNextAndNextWalkTheGraphemes() {
        val iterator = "👋🏽!".u8.iterator()
        assertTrue(iterator.hasNext())
        assertEquals("👋🏽", iterator.next().toString())
        assertEquals("!", iterator.next().toString())
        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }
    }
}
