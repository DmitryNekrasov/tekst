/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.tests

import utf8string.Utf8String
import utf8string.u8
import kotlin.test.Test
import kotlin.test.assertEquals

// The plugin stores literals as a Latin-1 string or as byteArrayOf depending on the target and the size, so each
// storage must give the same graphemes as run-time encoding.
class GraphemeLiteralsTest {
    @Test
    fun literalsHaveTheGraphemesOfRuntimeStrings() {
        assertGraphemes("Hello!", "Hello!".u8)
        assertGraphemes("e\u0301\u0915\u094D\u0937\r\n", "e\u0301\u0915\u094D\u0937\r\n".u8)
        assertGraphemes(
            "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67 \uD83C\uDDF3\uD83C\uDDF4",
            "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67 \uD83C\uDDF3\uD83C\uDDF4".u8,
        )
        // Above 1 KiB, so every target gets the bytes from a Latin-1 string constant.
        assertGraphemes(K1, K1.u8)
        assertGraphemes(LATIN1, LATIN1.u8)
    }

    @Test
    fun lengthOfLiterals() {
        assertEquals(6, "Hello!".u8.length)
        assertEquals(1, "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67".u8.length)
        assertEquals(0, "".u8.length)
    }

    private fun runtime(value: String) = value.u8

    private fun assertGraphemes(source: String, literal: Utf8String) {
        val expected = runtime(source)
        assertEquals(graphemes(expected), graphemes(literal), source)
        assertEquals(expected.length, literal.length, source)
    }

    private fun graphemes(string: Utf8String): List<String> {
        val graphemes = ArrayList<String>()
        for (grapheme in string) graphemes += grapheme.toString()
        return graphemes
    }
}
