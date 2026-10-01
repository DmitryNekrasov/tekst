/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GraphemeIteratorTest {
    @Test
    fun helloHasSixGraphemes() {
        val graphemes = ArrayList<String>()
        for (c in "Hello!".u8) graphemes += c.toString()
        assertEquals(listOf("H", "e", "l", "l", "o", "!"), graphemes)
        assertEquals(6, "Hello!".u8.length)
    }

    @Test
    fun emptyString() {
        val empty = "".u8
        assertFalse(empty.iterator().hasNext())
        assertFailsWith<NoSuchElementException> { empty.iterator().next() }
        assertEquals(0, empty.length)
    }

    @Test
    fun iteratorContract() {
        val iterator = "a\u0301b".u8.iterator()
        assertTrue(iterator.hasNext())
        assertTrue(iterator.hasNext())
        assertEquals("a\u0301", iterator.next().toString())
        assertEquals("b", iterator.next().toString())
        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }
        assertFailsWith<NoSuchElementException> { iterator.next() }
    }

    @Test
    fun iteratorsAreIndependent() {
        val string = "\u0915\u094D\u0937a\uD83C\uDDEA\uD83C\uDDF8".u8
        val first = string.iterator()
        val second = string.iterator()
        assertEquals("\u0915\u094D\u0937", first.next().toString())
        assertEquals("\u0915\u094D\u0937", second.next().toString())
        assertEquals("a", first.next().toString())
        assertEquals("\uD83C\uDDEA\uD83C\uDDF8", first.next().toString())
        assertEquals("a", second.next().toString())
    }

    @Test
    fun graphemesAreComparedByBytes() {
        val a = "xa\u0301".u8.iterator().also { it.next() }.next()
        val b = "a\u0301y".u8.iterator().next()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, "a".u8.iterator().next())
        assertNotEquals<Any>(a, "a\u0301".u8)
        // ASCII graphemes are shared, but that must not show in equality.
        assertEquals("zq".u8.iterator().next(), "z".u8.iterator().next())
        assertNotEquals("z".u8.iterator().next(), "y".u8.iterator().next())
        // The same size and first byte, different bytes after it.
        assertNotEquals("a\u0301".u8.iterator().next(), "a\u0300".u8.iterator().next())
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D"
        assertNotEquals((family + "\uD83D\uDC66").u8.iterator().next(), (family + "\uD83D\uDC67").u8.iterator().next())
    }

    @Test
    fun graphemeProperties() {
        val grapheme = "\uD83D\uDC4D\uD83C\uDFFD!".u8.iterator().next()
        assertEquals(8, grapheme.byteCount)
        assertEquals(2, grapheme.codePointCount)
        assertEquals("\uD83D\uDC4D\uD83C\uDFFD", grapheme.toString())
        assertUtf8Equals("\uD83D\uDC4D\uD83C\uDFFD".u8, grapheme.toUtf8String(), "thumbs up, medium skin tone")
        val crLf = "\r\n".u8.iterator().next()
        assertEquals(2, crLf.byteCount)
        assertEquals("\r\n", crLf.toString())
    }

    @Test
    fun graphemesCoverTheString() {
        val source = "Z\u0351\u036B\u0343\u036A\u0302 \u0645\u0631\u062D\u0628\u0627 \u05E9\u05B8\u05C1\u05DC\u05D5\u05B9\u05DD " +
            "\u1100\u1161\u11A8\r\n\u0E01\u0E33 \uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67 \u00E9e\u0301"
        val string = source.u8
        var byteCount = 0
        var codePointCount = 0
        val text = StringBuilder()
        var graphemeCount = 0
        for (grapheme in string) {
            assertTrue(grapheme.byteCount > 0)
            byteCount += grapheme.byteCount
            codePointCount += grapheme.codePointCount
            text.append(grapheme.toString())
            graphemeCount++
        }
        assertEquals(string.byteCount, byteCount)
        assertEquals(string.codePointCount, codePointCount)
        assertEquals(source, text.toString())
        assertEquals(graphemeCount, string.length)
        assertEquals(graphemeCount, string.length, "the cached length")
    }

    @Test
    fun lineBreaks() {
        assertGraphemes("\r\n", "\r\n")
        assertGraphemes("\r|\r\n", "\r\r\n")
        assertGraphemes("\n|\r", "\n\r")
        assertGraphemes("a|\r\n|b", "a\r\nb")
        assertGraphemes("a\u0308|\u0001", "a\u0308\u0001")
        assertGraphemes("\u0001|\u0308", "\u0001\u0308")
    }

    @Test
    fun conjuncts() {
        // Since Unicode 18.0 no consonant is needed before the linker (GB9c).
        assertGraphemes("a\u094D\u0924", "a\u094D\u0924")
        assertGraphemes("\u0915\u094D\u0937", "\u0915\u094D\u0937")
        assertGraphemes("\u0915\u094D\u200D\u0937", "\u0915\u094D\u200D\u0937")
        assertGraphemes("\u1CF5\u0995", "\u1CF5\u0995")
        assertGraphemes("\u1CF5\u200C|\u0995", "\u1CF5\u200C\u0995")
        assertGraphemes("\uD806\uDE3A\uD806\uDE0B", "\uD806\uDE3A\uD806\uDE0B")
        assertGraphemes("\u0915\u093F|\u0937", "\u0915\u093F\u0937")
    }

    @Test
    fun emojiSequences() {
        val family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"
        assertGraphemes(family, family)
        assertGraphemes("\uD83D\uDED1\u0308\u0308\u200D\uD83D\uDED1", "\uD83D\uDED1\u0308\u0308\u200D\uD83D\uDED1")
        assertGraphemes("\uD83D\uDED1\u200D\u200D|\uD83D\uDED1", "\uD83D\uDED1\u200D\u200D\uD83D\uDED1")
        assertGraphemes("a\u200D|\uD83D\uDED1", "a\u200D\uD83D\uDED1")
        // Where JDK 25 is wrong: SpacingMark is not Extend in GB11; Prepend joins; an unassigned code point is Other.
        assertGraphemes("\uD83D\uDED1\u0903\u200D|\uD83D\uDED1", "\uD83D\uDED1\u0903\u200D\uD83D\uDED1")
        assertGraphemes("\u0600\uD83D\uDED1\u200D\uD83D\uDED1", "\u0600\uD83D\uDED1\u200D\uD83D\uDED1")
        assertGraphemes("\u0379\u0308", "\u0379\u0308")
    }

    @Test
    fun regionalIndicators() {
        val ri = "\uD83C\uDDE6"
        assertGraphemes("$ri$ri", "$ri$ri")
        assertGraphemes("$ri$ri|$ri", "$ri$ri$ri")
        assertGraphemes("$ri$ri|$ri$ri", "$ri$ri$ri$ri")
        assertGraphemes("$ri\u0308|$ri", "$ri\u0308$ri")
        assertGraphemes("\u0600$ri$ri|$ri", "\u0600$ri$ri$ri")
    }

    @Test
    fun otherRules() {
        assertGraphemes("\u0600a", "\u0600a")
        assertGraphemes("\u0600|\n", "\u0600\n")
        assertGraphemes("\u1100\u1161\u11A8", "\u1100\u1161\u11A8")
        assertGraphemes("\uAC00\u11A8", "\uAC00\u11A8")
        assertGraphemes("\uAC01\u11A8", "\uAC01\u11A8")
        assertGraphemes("\u1100\uAC00", "\u1100\uAC00")
        assertGraphemes("\u11A8|\u1100", "\u11A8\u1100")
        assertGraphemes("\u0E01\u0E33", "\u0E01\u0E33")
        // An unpaired surrogate becomes U+FFFD, which a combining mark joins.
        assertGraphemes("\uFFFD\u0301|b", "\uD800\u0301b")
        val zalgo = "Z" + "\u0351\u036B\u0343\u036A\u0302\u036B\u033D\u034F\u0334".repeat(5)
        assertGraphemes(zalgo, zalgo)
    }

    // expected lists the graphemes of source separated by '|'.
    private fun assertGraphemes(expected: String, source: String) {
        val graphemes = ArrayList<String>()
        for (grapheme in source.u8) graphemes += grapheme.toString()
        assertEquals(expected.split('|'), graphemes, source)
        assertEquals(graphemes.size, source.u8.length, source)
    }
}
