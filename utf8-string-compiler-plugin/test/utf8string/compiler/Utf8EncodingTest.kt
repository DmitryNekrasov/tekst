/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.compiler

import org.junit.jupiter.api.Test
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Utf8EncodingTest {
    // The JDK encoder with U+FFFD as the replacement, the same reference the library's own JVM test uses.
    private val encoder = Charsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .replaceWith(byteArrayOf(0xEF.toByte(), 0xBF.toByte(), 0xBD.toByte()))

    @Test
    fun matchesJdkEncoderOnRandomStrings() {
        val random = Random(42)
        repeat(100_000) {
            val source = CharArray(random.nextInt(16)) {
                Char(if (random.nextInt(3) == 0) random.nextInt(0xD800, 0xE000) else random.nextInt(0x10000))
            }.concatToString()
            val encoded = encoder.encode(CharBuffer.wrap(source))
            val expected = ByteArray(encoded.remaining()).also { encoded.get(it) }

            val literal = encodeUtf8(source)
            assertContentEquals(expected, literal.bytes)
            assertEquals(source.codePointCount(0, source.length), literal.codePointCount)
        }
    }

    @Test
    fun reportsUnpairedSurrogates() {
        assertEquals(false, encodeUtf8("a\uD83D\uDE00").hasUnpairedSurrogate)
        assertEquals(true, encodeUtf8("\uD800").hasUnpairedSurrogate)
        assertEquals(true, encodeUtf8("\uDC00\uD800").hasUnpairedSurrogate)
        assertEquals(true, encodeUtf8("\uD800\uD800\uDC00").hasUnpairedSurrogate)
    }

    @Test
    fun latin1StringKeepsEveryByte() {
        val bytes = ByteArray(256) { it.toByte() }
        assertEquals((0..255).toList(), latin1String(bytes).map { it.code })
    }
}
