/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

// Grapheme boundaries at any byte index of the UTF-8 text in bytes[start..<end], with no memory but a few locals. For
// most classes, the automaton row after a code point does not depend on the text before it, and for most pairs of
// classes, the row after the pair does not. So to learn the row at an index, a query walks back to such a code point or
// pair and runs the automaton forward from there. Only for a few pairs, whether a boundary comes between the 2 code
// points depends on the text before them.
//
// In a run of regional indicators, a boundary depends on the parity of the run before it, so a query reads the run
// back to its start. GraphemeIterator.previous keeps the last run instead.
//
// On malformed bytes the boundaries are unspecified, but every loop moves by at least one byte and stays in the text.
//
// Utf8String passes the whole array. The range is for a later API over a part of the caller's array.

internal fun isGraphemeBoundaryAt(bytes: ByteArray, start: Int, end: Int, index: Int): Boolean {
    if (index == start || index == end) return true
    val next = bytes[index].toInt()
    if (next and 0xC0 == 0x80) return false
    val previous = bytes[index - 1].toInt()
    // Between 2 ASCII chars, only CR LF joins.
    if (previous >= 0 && next >= 0) return previous != CR || next != LF
    val p = codePointStartAt(bytes, start, index - 1)
    return breaksBetween(bytes, start, end, p, classAt(bytes, p, end), classAt(bytes, index, end))
}

internal fun nextGraphemeBoundaryAt(bytes: ByteArray, start: Int, end: Int, index: Int): Int {
    if (index == end) return -1
    val first = bytes[index].toInt()
    if (first >= 0) {
        // The fast paths of GraphemeIterator.moveToNextBoundary.
        val next = index + 1
        if (next == end) return next
        val second = bytes[next].toInt()
        if (first >= 0x20 && first != 0x7F) {
            if (second and 0xFF < 0xCC) return next
        } else if (first != CR || second >= 0) {
            return if (first == CR && second == LF) next + 1 else next
        }
    }
    val classes = GraphemeTables.classes
    val classIndex = GraphemeTables.index
    val transitions = GraphemeTables.transitions
    val s = codePointStartAt(bytes, start, index)
    var length = utf8LengthAt(bytes, s, end)
    var row = rowAfter(bytes, start, end, s, graphemeClassAt(bytes, s, length, classes, classIndex))
    var k = s + length
    while (k < end) {
        length = utf8LengthAt(bytes, k, end)
        val transition = transitions[row + graphemeClassAt(bytes, k, length, classes, classIndex)].code
        // On malformed bytes, a code point can start before index.
        if (transition >= BOUNDARY && k > index) return k
        row = transition and (BOUNDARY - 1)
        k += length
    }
    return end
}

internal fun previousGraphemeBoundaryAt(bytes: ByteArray, start: Int, end: Int, index: Int): Int {
    if (index == start) return -1
    val last = bytes[index - 1].toInt()
    if (last >= 0 && (index - 1 == start || bytes[index - 2] >= 0)) {
        // Between 2 ASCII chars only CR LF joins, and a boundary comes before CR after anything.
        return if (last == LF && index - 1 > start && bytes[index - 2].toInt() == CR) index - 2 else index - 1
    }
    var k = codePointStartAt(bytes, start, index - 1)
    var b = classAt(bytes, k, end)
    var runStart = -1
    while (k > start) {
        val p = codePointStartAt(bytes, start, k - 1)
        val a = classAt(bytes, p, end)
        val breaks = if (a == GRAPHEME_CLASS_REGIONAL_INDICATOR && b == GRAPHEME_CLASS_REGIONAL_INDICATOR) {
            // A walk meets one run at most: before a run only Prepend joins, and before Prepend only Prepend.
            if (runStart < 0) runStart = regionalIndicatorRunStart(bytes, start, end, p)
            breaksInRegionalIndicators(runStart, k)
        } else {
            breaksBetween(bytes, start, end, p, a, b)
        }
        if (breaks) return k
        k = p
        b = a
    }
    return start
}

// True when a grapheme boundary comes between the code point at p, of class a, and the next one, of class b.
internal fun breaksBetween(bytes: ByteArray, start: Int, end: Int, p: Int, a: Int, b: Int): Boolean {
    val pair = GraphemeTables.pairs[a * GRAPHEME_CLASS_COUNT + b].code
    val row = if (pair >= PAIR_CONTEXT) rowAfter(bytes, start, end, p, a) else GraphemeTables.startStates[a].code
    return GraphemeTables.transitions[row + b].code >= BOUNDARY
}

// In a run of regional indicators, every other one starts a grapheme, and each takes 4 bytes of valid UTF-8.
internal fun breaksInRegionalIndicators(runStart: Int, k: Int): Boolean = (k - runStart) / 4 % 2 == 0

internal fun regionalIndicatorRunStart(bytes: ByteArray, stop: Int, end: Int, p: Int): Int {
    var s = p
    while (s > stop) {
        val q = codePointStartAt(bytes, stop, s - 1)
        if (classAt(bytes, q, end) != GRAPHEME_CLASS_REGIONAL_INDICATOR) break
        s = q
    }
    return s
}

// On malformed bytes, a continuation byte goes with any byte before it, so the split can differ from utf8LengthAt.
internal fun codePointStartAt(bytes: ByteArray, start: Int, i: Int): Int {
    var j = i
    while (j > start && i - j < 3 && bytes[j].toInt() and 0xC0 == 0x80) j--
    return j
}

private fun rowAfter(bytes: ByteArray, start: Int, end: Int, p: Int, cls: Int): Int {
    val classes = GraphemeTables.classes
    val classIndex = GraphemeTables.index
    val pairs = GraphemeTables.pairs
    var s = p
    var sClass = cls
    var row: Int
    while (true) {
        if (s == start || (GRAPHEME_SYNC_CLASSES ushr sClass) and 1 != 0) {
            row = GraphemeTables.startStates[sClass].code
            break
        }
        val q = codePointStartAt(bytes, start, s - 1)
        val qClass = graphemeClassAt(bytes, q, utf8LengthAt(bytes, q, end), classes, classIndex)
        val pair = pairs[qClass * GRAPHEME_CLASS_COUNT + sClass].code
        if (pair and PAIR_SAFE != 0) {
            row = pair and PAIR_ROW
            break
        }
        s = q
        sClass = qClass
    }
    val transitions = GraphemeTables.transitions
    var k = s + utf8LengthAt(bytes, s, end)
    while (k <= p) {
        val length = utf8LengthAt(bytes, k, end)
        row = transitions[row + graphemeClassAt(bytes, k, length, classes, classIndex)].code and (BOUNDARY - 1)
        k += length
    }
    return row
}

private fun classAt(bytes: ByteArray, i: Int, end: Int): Int =
    graphemeClassAt(bytes, i, utf8LengthAt(bytes, i, end), GraphemeTables.classes, GraphemeTables.index)

private fun utf8LengthAt(bytes: ByteArray, i: Int, end: Int): Int {
    val lead = bytes[i].toInt() and 0xFF
    val length = if (lead < 0xC0) 1 else if (lead < 0xE0) 2 else if (lead < 0xF0) 3 else 4
    return if (length <= end - i) length else end - i
}

// The flags of GRAPHEME_PAIR_ROWS, and the mask of the row after a safe pair.
private const val PAIR_CONTEXT = 0x8000
private const val PAIR_SAFE = 0x4000
private const val PAIR_ROW = 0x3FFF
