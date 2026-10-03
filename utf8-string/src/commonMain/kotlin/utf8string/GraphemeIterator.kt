/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

/**
 * An iterator over the graphemes of a [Utf8String]. For one ASCII char or CR LF, [next] returns a shared object, and
 * for any other grapheme it allocates one small object.
 *
 * @sample samples.GraphemeSamples.hasNextAndNextWalkTheGraphemes
 */
public class GraphemeIterator internal constructor(private val bytes: ByteArray) : Iterator<Grapheme> {
    private val classes = GraphemeTables.classes
    private val index = GraphemeTables.index
    private val transitions = GraphemeTables.transitions
    private val startStates = GraphemeTables.startStates
    private val asciiGraphemes = AsciiGraphemes.single

    private var position = 0

    // The length of the code point at position and the automaton row after it, when the previous search has classified
    // it and it is not ASCII, else carriedLength is 0. Two fields, so that the start of the next code point does not
    // wait for the table lookups that give the state.
    private var carriedLength = 0
    private var carriedRow = 0

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

    private fun advance(): Int {
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
                return search(end, startStates[graphemeClassAt(bytes, start, 1, classes, index)].code)
            }
            // A control is a grapheme by itself (GB4, GB5), except CR LF (GB3).
            position = if (first == CR && end < bytes.size && bytes[end].toInt() == LF) end + 1 else end
            return position
        }
        val length = utf8LengthAt(bytes, start)
        return search(start + length, startStates[graphemeClassAt(bytes, start, length, classes, index)].code)
    }

    private fun search(from: Int, row: Int): Int {
        var end = from
        var current = row
        while (end < bytes.size) {
            val length = utf8LengthAt(bytes, end)
            val transition = transitions[current + graphemeClassAt(bytes, end, length, classes, index)].code
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
}

// The flag of a transition across a cluster boundary.
private const val BOUNDARY = 0x8000

private const val CR = 0x0D
private const val LF = 0x0A
