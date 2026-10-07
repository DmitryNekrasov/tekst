/*
 * Copyright 2026 Dmitry Nekrasov and tekst library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tekst

// The kernels of GraphemeBoundaries.kt for the UTF-16 text in text[start..<end], at char indices. A change to the rules
// of one file must go into the other.
//
// A high surrogate and the low surrogate after it, both in the range, are one code point. Every other char is one code
// point, and a lone surrogate has the class of U+FFFD, which .u8 writes for it, so the boundaries of a String are those
// of its Utf8String. When the range cuts a pair, the part in the range is a lone surrogate, so a query reads no char
// outside the range.

internal fun isGraphemeBoundaryAt(text: CharSequence, start: Int, end: Int, index: Int): Boolean {
    if (index == start || index == end) return true
    val next = text[index]
    val previous = text[index - 1]
    // Between 2 ASCII chars, only CR LF joins.
    if (previous < '\u0080' && next < '\u0080') return previous.code != CR || next.code != LF
    if (next.isLowSurrogate() && previous.isHighSurrogate()) return false
    val p = codePointStartAt(text, start, index - 1)
    return breaksBetween(text, start, end, p, classAt(text, p, end), classAt(text, index, end))
}

internal fun nextGraphemeBoundaryAt(text: CharSequence, start: Int, end: Int, index: Int): Int {
    if (index == end) return -1
    val first = text[index]
    if (first < '\u0080') {
        // The fast paths of the byte kernel, with U+0300 for its lead byte 0xCC, and with no automaton after CR, since
        // UTF-16 has no malformed bytes.
        val next = index + 1
        if (next == end) return next
        val second = text[next]
        if (first >= ' ' && first != '\u007F') {
            if (second < '\u0300') return next
        } else {
            return if (first.code == CR && second.code == LF) next + 1 else next
        }
    }
    val transitions = GraphemeTables.transitions
    val s = codePointStartAt(text, start, index)
    var length = utf16LengthAt(text, s, end)
    var row = rowAfter(text, start, end, s, utf16ClassAt(text, s, length))
    var k = s + length
    while (k < end) {
        length = utf16LengthAt(text, k, end)
        val transition = transitions[row + utf16ClassAt(text, k, length)].code
        if (transition >= BOUNDARY) return k
        row = transition and (BOUNDARY - 1)
        k += length
    }
    return end
}

internal fun previousGraphemeBoundaryAt(text: CharSequence, start: Int, end: Int, index: Int): Int {
    if (index == start) return -1
    val last = text[index - 1]
    if (last < '\u0080' && (index - 1 == start || text[index - 2] < '\u0080')) {
        // Between 2 ASCII chars only CR LF joins, and a boundary comes before CR after anything.
        return if (last.code == LF && index - 1 > start && text[index - 2].code == CR) index - 2 else index - 1
    }
    var k = codePointStartAt(text, start, index - 1)
    var b = classAt(text, k, end)
    var runStart = -1
    while (k > start) {
        val p = codePointStartAt(text, start, k - 1)
        val a = classAt(text, p, end)
        val breaks = if (a == GRAPHEME_CLASS_REGIONAL_INDICATOR && b == GRAPHEME_CLASS_REGIONAL_INDICATOR) {
            // A walk meets one run at most: before a run only Prepend joins, and before Prepend only Prepend.
            if (runStart < 0) runStart = regionalIndicatorRunStart(text, start, end, p)
            // Every other regional indicator starts a grapheme, and each takes 2 chars.
            (k - runStart) / 2 % 2 == 0
        } else {
            breaksBetween(text, start, end, p, a, b)
        }
        if (breaks) return k
        k = p
        b = a
    }
    return start
}

// True when a grapheme boundary comes between the code point at p, of class a, and the next one, of class b.
private fun breaksBetween(text: CharSequence, start: Int, end: Int, p: Int, a: Int, b: Int): Boolean {
    val pair = GraphemeTables.pairs[a * GRAPHEME_CLASS_COUNT + b].code
    val row = if (pair >= PAIR_CONTEXT) rowAfter(text, start, end, p, a) else GraphemeTables.startStates[a].code
    return GraphemeTables.transitions[row + b].code >= BOUNDARY
}

private fun regionalIndicatorRunStart(text: CharSequence, stop: Int, end: Int, p: Int): Int {
    var s = p
    while (s > stop) {
        val q = codePointStartAt(text, stop, s - 1)
        if (classAt(text, q, end) != GRAPHEME_CLASS_REGIONAL_INDICATOR) break
        s = q
    }
    return s
}

private fun codePointStartAt(text: CharSequence, start: Int, i: Int): Int =
    if (i > start && text[i].isLowSurrogate() && text[i - 1].isHighSurrogate()) i - 1 else i

private fun rowAfter(text: CharSequence, start: Int, end: Int, p: Int, cls: Int): Int {
    val pairs = GraphemeTables.pairs
    var s = p
    var sClass = cls
    var row: Int
    while (true) {
        if (s == start || (GRAPHEME_SYNC_CLASSES ushr sClass) and 1 != 0) {
            row = GraphemeTables.startStates[sClass].code
            break
        }
        val q = codePointStartAt(text, start, s - 1)
        val qClass = classAt(text, q, end)
        val pair = pairs[qClass * GRAPHEME_CLASS_COUNT + sClass].code
        if (pair and PAIR_SAFE != 0) {
            row = pair and PAIR_ROW
            break
        }
        s = q
        sClass = qClass
    }
    val transitions = GraphemeTables.transitions
    var k = s + utf16LengthAt(text, s, end)
    while (k <= p) {
        val length = utf16LengthAt(text, k, end)
        row = transitions[row + utf16ClassAt(text, k, length)].code and (BOUNDARY - 1)
        k += length
    }
    return row
}

private fun classAt(text: CharSequence, i: Int, end: Int): Int = utf16ClassAt(text, i, utf16LengthAt(text, i, end))

private fun utf16LengthAt(text: CharSequence, i: Int, end: Int): Int =
    if (text[i].isHighSurrogate() && i + 1 < end && text[i + 1].isLowSurrogate()) 2 else 1

private fun utf16ClassAt(text: CharSequence, i: Int, length: Int): Int {
    val char = text[i]
    val codePoint = when {
        length == 2 -> 0x10000 + ((char.code - 0xD800) shl 10) + (text[i + 1].code - 0xDC00)
        char.isSurrogate() -> REPLACEMENT_CHARACTER
        else -> char.code
    }
    return graphemeClassOf(codePoint, GraphemeTables.classes, GraphemeTables.index)
}

private const val REPLACEMENT_CHARACTER = 0xFFFD
