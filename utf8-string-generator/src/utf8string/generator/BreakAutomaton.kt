/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.generator

import kotlin.random.Random

// transitions[s * classCount + c] is the next state, or BOUNDARY + the start state of c when c starts a cluster.
class BreakAutomaton(val stateCount: Int, val startStates: IntArray, val transitions: IntArray) {
    val classCount: Int get() = startStates.size

    fun nextState(state: Int, next: Int): Int = transitions[state * classCount + next] and (BOUNDARY - 1)

    fun breaksBefore(state: Int, next: Int): Boolean = transitions[state * classCount + next] >= BOUNDARY

    fun boundaries(classes: List<Int>): List<Boolean> {
        if (classes.isEmpty()) return listOf(true)
        val result = arrayListOf(true)
        var state = startStates[classes[0]]
        for (i in 1..<classes.size) {
            result += breaksBefore(state, classes[i])
            state = nextState(state, classes[i])
        }
        result += true
        return result
    }

    companion object {
        const val BOUNDARY: Int = 0x80
    }
}

fun buildBreakAutomaton(): BreakAutomaton {
    val classes = GraphemeClass.entries
    val states = ArrayList<RuleState>()
    val ids = HashMap<RuleState, Int>()
    fun id(state: RuleState): Int = ids.getOrPut(state) { states += state; states.size - 1 }
    classes.forEach { id(startState(it)) }
    var searched = 0
    while (searched < states.size) {
        val state = states[searched++]
        for (next in classes) if (!isBoundary(state, next)) id(append(state, next))
    }

    // Moore's algorithm: states are equivalent when they have the same boundaries and equivalent next states.
    var blocks = IntArray(states.size)
    var blockCount = 0
    while (true) {
        val keys = HashMap<List<Int>, Int>()
        val refined = IntArray(states.size) { s ->
            val key = listOf(blocks[s]) + classes.map { next ->
                if (isBoundary(states[s], next)) -1 else blocks[id(append(states[s], next))]
            }
            keys.getOrPut(key) { keys.size }
        }
        blocks = refined
        if (keys.size == blockCount) break
        blockCount = keys.size
    }

    // Number the minimized states in the order of their first state, so that the output is stable.
    val numbers = HashMap<Int, Int>()
    val representatives = ArrayList<RuleState>()
    states.forEachIndexed { s, state -> numbers.getOrPut(blocks[s]) { representatives += state; numbers.size } }
    fun number(state: RuleState): Int = numbers.getValue(blocks[id(state)])
    check(numbers.size < BreakAutomaton.BOUNDARY) { "Too many states: ${numbers.size}" }

    val startStates = IntArray(classes.size) { number(startState(classes[it])) }
    val transitions = IntArray(numbers.size * classes.size) { i ->
        val state = representatives[i / classes.size]
        val next = classes[i % classes.size]
        if (isBoundary(state, next)) BreakAutomaton.BOUNDARY + startStates[next.ordinal] else number(append(state, next))
    }
    return BreakAutomaton(numbers.size, startStates, transitions)
}

// The automaton forgets everything before the current cluster, so check it against the rules read from the whole text.
fun verifyAutomaton(automaton: BreakAutomaton) {
    val classes = GraphemeClass.entries
    fun verify(text: List<GraphemeClass>) {
        val expected = listOf(true) + (1..<text.size).map { isBoundaryInText(text, it) } + true
        check(automaton.boundaries(text.map { it.ordinal }) == expected) { "The automaton disagrees with the rules on $text" }
    }
    fun all(prefix: List<GraphemeClass>, length: Int) {
        if (prefix.isNotEmpty()) verify(prefix)
        if (prefix.size < length) for (next in classes) all(prefix + next, length)
    }
    all(emptyList(), 5)
    val random = Random(18)
    repeat(200_000) {
        // Few classes per text, so that long runs of regional indicators, extenders and linkers occur.
        val alphabet = List(random.nextInt(1, 5)) { classes.random(random) }
        verify(List(random.nextInt(1, 40)) { alphabet.random(random) })
    }
}

// The conditions that the ASCII fast paths of GraphemeIterator, GraphemeBoundaries.kt and GraphemeBoundariesUtf16.kt
// rely on.
fun verifyAsciiFastPaths(classes: IntArray, automaton: BreakAutomaton) {
    fun joins(first: Int, nextClass: Int): Boolean =
        !automaton.breaksBefore(automaton.startStates[classes[first]], nextClass)
    for (ascii in 0x20..0x7E) {
        check(classes[ascii] == GraphemeClass.Other.ordinal) { "U+%04X is not Other".format(ascii) }
    }
    for (next in 0..<0x300) {
        check(!joins('a'.code, classes[next])) {
            "U+%04X joins printable ASCII: lower the bound U+0300, which is the lead byte 0xCC in UTF-8".format(next)
        }
    }
    for (control in (0..<0x20) + 0x7F) {
        for (next in GraphemeClass.entries) {
            val expected = control == 0x0D && next == GraphemeClass.LF
            check(joins(control, next.ordinal) == expected) {
                "U+%04X before %s breaks the control fast path".format(control, next)
            }
        }
    }
    for (codePoint in classes.indices) {
        val isLf = classes[codePoint] == GraphemeClass.LF.ordinal
        check(isLf == (codePoint == 0x0A)) { "U+%04X is not the only LF".format(codePoint) }
    }
}
