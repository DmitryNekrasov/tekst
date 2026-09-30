/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.generator

import kotlin.random.Random

// The minimized automaton of the rules. For state s and class c, transitions[s * classCount + c] is the next state, or
// BOUNDARY plus the start state of c when a cluster boundary comes before c.
class BreakAutomaton(val stateCount: Int, val startStates: IntArray, val transitions: IntArray) {
    val classCount: Int get() = startStates.size

    // Whether a boundary comes before each position of classes, from 0 to classes.size.
    fun boundaries(classes: List<Int>): List<Boolean> {
        if (classes.isEmpty()) return listOf(true)
        val result = arrayListOf(true)
        var state = startStates[classes[0]]
        for (i in 1..<classes.size) {
            val transition = transitions[state * classCount + classes[i]]
            result += transition >= BOUNDARY
            state = transition and (BOUNDARY - 1)
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
    // The reachable states, in the order in which a search from the start states finds them.
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

// Checks that the automaton, which forgets everything before the current cluster, finds the same boundaries as the rules
// read from the whole text: for every sequence of up to five classes and for long random ones.
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
