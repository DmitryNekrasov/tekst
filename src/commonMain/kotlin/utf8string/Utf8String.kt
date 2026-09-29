/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

public class Utf8String(public val buffer: ByteArray, public val codePointCount: Int) {

    companion {
        public fun fromString(source: String): Utf8String {
            val length = source.length
            var i = 0
            while (i < length && source[i] < '\u0080') i++
            if (i == length) return Utf8String(encodeAscii(source), length)

            val asciiPrefix = i
            var byteCount = length
            var codePointCount = length
            while (i < length) {
                val char = source[i++]
                // Branch-free: +1 byte from U+0080, +1 more from U+0800.
                byteCount += ((0x7F - char.code) ushr 31) + ((0x7FF - char.code) ushr 31)
                // A surrogate pair takes 4 bytes; an unpaired surrogate becomes 3-byte U+FFFD.
                if (char.isHighSurrogate() && i < length && source[i].isLowSurrogate()) {
                    i++
                    codePointCount--
                }
            }
            // Int overflow leaves byteCount below length.
            require(byteCount >= length) { "The string is too long to be encoded in UTF-8" }

            val buffer = ByteArray(byteCount)
            for (k in 0..<asciiPrefix) buffer[k] = source[k].code.toByte()
            var pos = asciiPrefix
            i = asciiPrefix
            while (i < length) {
                val char = source[i++]
                when {
                    char < '\u0080' -> buffer[pos++] = char.code.toByte()
                    char < '\u0800' -> {
                        buffer[pos++] = (0xC0 or (char.code shr 6)).toByte()
                        buffer[pos++] = (0x80 or (char.code and 0x3F)).toByte()
                    }
                    !char.isSurrogate() -> {
                        buffer[pos++] = (0xE0 or (char.code shr 12)).toByte()
                        buffer[pos++] = (0x80 or ((char.code shr 6) and 0x3F)).toByte()
                        buffer[pos++] = (0x80 or (char.code and 0x3F)).toByte()
                    }
                    char.isHighSurrogate() && i < length && source[i].isLowSurrogate() -> {
                        val codePoint = ((char.code - 0xD800) shl 10) + (source[i++].code - 0xDC00) + 0x10000
                        buffer[pos++] = (0xF0 or (codePoint shr 18)).toByte()
                        buffer[pos++] = (0x80 or ((codePoint shr 12) and 0x3F)).toByte()
                        buffer[pos++] = (0x80 or ((codePoint shr 6) and 0x3F)).toByte()
                        buffer[pos++] = (0x80 or (codePoint and 0x3F)).toByte()
                    }
                    else -> {
                        // Unpaired surrogate: U+FFFD.
                        buffer[pos++] = 0xEF.toByte()
                        buffer[pos++] = 0xBF.toByte()
                        buffer[pos++] = 0xBD.toByte()
                    }
                }
            }
            return Utf8String(buffer, codePointCount)
        }
    }
}

// The source must be pure ASCII.
internal expect fun encodeAscii(source: String): ByteArray
