/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.concurrent.Volatile

public class Utf8String internal constructor(private val buffer: ByteArray, internal val codePointCount: Int) {
    // Computed once, since the bytes never change; 0 means not computed yet. Volatile, because two threads may compute
    // it at once, and on Kotlin/Native a plain read that races with a write is undefined.
    @Volatile
    private var hash: Int = 0

    // The same for the number of graphemes; 0 means not computed yet, or an empty string.
    @Volatile
    private var graphemeCount: Int = 0

    /** The number of bytes of the UTF-8 encoding. */
    public val byteCount: Int
        get() = buffer.size

    /** A copy of the UTF-8 bytes. */
    public fun toByteArray(): ByteArray = buffer.copyOf()

    /**
     * Copies the UTF-8 bytes, or those from [startIndex] until [endIndex], into [destination] at [destinationOffset],
     * and returns [destination]. The range counts bytes, so it can split a code point.
     */
    public fun copyInto(
        destination: ByteArray,
        destinationOffset: Int = 0,
        startIndex: Int = 0,
        endIndex: Int = byteCount,
    ): ByteArray = buffer.copyInto(destination, destinationOffset, startIndex, endIndex)

    /** The number of extended grapheme clusters, computed on the first access. [String.length] counts UTF-16 chars. */
    public val length: Int
        get() {
            var count = graphemeCount
            if (count == 0 && buffer.isNotEmpty()) {
                count = countGraphemes(buffer, isAscii = codePointCount == buffer.size)
                graphemeCount = count
            }
            return count
        }

    /** Iterates over the extended grapheme clusters: `for (grapheme in string)`. */
    public operator fun iterator(): GraphemeIterator = GraphemeIterator(buffer)

    /** The extended grapheme clusters as a sequence, which can be iterated more than once. */
    public val graphemes: Sequence<Grapheme>
        get() = Sequence { iterator() }

    override fun equals(other: Any?): Boolean {
        return this === other ||
                other is Utf8String && codePointCount == other.codePointCount && buffer.contentEquals(other.buffer)
    }

    override fun hashCode(): Int {
        var h = hash
        if (h == 0) {
            h = buffer.contentHashCode()
            // A content hash of 0 is stored as 1, so that it is not computed again.
            if (h == 0) h = 1
            hash = h
        }
        return h
    }

    /** Decodes the UTF-8 bytes into a [String], again on each call. */
    override fun toString(): String = buffer.decodeToString()

    internal companion object {
        fun fromString(source: String): Utf8String {
            val length = source.length
            var i = 0
            while (i < length && source[i] < '\u0080') i++
            // An ASCII string is also Latin-1, with the same bytes.
            if (i == length) return Utf8String(latin1Bytes(source), length)

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
