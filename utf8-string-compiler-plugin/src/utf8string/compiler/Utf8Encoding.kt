/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.compiler

import java.io.ByteArrayOutputStream

class Utf8Literal(val bytes: ByteArray, val codePointCount: Int, val hasUnpairedSurrogate: Boolean)

// The compiler's String.toByteArray(UTF_8) writes '?' for an unpaired surrogate, while the library writes U+FFFD.
fun encodeUtf8(source: String): Utf8Literal {
    val bytes = ByteArrayOutputStream(source.length)
    var codePointCount = 0
    var hasUnpairedSurrogate = false
    var i = 0
    while (i < source.length) {
        val char = source[i++]
        val codePoint = when {
            char.isHighSurrogate() && i < source.length && source[i].isLowSurrogate() -> Character.toCodePoint(char, source[i++])
            char.isSurrogate() -> {
                hasUnpairedSurrogate = true
                0xFFFD
            }
            else -> char.code
        }
        when {
            codePoint < 0x80 -> bytes.write(codePoint)
            codePoint < 0x800 -> {
                bytes.write(0xC0 or (codePoint shr 6))
                bytes.write(0x80 or (codePoint and 0x3F))
            }
            codePoint < 0x10000 -> {
                bytes.write(0xE0 or (codePoint shr 12))
                bytes.write(0x80 or ((codePoint shr 6) and 0x3F))
                bytes.write(0x80 or (codePoint and 0x3F))
            }
            else -> {
                bytes.write(0xF0 or (codePoint shr 18))
                bytes.write(0x80 or ((codePoint shr 12) and 0x3F))
                bytes.write(0x80 or ((codePoint shr 6) and 0x3F))
                bytes.write(0x80 or (codePoint and 0x3F))
            }
        }
        codePointCount++
    }
    return Utf8Literal(bytes.toByteArray(), codePointCount, hasUnpairedSurrogate)
}

fun latin1String(bytes: ByteArray): String = String(CharArray(bytes.size) { (bytes[it].toInt() and 0xFF).toChar() })
