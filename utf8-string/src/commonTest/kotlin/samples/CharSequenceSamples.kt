/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

// The API reference shows these samples, so they hold the characters themselves, except where 2 strings look the same.

package samples

import utf8string.isGraphemeBoundary
import utf8string.nextGraphemeBoundary
import utf8string.previousGraphemeBoundary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CharSequenceSamples {
    @Test
    fun isGraphemeBoundaryChecksACharIndex() {
        val text = "Hi 👋🏽 🇪🇸" // 👋 takes chars 3 and 4, and 🏽 chars 5 and 6
        assertTrue(text.isGraphemeBoundary(3))
        assertFalse(text.isGraphemeBoundary(5)) // 🏽 starts a code point, but 👋🏽 is one grapheme
        assertFalse(text.isGraphemeBoundary(4)) // between the 2 chars of 👋
        assertTrue(text.isGraphemeBoundary(text.length))
    }

    @Test
    fun nextGraphemeBoundaryFindsTheEnd() {
        val text = "Hi 👋🏽 🇪🇸" // 👋🏽 takes chars 3 to 6
        assertEquals(7, text.nextGraphemeBoundary(3))
        assertEquals(7, text.nextGraphemeBoundary(5))
        assertEquals(12, text.nextGraphemeBoundary(8)) // the end of 🇪🇸
        assertEquals(-1, text.nextGraphemeBoundary(12))
    }

    @Test
    fun previousGraphemeBoundaryFindsTheStart() {
        val text = "Hi 👋🏽 🇪🇸" // 👋🏽 takes chars 3 to 6
        assertEquals(3, text.previousGraphemeBoundary(7))
        assertEquals(3, text.previousGraphemeBoundary(5)) // inside 👋🏽
        assertEquals(8, text.previousGraphemeBoundary(12)) // the start of 🇪🇸
        assertEquals(-1, text.previousGraphemeBoundary(0))
    }

    @Test
    fun roundAnIndexToAGraphemeBoundary() {
        val text = "Hi 👋🏽 🇪🇸" // 👋🏽 takes chars 3 to 6
        val index = 5 // from a hit test, for example
        val atBoundary = text.isGraphemeBoundary(index)
        val down = if (atBoundary) index else text.previousGraphemeBoundary(index)
        val up = if (atBoundary) index else text.nextGraphemeBoundary(index)
        assertEquals(3, down)
        assertEquals(7, up)
    }

    @Test
    fun queryOneLineOfAText() {
        val text = "first\n👨‍👩‍👧 and 🇪🇸\nlast"
        val lineStart = text.indexOf('\n') + 1
        val lineEnd = text.indexOf('\n', lineStart)
        val caret = lineStart + "👨‍👩‍👧".length
        // Backspace at the caret deletes the whole family, and Delete at the line start does too.
        assertEquals(lineStart, text.previousGraphemeBoundary(caret, lineStart, lineEnd))
        assertEquals(caret, text.nextGraphemeBoundary(lineStart, lineStart, lineEnd))
        assertTrue(text.isGraphemeBoundary(lineEnd, lineStart, lineEnd))
    }

    @Test
    fun walkTheGraphemes() {
        val text = "Hi 👋🏽 🇪🇸"
        val graphemes = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            // startIndex at the last boundary keeps the walk linear, also over a run of flags.
            val end = text.nextGraphemeBoundary(start, startIndex = start, endIndex = text.length)
            graphemes += text.substring(start, end)
            start = end
        }
        assertEquals(listOf("H", "i", " ", "👋🏽", " ", "🇪🇸"), graphemes)
    }
}
