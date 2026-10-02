/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

/**
 * An extended grapheme cluster of a [Utf8String] (UAX #29), what a user sees as one character. Graphemes with the same
 * UTF-8 bytes are equal. A grapheme keeps the bytes of its string in memory, and [toUtf8String] copies it.
 */
public class Grapheme internal constructor(
    private val bytes: ByteArray,
    private val start: Int,
    private val end: Int,
) {
    internal val byteCount: Int
        get() = end - start

    internal val codePointCount: Int
        get() {
            var count = 0
            for (i in start..<end) if (bytes[i].toInt() and 0xC0 != 0x80) count++
            return count
        }

    public fun toUtf8String(): Utf8String = Utf8String(bytes.copyOfRange(start, end), codePointCount)

    // No identity check, since comparing with this in a loop keeps the JIT from removing the allocation.
    override fun equals(other: Any?): Boolean {
        if (other !is Grapheme) return false
        val size = end - start
        if (size != other.end - other.start) return false
        for (i in 0..<size) if (bytes[start + i] != other.bytes[other.start + i]) return false
        return true
    }

    override fun hashCode(): Int {
        var hash = 1
        for (i in start..<end) hash = 31 * hash + bytes[i]
        return hash
    }

    override fun toString(): String = bytes.decodeToString(start, end)
}

internal object AsciiGraphemes {
    private val bytes = ByteArray(130) { if (it < 128) it.toByte() else if (it == 128) 0x0D else 0x0A }
    val single: Array<Grapheme> = Array(128) { Grapheme(bytes, it, it + 1) }
    val crLf: Grapheme = Grapheme(bytes, 128, 130)
}
