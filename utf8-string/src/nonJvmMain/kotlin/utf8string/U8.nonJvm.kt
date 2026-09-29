/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

internal actual fun latin1Bytes(latin1: String): ByteArray = ByteArray(latin1.length) { latin1[it].code.toByte() }
