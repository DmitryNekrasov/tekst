/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.generator

import kotlin.random.Random

// The tables that find a cluster boundary at any index without reading the text from its start. For a sync class, the
// state after a code point of the class does not depend on the text before it, and for a safe pair of classes, the
// state after the pair does not. So to learn the state at an index, a query walks back to such a code point or pair
// and runs the automaton forward from there. For a context pair, whether a boundary comes between the two code points
// depends on the text before them, and for any other pair the two classes decide it.
class BoundaryTables(
    val sync: BooleanArray,
    val context: BooleanArray,
    // For the pair a * classCount + b, the state after b when it does not depend on the text before a, else -1.
    val safeStates: IntArray,
)

fun buildBoundaryTables(automaton: BreakAutomaton): BoundaryTables {
    val classCount = automaton.classCount
    val after = statesAfter(automaton)
    return BoundaryTables(
        sync = BooleanArray(classCount) { after[it].size == 1 },
        context = BooleanArray(classCount * classCount) { pair ->
            after[pair / classCount].map { automaton.breaksBefore(it, pair % classCount) }.toSet().size == 2
        },
        safeStates = IntArray(classCount * classCount) { pair ->
            after[pair / classCount].map { automaton.nextState(it, pair % classCount) }.toSet().singleOrNull() ?: -1
        },
    )
}

// For each class, the states after a code point of the class in any text: the start state of the class, when the text
// starts with it, and the state that each reachable state goes to with it.
private fun statesAfter(automaton: BreakAutomaton): List<Set<Int>> {
    val reachable = HashSet<Int>()
    val queue = ArrayDeque(automaton.startStates.toList())
    while (queue.isNotEmpty()) {
        val state = queue.removeFirst()
        if (reachable.add(state)) for (next in 0..<automaton.classCount) queue += automaton.nextState(state, next)
    }
    return List(automaton.classCount) { cls ->
        reachable.mapTo(hashSetOf(automaton.startStates[cls])) { automaton.nextState(it, cls) }
    }
}

// The conditions that GraphemeBoundaries.kt and GraphemeIterator.previous rely on.
fun verifyBoundaryTables(classes: IntArray, automaton: BreakAutomaton, tables: BoundaryTables) {
    val classCount = automaton.classCount
    val start = automaton.startStates
    val names = GraphemeClass.entries
    val ri = GraphemeClass.RegionalIndicator.ordinal
    for (pair in 0..<classCount * classCount) {
        // The walk back of one query then ends where the next context pair starts, so a walk over a text in one
        // direction reads each code point a bounded number of times, except in a run of regional indicators.
        check(!tables.context[pair] || tables.sync[pair % classCount] || pair == ri * classCount + ri) {
            "The context pair ${names[pair / classCount]} ${names[pair % classCount]} ends in a class that is not sync"
        }
    }
    // In a run of regional indicators, a boundary comes before one when an even number of them come before it in the
    // run. Each takes 4 bytes of UTF-8, so the byte count of the run gives the number.
    val odd = start[ri]
    check(!automaton.breaksBefore(odd, ri) && automaton.breaksBefore(automaton.nextState(odd, ri), ri)) {
        "The parity does not decide the boundaries in a run of regional indicators"
    }
    for (cls in 0..<classCount) {
        check(cls == ri || tables.safeStates[cls * classCount + ri] == odd) {
            "A run of regional indicators after ${names[cls]} does not start with the odd state"
        }
    }
    for (codePoint in 0..<0x10000) {
        check(classes[codePoint] != ri) { "U+%04X is a regional indicator below U+10000".format(codePoint) }
    }
    // The ASCII fast paths: between 2 ASCII chars the classes decide, and a boundary comes before CR after anything.
    for (ascii in 0..<0x80) check(tables.sync[classes[ascii]]) { "U+%04X is not sync".format(ascii) }
    for (state in 0..<automaton.stateCount) {
        check(automaton.breaksBefore(state, GraphemeClass.CR.ordinal)) { "CR joins the state $state" }
    }
    verifyRandomAccess(automaton, tables)
}

// The functions of GraphemeBoundaries.kt and GraphemeIterator.previous in a text of classes, one unit per code point,
// against the automaton on every text of up to 5 classes and on random texts with long runs.
private fun verifyRandomAccess(automaton: BreakAutomaton, tables: BoundaryTables) {
    val model = RandomAccessModel(automaton, tables)
    val names = GraphemeClass.entries
    fun verify(text: IntArray, size: Int): List<Int> {
        val expected = automaton.boundaries(text.asList().subList(0, size))
        for (i in 0..size) {
            check(
                model.isBoundary(text, size, i) == expected[i] &&
                        model.next(text, size, i) == ((i + 1..size).firstOrNull { expected[it] } ?: -1) &&
                        model.previous(text, i) == ((i - 1 downTo 0).firstOrNull { expected[it] } ?: -1)
            ) {
                "Random access disagrees with the automaton at $i of ${text.take(size).map { names[it] }}"
            }
        }
        return expected.indices.filter { expected[it] }
    }

    val text = IntArray(5)
    fun all(size: Int) {
        if (size > 0) verify(text, size)
        if (size < text.size) for (next in 0..<automaton.classCount) {
            text[size] = next
            all(size + 1)
        }
    }
    all(0)

    val random = Random(19)
    val classes = GraphemeClass.entries.indices.toList()
    repeat(20_000) {
        // Few classes per text, and regional indicators in half of them, so that long runs occur.
        val alphabet = List(random.nextInt(1, 5)) { classes.random(random) } +
                if (random.nextBoolean()) listOf(GraphemeClass.RegionalIndicator.ordinal) else emptyList()
        val randomText = IntArray(random.nextInt(1, 60)) { alphabet.random(random) }
        val boundaries = verify(randomText, randomText.size)
        val index = random.nextInt(randomText.size + 1)
        val cursor = CursorModel(model, randomText, index)
        var at = boundaries.indexOfLast { it <= index }
        repeat(100) {
            val forward = random.nextBoolean()
            val moved = if (forward) cursor.next() else cursor.previous()
            val expected = if (forward) boundaries.getOrNull(at + 1) else boundaries.getOrNull(at - 1)
            if (expected != null) at += if (forward) 1 else -1
            check(moved == (expected ?: -1) && cursor.position == boundaries[at]) {
                "The iterator disagrees with the automaton at $at of ${randomText.map { names[it] }}"
            }
        }
    }
}

// The functions of GraphemeBoundaries.kt.
private class RandomAccessModel(val automaton: BreakAutomaton, private val tables: BoundaryTables) {
    private val classCount = automaton.classCount
    private val ri = GraphemeClass.RegionalIndicator.ordinal

    fun stateAfter(text: IntArray, p: Int): Int {
        var s = p
        var state: Int
        while (true) {
            if (s == 0 || tables.sync[text[s]]) {
                state = automaton.startStates[text[s]]
                break
            }
            val safe = tables.safeStates[text[s - 1] * classCount + text[s]]
            if (safe >= 0) {
                state = safe
                break
            }
            s--
        }
        for (k in s + 1..p) state = automaton.nextState(state, text[k])
        return state
    }

    fun breaksBetween(text: IntArray, p: Int): Boolean {
        val pair = text[p] * classCount + text[p + 1]
        val state = if (tables.context[pair]) stateAfter(text, p) else automaton.startStates[text[p]]
        return automaton.breaksBefore(state, text[p + 1])
    }

    fun isBoundary(text: IntArray, size: Int, i: Int): Boolean = i == 0 || i == size || breaksBetween(text, i - 1)

    fun next(text: IntArray, size: Int, i: Int): Int {
        if (i == size) return -1
        var state = stateAfter(text, i)
        for (k in i + 1..<size) {
            if (automaton.breaksBefore(state, text[k])) return k
            state = automaton.nextState(state, text[k])
        }
        return size
    }

    fun previous(text: IntArray, i: Int): Int {
        if (i == 0) return -1
        var runStart = -1
        var k = i - 1
        while (k > 0) {
            val p = k - 1
            val breaks = if (isRegionalIndicatorPair(text, p)) {
                if (runStart < 0) runStart = regionalIndicatorRunStart(text, p, 0)
                (k - runStart) % 2 == 0
            } else {
                breaksBetween(text, p)
            }
            if (breaks) return k
            k = p
        }
        return 0
    }

    fun isRegionalIndicatorPair(text: IntArray, p: Int): Boolean = text[p] == ri && text[p + 1] == ri

    // The start of the run of regional indicators that has the one at p, or stop when the run goes on before stop.
    fun regionalIndicatorRunStart(text: IntArray, p: Int, stop: Int): Int {
        var s = p
        while (s > stop && text[s - 1] == ri) s--
        return s
    }
}

// GraphemeIterator: next runs the automaton, and previous keeps the last run of regional indicators that it walked.
private class CursorModel(private val model: RandomAccessModel, private val text: IntArray, index: Int) {
    var position: Int = if (index == text.size) index else model.previous(text, index + 1)
        private set
    private var runStart = -1
    private var runEnd = -1

    fun next(): Int {
        if (position == text.size) return -1
        val automaton = model.automaton
        var state = automaton.startStates[text[position]]
        var k = position + 1
        while (k < text.size && !automaton.breaksBefore(state, text[k])) state = automaton.nextState(state, text[k++])
        position = k
        return k
    }

    fun previous(): Int {
        if (position == 0) return -1
        var k = position - 1
        while (k > 0) {
            val p = k - 1
            val breaks = if (model.isRegionalIndicatorPair(text, p)) {
                (k - runStartOf(p)) % 2 == 0
            } else {
                model.breaksBetween(text, p)
            }
            if (breaks) break
            k = p
        }
        position = k
        return k
    }

    // A scan back from p that reaches the end of the last run continues that run.
    private fun runStartOf(p: Int): Int {
        if (p in runStart..<runEnd) return runStart
        val stop = if (runStart >= 0 && p >= runEnd) runEnd else 0
        val s = model.regionalIndicatorRunStart(text, p, stop)
        runEnd = p + 1
        if (s == stop && stop > 0) return runStart
        runStart = s
        return s
    }
}
