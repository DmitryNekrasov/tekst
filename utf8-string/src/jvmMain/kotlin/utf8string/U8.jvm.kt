/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

// On JDK 9+, a Latin-1 string stores these bytes, so this is a plain array copy.
internal actual fun latin1Bytes(latin1: String): ByteArray = latin1.toByteArray(Charsets.ISO_8859_1)
