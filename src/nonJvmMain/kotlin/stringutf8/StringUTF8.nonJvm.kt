/*
 * Copyright 2026 Dmitry Nekrasov and string-utf8 library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package stringutf8

// encodeToByteArray() would allocate an intermediate buffer here.
internal actual fun encodeAscii(source: String): ByteArray = ByteArray(source.length) { source[it].code.toByte() }
