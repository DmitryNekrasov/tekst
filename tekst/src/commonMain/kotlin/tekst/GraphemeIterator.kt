/*
 * Copyright 2026 Dmitry Nekrasov and tekst library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tekst

/**
 * An iterator over the graphemes of a [Utf8String], in both directions. For one ASCII char or CR LF, [next] and
 * [previous] return a shared object, and for any other grapheme they allocate one small object, which [skipNext] and
 * [skipPrevious] avoid.
 *
 * @sample samples.GraphemeSamples.hasNextAndNextWalkTheGraphemes
 * @sample samples.GraphemeSamples.iteratorWalksBothWays
 */
public class GraphemeIterator internal constructor(
    private val bytes: ByteArray,
    // No default value: the JIT would not inline the extra constructor, and a for loop would allocate the iterator.
    start: Int,
) : Iterator<Grapheme> {
    private val classes = GraphemeTables.classes
    private val classIndex = GraphemeTables.index
    private val transitions = GraphemeTables.transitions
    private val startStates = GraphemeTables.startStates
    private val asciiGraphemes = AsciiGraphemes.single

    private var position = start

    // The length of the code point at position and the automaton row after it, when the previous search has classified
    // it and it is not ASCII, else carriedLength is 0. Two fields, so that the start of the next code point does not
    // wait for the table lookups that give the state.
    private var carriedLength = 0
    private var carriedRow = 0

    // A run of regional indicators bytes[runStart..<runEnd] that starts at runStart, or -1 before previous meets one.
    private var runStart = -1
    private var runEnd = -1

    /**
     * The byte index of the iterator: 0 at the start, [Utf8String.byteCount] at the end, and a grapheme boundary in
     * between. [next] returns the grapheme that starts here, and [previous] the one that ends here.
     *
     * @sample samples.GraphemeSamples.indexIsTheByteBetweenGraphemes
     */
    public val index: Int
        get() = position

    /** @sample samples.GraphemeSamples.hasNextAndNextWalkTheGraphemes */
    override fun hasNext(): Boolean = position < bytes.size

    // Small, with one allocation, so that the JIT can inline it into the loop and remove the allocation.
    /** @sample samples.GraphemeSamples.hasNextAndNextWalkTheGraphemes */
    override fun next(): Grapheme {
        val start = position
        if (start >= bytes.size) throw NoSuchElementException()
        val end = advance()
        val first = bytes[start].toInt()
        if (first >= 0) {
            if (end - start == 1) return asciiGraphemes[first]
            if (end - start == 2 && first == CR) return AsciiGraphemes.crLf
        }
        return Grapheme(bytes, start, end)
    }

    /**
     * True when [index] is not 0, so that [previous] has a grapheme to return.
     *
     * @sample samples.GraphemeSamples.hasPreviousAndPreviousWalkBack
     */
    public fun hasPrevious(): Boolean = position > 0

    /**
     * The grapheme that ends at [index], and moves [index] to its start. After [next], it returns the same grapheme.
     *
     * @throws NoSuchElementException when [index] is 0.
     * @sample samples.GraphemeSamples.hasPreviousAndPreviousWalkBack
     */
    public fun previous(): Grapheme {
        val end = position
        if (end <= 0) throw NoSuchElementException()
        val start = retreat()
        val first = bytes[start].toInt()
        if (first >= 0) {
            if (end - start == 1) return asciiGraphemes[first]
            // The walk back can join a continuation byte to CR on malformed bytes.
            if (end - start == 2 && first == CR && bytes[start + 1].toInt() == LF) return AsciiGraphemes.crLf
        }
        return Grapheme(bytes, start, end)
    }

    /**
     * Moves over the next grapheme as [next] does, without a [Grapheme], and returns the new [index].
     *
     * @throws NoSuchElementException when [index] is at the end.
     * @sample samples.GraphemeSamples.skipNextMovesWithoutAGrapheme
     */
    public fun skipNext(): Int {
        if (position >= bytes.size) throw NoSuchElementException()
        return moveToNextBoundary()
    }

    /**
     * Moves over the previous grapheme as [previous] does, without a [Grapheme], and returns the new [index].
     *
     * @throws NoSuchElementException when [index] is 0.
     * @sample samples.GraphemeSamples.skipPreviousMovesWithoutAGrapheme
     */
    public fun skipPrevious(): Int {
        if (position <= 0) throw NoSuchElementException()
        return moveToPreviousBoundary()
    }

    // Kotlin/Native and Wasm inline advance into next, since next is its only caller, so skipNext has its own copy of
    // the body. The same holds for retreat, previous and skipPrevious.
    private fun advance(): Int = moveToNextBoundary()

    private fun retreat(): Int = moveToPreviousBoundary()

    @Suppress("NOTHING_TO_INLINE")
    private inline fun moveToNextBoundary(): Int {
        val start = position
        val carriedLength = carriedLength
        if (carriedLength != 0) return search(start + carriedLength, carriedRow)
        val first = bytes[start].toInt()
        if (first >= 0) {
            val end = start + 1
            if (first >= 0x20 && first != 0x7F) {
                // Printable ASCII is Other, and no code point below U+0300 (lead byte below 0xCC) joins it.
                if (end == bytes.size || bytes[end].toInt() and 0xFF < 0xCC) {
                    position = end
                    return end
                }
                return search(end, startStates[GRAPHEME_CLASS_OTHER].code)
            }
            // Malformed bytes after CR can stand for LF, so the automaton decides, as in countGraphemes.
            if (first == CR && end < bytes.size && bytes[end] < 0) {
                return search(end, startStates[graphemeClassAt(bytes, start, 1, classes, classIndex)].code)
            }
            // A control is a grapheme by itself (GB4, GB5), except CR LF (GB3).
            position = if (first == CR && end < bytes.size && bytes[end].toInt() == LF) end + 1 else end
            return position
        }
        val length = utf8LengthAt(bytes, start)
        return search(start + length, startStates[graphemeClassAt(bytes, start, length, classes, classIndex)].code)
    }

    private fun search(from: Int, row: Int): Int {
        var end = from
        var current = row
        while (end < bytes.size) {
            val length = utf8LengthAt(bytes, end)
            val transition = transitions[current + graphemeClassAt(bytes, end, length, classes, classIndex)].code
            if (transition >= BOUNDARY) {
                // The transition holds the start state of the next grapheme.
                if (bytes[end] < 0) {
                    carriedLength = length
                    carriedRow = transition - BOUNDARY
                } else {
                    carriedLength = 0
                }
                position = end
                return end
            }
            current = transition
            end += length
        }
        carriedLength = 0
        position = end
        return end
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun moveToPreviousBoundary(): Int {
        carriedLength = 0
        val end = position
        val last = bytes[end - 1].toInt()
        if (last >= 0 && (end == 1 || bytes[end - 2] >= 0)) {
            // Between 2 ASCII chars only CR LF joins, and a boundary comes before CR after anything.
            position = if (last == LF && end >= 2 && bytes[end - 2].toInt() == CR) end - 2 else end - 1
            return position
        }
        var k = codePointStartAt(bytes, 0, end - 1)
        var b = classAt(k)
        while (k > 0) {
            val p = codePointStartAt(bytes, 0, k - 1)
            val a = classAt(p)
            val breaks = if (a == GRAPHEME_CLASS_REGIONAL_INDICATOR && b == GRAPHEME_CLASS_REGIONAL_INDICATOR) {
                breaksInRegionalIndicators(runStartOf(p), k)
            } else {
                // For the few pairs whose boundary depends on the text before them, this reads that text, so a walk
                // back and forth over such a boundary reads it again each time it goes back.
                breaksBetween(bytes, 0, bytes.size, p, a, b)
            }
            if (breaks) break
            k = p
            b = a
        }
        position = k
        return k
    }

    private fun classAt(i: Int): Int = graphemeClassAt(bytes, i, utf8LengthAt(bytes, i), classes, classIndex)

    // A scan back from p that reaches the end of the last run continues that run, so that a walk reads the last run
    // once, whichever way it goes. A walk back and forth between 2 runs reads the earlier one again each time.
    private fun runStartOf(p: Int): Int {
        val lastStart = runStart
        val lastEnd = runEnd
        if (p in lastStart..<lastEnd) return lastStart
        val stop = if (lastStart >= 0 && p >= lastEnd) lastEnd else 0
        val start = regionalIndicatorRunStart(bytes, stop, bytes.size, p)
        runEnd = p + 4
        if (start == stop && stop > 0) return lastStart
        runStart = start
        return start
    }
}

// From the lead byte alone, so that finding the next code point does not wait for the table lookup of this one.
internal fun utf8LengthAt(bytes: ByteArray, i: Int): Int {
    val lead = bytes[i].toInt() and 0xFF
    val length = if (lead < 0xC0) 1 else if (lead < 0xE0) 2 else if (lead < 0xF0) 3 else 4
    return if (length <= bytes.size - i) length else bytes.size - i
}

// Malformed bytes give some class, with indices that stay within the tables.
internal fun graphemeClassAt(bytes: ByteArray, i: Int, length: Int, classes: ByteArray, index: CharArray): Int {
    val lead = bytes[i].toInt() and 0xFF
    return when (length) {
        1 -> if (lead < 0x80) classes[index[lead shr 6].code + (lead and 0x3F)].toInt() else GRAPHEME_CLASS_OTHER
        2 -> classes[index[lead and 0x1F].code + (bytes[i + 1].toInt() and 0x3F)].toInt()
        3 -> {
            val block = index[((lead and 0x0F) shl 6) or (bytes[i + 1].toInt() and 0x3F)].code
            classes[block + (bytes[i + 2].toInt() and 0x3F)].toInt()
        }

        else -> {
            // Only planes 0 and 1 and U+E0000..U+E0FFF have classes other than Other.
            val second = bytes[i + 1].toInt() and 0x3F
            val row = when {
                lead == 0xF0 && second < 0x20 -> (second shl 6) or (bytes[i + 2].toInt() and 0x3F)
                lead == 0xF3 && second == 0x20 -> 2048 + (bytes[i + 2].toInt() and 0x3F)
                else -> return GRAPHEME_CLASS_OTHER
            }
            classes[index[row].code + (bytes[i + 3].toInt() and 0x3F)].toInt()
        }
    }
}

// The blocks of graphemeClassAt: below U+20000 the block is codePoint shr 6, and the 64 blocks of U+E0000..U+E0FFF
// follow.
internal fun graphemeClassOf(codePoint: Int, classes: ByteArray, index: CharArray): Int {
    val block = when {
        codePoint < 0x20000 -> codePoint shr 6
        codePoint shr 12 == 0xE0 -> 2048 + ((codePoint shr 6) and 0x3F)
        else -> return GRAPHEME_CLASS_OTHER
    }
    return classes[index[block].code + (codePoint and 0x3F)].toInt()
}

internal fun countGraphemes(bytes: ByteArray, isAscii: Boolean): Int {
    if (isAscii) {
        var count = bytes.size
        for (i in 1..<bytes.size) if (bytes[i].toInt() == LF && bytes[i - 1].toInt() == CR) count--
        return count
    }
    if (bytes.isEmpty()) return 0
    val classes = GraphemeTables.classes
    val index = GraphemeTables.index
    val transitions = GraphemeTables.transitions
    var i = utf8LengthAt(bytes, 0)
    var row = GraphemeTables.startStates[graphemeClassAt(bytes, 0, i, classes, index)].code
    var count = 1
    while (i < bytes.size) {
        val length = utf8LengthAt(bytes, i)
        val transition = transitions[row + graphemeClassAt(bytes, i, length, classes, index)].code
        count += transition ushr 15
        row = transition and (BOUNDARY - 1)
        i += length
    }
    return count
}

internal object GraphemeTables {
    val classes: ByteArray = latin1Bytes(GRAPHEME_CLASS_DATA).also { data ->
        for (i in data.indices) data[i] = (data[i] - 'A'.code).toByte()
    }
    val index: CharArray = GRAPHEME_CLASS_INDEX.toCharArray()
    val transitions: CharArray = GRAPHEME_TRANSITIONS.toCharArray()
    val startStates: CharArray = GRAPHEME_START_STATES.toCharArray()
    val pairs: CharArray = GRAPHEME_PAIR_ROWS.toCharArray()
}

// The flag of a transition across a cluster boundary.
internal const val BOUNDARY = 0x8000

internal const val CR = 0x0D
internal const val LF = 0x0A
