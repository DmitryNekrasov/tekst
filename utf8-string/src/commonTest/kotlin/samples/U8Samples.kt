/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

// The API reference shows these samples, so only the invisible characters are escaped.

package samples

import utf8string.toUtf8String
import utf8string.u8
import kotlin.test.Test
import kotlin.test.assertEquals

class U8Samples {
    @Test
    fun u8EncodesAString() {
        val text = "Hi 👋🏽".u8
        assertEquals(4, text.length)
        assertEquals(11, text.byteCount)
        assertEquals("Hi 👋🏽", text.toString())
    }

    @Test
    fun toUtf8StringEncodesAtRunTime() {
        // Not a constant, so .u8 on it would give the U8_NOT_CONSTANT warning.
        val input = "Hi 👋🏽".substringAfter(' ')
        val text = input.toUtf8String()
        assertEquals("👋🏽".u8, text)
        assertEquals(1, text.length)
    }
}
