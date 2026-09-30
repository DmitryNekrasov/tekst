/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.test.Test

class U8Test {
    @Test
    fun u8EncodesLikeFromString() {
        for (source in listOf("", "ascii", "\u041F\u0440\u0438\u0432\u0435\u0442", "a\u20AC\uD83D\uDE00", "\uD800x")) {
            assertUtf8Equals(Utf8String.fromString(source), source.u8, source)
        }
    }

    @Test
    fun u8LiteralWrapsTheBytes() {
        assertUtf8Equals(Utf8String(byteArrayOf(0x61, 0x62), 2), u8Literal(byteArrayOf(0x61, 0x62), 2), "61 62")
    }

    @Test
    fun u8LiteralLatin1MapsEveryCharToOneByte() {
        val latin1 = CharArray(256) { Char(it) }.concatToString()
        assertUtf8Equals(Utf8String(ByteArray(256) { it.toByte() }, 7), u8LiteralLatin1(latin1, 7), "00..FF")
    }
}
