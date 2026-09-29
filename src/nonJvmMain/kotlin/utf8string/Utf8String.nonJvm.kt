/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

// encodeToByteArray() would allocate an intermediate buffer here.
internal actual fun encodeAscii(source: String): ByteArray = ByteArray(source.length) { source[it].code.toByte() }
