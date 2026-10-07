/*
 * Copyright 2026 Dmitry Nekrasov and tekst library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tekst

import kotlin.concurrent.Volatile

/**
 * An immutable string stored as UTF-8 bytes. Its [length] counts graphemes, what a user sees as one character, and
 * `for` iterates over them. [u8] and [toUtf8String] make one from a [String], and nothing makes one from bytes.
 *
 * An index is a byte index, as in [copyInto], and a count is a number of graphemes, as [length] is. A function that
 * takes an index reads the grapheme around it and the one before it, and in a run of flags also the run before it, so a
 * loop over the graphemes should use an [iterator] instead of calling such a function for each grapheme.
 *
 * @sample samples.Utf8StringSamples.countAndIterateGraphemes
 * @sample samples.Utf8StringSamples.lengthIsNotAByteIndex
 */
public class Utf8String internal constructor(private val buffer: ByteArray, internal val codePointCount: Int) {
    // Volatile, because on Kotlin/Native reading a plain field while another thread writes it is undefined.
    @Volatile
    private var hash: Int = 0

    @Volatile
    private var graphemeCount: Int = 0

    /**
     * The number of UTF-8 bytes.
     *
     * @sample samples.Utf8StringSamples.byteCountCountsUtf8Bytes
     */
    public val byteCount: Int
        get() = buffer.size

    /**
     * A copy of the UTF-8 bytes.
     *
     * @sample samples.Utf8StringSamples.toByteArrayCopiesTheBytes
     */
    public fun toByteArray(): ByteArray = buffer.copyOf()

    /**
     * Like [ByteArray.copyInto], for the UTF-8 bytes. The range counts bytes, so it can split a code point.
     *
     * @sample samples.Utf8StringSamples.copyIntoWritesIntoOneBuffer
     */
    public fun copyInto(
        destination: ByteArray,
        destinationOffset: Int = 0,
        startIndex: Int = 0,
        endIndex: Int = byteCount,
    ): ByteArray = buffer.copyInto(destination, destinationOffset, startIndex, endIndex)

    /**
     * The number of graphemes, computed on the first access. [String.length] counts UTF-16 chars.
     *
     * @sample samples.Utf8StringSamples.lengthCountsGraphemes
     */
    public val length: Int
        get() {
            var count = graphemeCount
            if (count == 0 && buffer.isNotEmpty()) {
                count = countGraphemes(buffer, isAscii = codePointCount == buffer.size)
                graphemeCount = count
            }
            return count
        }

    /**
     * An iterator over the graphemes, which a `for` loop over the string uses.
     *
     * @sample samples.Utf8StringSamples.forLoopUsesTheIterator
     */
    public operator fun iterator(): GraphemeIterator = GraphemeIterator(buffer, 0)

    /**
     * An iterator that starts at the last grapheme boundary at or before the byte [index], so that its
     * [GraphemeIterator.next] returns the grapheme that contains the byte at [index].
     *
     * @throws IndexOutOfBoundsException when [index] is not in `0..byteCount`.
     * @sample samples.Utf8StringSamples.iteratorStartsAtAByteIndex
     * @sample samples.Utf8StringSamples.fitGraphemesIntoAByteLimit
     */
    public fun iterator(index: Int): GraphemeIterator {
        checkIndex(index)
        if (index == buffer.size) return GraphemeIterator(buffer, index)
        // The iterator walks back itself, so that it keeps the run of flags that it reads.
        return GraphemeIterator(buffer, index + 1).also { it.skipPrevious() }
    }

    /**
     * The graphemes as a sequence, which can be iterated more than once.
     *
     * @sample samples.Utf8StringSamples.graphemesIsASequence
     */
    public val graphemes: Sequence<Grapheme>
        get() = Sequence { iterator() }

    /**
     * True when a grapheme starts at the byte [index], or [index] is 0 or [byteCount]. False otherwise, even for an
     * index inside a code point.
     *
     * @throws IndexOutOfBoundsException when [index] is not in `0..byteCount`.
     * @sample samples.Utf8StringSamples.isGraphemeBoundaryChecksAByteIndex
     */
    public fun isGraphemeBoundary(index: Int): Boolean {
        checkIndex(index)
        return isGraphemeBoundaryAt(buffer, 0, buffer.size, index)
    }

    /**
     * The first grapheme boundary after the byte [index], which is the end of the grapheme that contains the byte at
     * [index], or -1 when [index] is [byteCount].
     *
     * @throws IndexOutOfBoundsException when [index] is not in `0..byteCount`.
     * @sample samples.Utf8StringSamples.nextGraphemeBoundaryFindsTheEnd
     * @sample samples.Utf8StringSamples.roundAnIndexToAGraphemeBoundary
     */
    public fun nextGraphemeBoundary(index: Int): Int {
        checkIndex(index)
        return nextGraphemeBoundaryAt(buffer, 0, buffer.size, index)
    }

    /**
     * The last grapheme boundary before the byte [index], which is the start of the grapheme that contains the byte at
     * `index - 1`, or -1 when [index] is 0. So for an index inside a grapheme, it is the start of that grapheme, as in
     * `java.text.BreakIterator`, and not the start of the grapheme before it, as in Swift.
     *
     * @throws IndexOutOfBoundsException when [index] is not in `0..byteCount`.
     * @sample samples.Utf8StringSamples.previousGraphemeBoundaryFindsTheStart
     * @sample samples.Utf8StringSamples.roundAnIndexToAGraphemeBoundary
     */
    public fun previousGraphemeBoundary(index: Int): Int {
        checkIndex(index)
        return previousGraphemeBoundaryAt(buffer, 0, buffer.size, index)
    }

    /**
     * A string of the first [n] graphemes, or this string when it has [n] graphemes or fewer.
     *
     * @throws IllegalArgumentException when [n] is negative.
     * @sample samples.Utf8StringSamples.takeKeepsTheFirstGraphemes
     */
    public fun take(n: Int): Utf8String {
        require(n >= 0) { "Requested grapheme count $n is less than zero." }
        return substring(0, indexAfter(n), n)
    }

    /**
     * A string without the first [n] graphemes, or this string when [n] is 0.
     *
     * @throws IllegalArgumentException when [n] is negative.
     * @sample samples.Utf8StringSamples.dropRemovesTheFirstGraphemes
     */
    public fun drop(n: Int): Utf8String {
        require(n >= 0) { "Requested grapheme count $n is less than zero." }
        val cut = indexAfter(n)
        val count = graphemeCount
        return substring(cut, buffer.size, if (count == 0) 0 else count - n)
    }

    /**
     * A string of the last [n] graphemes, or this string when it has [n] graphemes or fewer.
     *
     * @throws IllegalArgumentException when [n] is negative.
     * @sample samples.Utf8StringSamples.takeLastKeepsTheLastGraphemes
     */
    public fun takeLast(n: Int): Utf8String {
        require(n >= 0) { "Requested grapheme count $n is less than zero." }
        return substring(indexBefore(n), buffer.size, 0)
    }

    /**
     * A string without the last [n] graphemes, or this string when [n] is 0.
     *
     * @throws IllegalArgumentException when [n] is negative.
     * @sample samples.Utf8StringSamples.dropLastRemovesTheLastGraphemes
     */
    public fun dropLast(n: Int): Utf8String {
        require(n >= 0) { "Requested grapheme count $n is less than zero." }
        return substring(0, indexBefore(n), 0)
    }

    private fun checkIndex(index: Int) {
        if (index < 0 || index > buffer.size) {
            throw IndexOutOfBoundsException("index: $index, byteCount: ${buffer.size}")
        }
    }

    private fun indexAfter(n: Int): Int {
        // Each grapheme takes at least a byte.
        if (n >= buffer.size) return buffer.size
        val count = graphemeCount
        if (count != 0 && n >= count) return buffer.size
        val graphemes = GraphemeIterator(buffer, 0)
        var skipped = 0
        while (skipped < n && graphemes.hasNext()) {
            graphemes.skipNext()
            skipped++
        }
        if (!graphemes.hasNext() && skipped > 0) graphemeCount = skipped
        return graphemes.index
    }

    // The walk back neither reads nor sets the length, which counts the graphemes of the for loop: on malformed bytes
    // the walk back can split the text differently.
    private fun indexBefore(n: Int): Int {
        if (n >= buffer.size) return 0
        val graphemes = GraphemeIterator(buffer, buffer.size)
        var skipped = 0
        while (skipped < n && graphemes.hasPrevious()) {
            graphemes.skipPrevious()
            skipped++
        }
        return graphemes.index
    }

    // A grapheme count of 0 means unknown. A part cut by the walk forward has the graphemes that the walk counted,
    // since a grapheme after a boundary does not depend on the text before it.
    private fun substring(from: Int, to: Int, graphemeCount: Int): Utf8String {
        if (from == 0 && to == buffer.size) return this
        if (from == to) return Utf8String(ByteArray(0), 0)
        val bytes = buffer.copyOfRange(from, to)
        // When length counts this string as ASCII, it can count every part of it as ASCII too.
        val result = Utf8String(bytes, if (codePointCount == buffer.size) bytes.size else codePointCountOfCopy(bytes))
        result.graphemeCount = graphemeCount
        return result
    }

    /**
     * True when [other] is a [Utf8String] with the same UTF-8 bytes, without Unicode normalization.
     *
     * @sample samples.Utf8StringSamples.equalsComparesTheBytes
     */
    override fun equals(other: Any?): Boolean {
        return this === other ||
                other is Utf8String && codePointCount == other.codePointCount && buffer.contentEquals(other.buffer)
    }

    /**
     * A hash of the UTF-8 bytes, computed on the first call.
     *
     * @sample samples.Utf8StringSamples.hashCodeAgreesWithEquals
     */
    override fun hashCode(): Int {
        var h = hash
        if (h == 0) {
            h = buffer.contentHashCode()
            if (h == 0) h = 1
            hash = h
        }
        return h
    }

    /**
     * Decodes the UTF-8 bytes into a [String], again on each call.
     *
     * @sample samples.Utf8StringSamples.toStringDecodesTheBytes
     */
    override fun toString(): String = buffer.decodeToString()

    internal companion object {
        fun fromString(source: String): Utf8String {
            val length = source.length
            var i = 0
            while (i < length && source[i] < '\u0080') i++
            if (i == length) return Utf8String(latin1Bytes(source), length)

            val asciiPrefix = i
            var byteCount = length
            var codePointCount = length
            while (i < length) {
                val char = source[i++]
                // Branch-free: +1 byte from U+0080, +1 more from U+0800.
                byteCount += ((0x7F - char.code) ushr 31) + ((0x7FF - char.code) ushr 31)
                // A surrogate pair takes 4 bytes, and an unpaired surrogate becomes the 3 bytes of U+FFFD.
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

// The number of lead bytes, from the copied bytes alone, so that equal bytes make equal strings. When that equals the
// byte count, length takes the bytes as ASCII, which is wrong if a byte from 0xC0 comes before the last one: then it is
// the number of code points that utf8LengthAt splits them into.
internal fun codePointCountOfCopy(bytes: ByteArray): Int {
    var count = 0
    for (byte in bytes) if (byte.toInt() and 0xC0 != 0x80) count++
    if (count != bytes.size) return count
    for (i in 0..<bytes.size - 1) {
        if (bytes[i].toInt() and 0xFF >= 0xC0) {
            var codePoints = 0
            var k = 0
            while (k < bytes.size) {
                k += utf8LengthAt(bytes, k)
                codePoints++
            }
            return codePoints
        }
    }
    return count
}
