/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

// The JVM stores an ASCII string as bytes, so this is a plain array copy.
internal actual fun encodeAscii(source: String): ByteArray = source.encodeToByteArray()
