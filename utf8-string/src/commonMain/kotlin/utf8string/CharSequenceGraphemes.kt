/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

@file:JvmName("CharSequenceGraphemes")

package utf8string

import kotlin.jvm.JvmName

/**
 * True when a grapheme starts at the char [index], or [index] is 0 or `length`. False otherwise, even between the 2
 * chars of a surrogate pair. The same as `isGraphemeBoundary(index, 0, length)`.
 *
 * @throws IndexOutOfBoundsException when [index] is not in `0..length`.
 * @sample samples.CharSequenceSamples.isGraphemeBoundaryChecksACharIndex
 */
public fun CharSequence.isGraphemeBoundary(index: Int): Boolean = isGraphemeBoundary(index, 0, length)

/**
 * True when a grapheme starts at the char [index], or [index] is [startIndex] or [endIndex]. False otherwise, even
 * between the 2 chars of a surrogate pair.
 *
 * The text is the chars in `startIndex..<endIndex`, for example one line of a document, and the function reads no char
 * outside it. A lone surrogate counts as U+FFFD, as in [u8], so the boundaries are those of the [Utf8String] of the
 * text. The function allocates nothing. It reads the grapheme around [index] and the one before it, and in a run of
 * flags also the run before it. So a loop of such calls over a run of flags reads the run again for each flag, and its
 * time grows as the square of the run length. A loop over the graphemes should call [nextGraphemeBoundary] with
 * `startIndex` at the last boundary, which walks the text in linear time.
 *
 * @throws IndexOutOfBoundsException when [startIndex] is negative or [endIndex] is greater than `length`, or when the
 *   range is valid and [index] is not in `startIndex..endIndex`.
 * @throws IllegalArgumentException when [startIndex] is greater than [endIndex], [startIndex] is not negative, and
 *   [endIndex] is not greater than `length`.
 * @sample samples.CharSequenceSamples.queryOneLineOfAText
 */
public fun CharSequence.isGraphemeBoundary(index: Int, startIndex: Int, endIndex: Int): Boolean {
    checkIndexInRange(index, startIndex, endIndex)
    return isGraphemeBoundaryAt(this, startIndex, endIndex, index)
}

/**
 * The first grapheme boundary after the char [index], which is the end of the grapheme that contains the char at
 * [index], or -1 when [index] is `length`. The same as `nextGraphemeBoundary(index, 0, length)`.
 *
 * @throws IndexOutOfBoundsException when [index] is not in `0..length`.
 * @sample samples.CharSequenceSamples.nextGraphemeBoundaryFindsTheEnd
 * @sample samples.CharSequenceSamples.roundAnIndexToAGraphemeBoundary
 */
public fun CharSequence.nextGraphemeBoundary(index: Int): Int = nextGraphemeBoundary(index, 0, length)

/**
 * The first grapheme boundary after the char [index], which is the end of the grapheme that contains the char at
 * [index], or -1 when [index] is [endIndex].
 *
 * The text is the chars in `startIndex..<endIndex`, for example one line of a document, and the function reads no char
 * outside it. A lone surrogate counts as U+FFFD, as in [u8], so the boundaries are those of the [Utf8String] of the
 * text. The function allocates nothing. It reads the grapheme around [index] and the one before it, and in a run of
 * flags also the run before it. So a loop of such calls over a run of flags reads the run again for each flag, and its
 * time grows as the square of the run length. When [index] is a boundary, `startIndex = index` gives the same result
 * without reading the text before [index], since the boundaries after a boundary do not depend on it. So a loop over
 * the graphemes that passes each boundary as [startIndex] walks the text in linear time.
 *
 * @throws IndexOutOfBoundsException when [startIndex] is negative or [endIndex] is greater than `length`, or when the
 *   range is valid and [index] is not in `startIndex..endIndex`.
 * @throws IllegalArgumentException when [startIndex] is greater than [endIndex], [startIndex] is not negative, and
 *   [endIndex] is not greater than `length`.
 * @sample samples.CharSequenceSamples.walkTheGraphemes
 * @sample samples.CharSequenceSamples.queryOneLineOfAText
 */
public fun CharSequence.nextGraphemeBoundary(index: Int, startIndex: Int, endIndex: Int): Int {
    checkIndexInRange(index, startIndex, endIndex)
    return nextGraphemeBoundaryAt(this, startIndex, endIndex, index)
}

/**
 * The last grapheme boundary before the char [index], which is the start of the grapheme that contains the char at
 * `index - 1`, or -1 when [index] is 0. The same as `previousGraphemeBoundary(index, 0, length)`.
 *
 * @throws IndexOutOfBoundsException when [index] is not in `0..length`.
 * @sample samples.CharSequenceSamples.previousGraphemeBoundaryFindsTheStart
 * @sample samples.CharSequenceSamples.roundAnIndexToAGraphemeBoundary
 */
public fun CharSequence.previousGraphemeBoundary(index: Int): Int = previousGraphemeBoundary(index, 0, length)

/**
 * The last grapheme boundary before the char [index], which is the start of the grapheme that contains the char at
 * `index - 1`, or -1 when [index] is [startIndex]. So for an index inside a grapheme, it is the start of that grapheme,
 * as in `java.text.BreakIterator`.
 *
 * The text is the chars in `startIndex..<endIndex`, for example one line of a document, and the function reads no char
 * outside it. A lone surrogate counts as U+FFFD, as in [u8], so the boundaries are those of the [Utf8String] of the
 * text. The function allocates nothing. It reads the grapheme around [index] and the one before it, and in a run of
 * flags also the run before it. So a loop of such calls over a run of flags reads the run again for each flag, and its
 * time grows as the square of the run length. A loop over the graphemes should call [nextGraphemeBoundary] with
 * `startIndex` at the last boundary, which walks the text in linear time.
 *
 * @throws IndexOutOfBoundsException when [startIndex] is negative or [endIndex] is greater than `length`, or when the
 *   range is valid and [index] is not in `startIndex..endIndex`.
 * @throws IllegalArgumentException when [startIndex] is greater than [endIndex], [startIndex] is not negative, and
 *   [endIndex] is not greater than `length`.
 * @sample samples.CharSequenceSamples.queryOneLineOfAText
 */
public fun CharSequence.previousGraphemeBoundary(index: Int, startIndex: Int, endIndex: Int): Int {
    checkIndexInRange(index, startIndex, endIndex)
    return previousGraphemeBoundaryAt(this, startIndex, endIndex, index)
}

// The checks and their order follow AbstractList.checkBoundsIndexes of the standard library, with length for its size.
private fun CharSequence.checkIndexInRange(index: Int, startIndex: Int, endIndex: Int) {
    if (startIndex < 0 || endIndex > length) {
        throw IndexOutOfBoundsException("startIndex: $startIndex, endIndex: $endIndex, length: $length")
    }
    if (startIndex > endIndex) throw IllegalArgumentException("startIndex: $startIndex > endIndex: $endIndex")
    if (index < startIndex || index > endIndex) {
        throw IndexOutOfBoundsException("index: $index, startIndex: $startIndex, endIndex: $endIndex")
    }
}
