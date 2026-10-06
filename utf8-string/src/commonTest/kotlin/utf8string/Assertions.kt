/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.test.assertEquals

// The counts first, since a failure shows the strings decoded, which hides a wrong count or malformed bytes.
internal fun assertUtf8Equals(expected: Utf8String, actual: Utf8String, message: String) {
    assertEquals(expected.byteCount, actual.byteCount, "byte count of $message")
    assertEquals(expected.codePointCount, actual.codePointCount, "code point count of $message")
    assertEquals(expected, actual, "bytes of $message")
}

internal fun ByteArray.toHex(): String = joinToString(" ") { it.toUByte().toString(16).uppercase().padStart(2, '0') }

internal fun String.hexToBytes(): ByteArray = split(' ').map { it.toInt(16).toByte() }.toByteArray()
