/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.test.assertEquals

// Compares the counts before the bytes: a failure shows the strings decoded, and the same bytes with another code point
// count, or malformed bytes, decode alike.
internal fun assertUtf8Equals(expected: Utf8String, actual: Utf8String, message: String) {
    assertEquals(expected.byteCount, actual.byteCount, "byte count of $message")
    assertEquals(expected.codePointCount, actual.codePointCount, "code point count of $message")
    assertEquals(expected, actual, "bytes of $message")
}

internal fun ByteArray.toHex(): String = joinToString(" ") { it.toUByte().toString(16).uppercase().padStart(2, '0') }
