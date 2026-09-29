/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Utf8StringJvmTest {
    // The JDK encoder with U+FFFD as the replacement is an independent reference for unpaired surrogates.
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

            val utf8 = Utf8String.fromString(source)
            assertContentEquals(expected, utf8.buffer)
            assertEquals(source.codePointCount(0, source.length), utf8.codePointCount)
        }
    }
}
