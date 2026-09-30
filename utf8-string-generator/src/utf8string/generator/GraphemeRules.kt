/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.generator

import utf8string.generator.GraphemeClass.CR
import utf8string.generator.GraphemeClass.ExtendedPictographic
import utf8string.generator.GraphemeClass.L
import utf8string.generator.GraphemeClass.LF
import utf8string.generator.GraphemeClass.LV
import utf8string.generator.GraphemeClass.LVT
import utf8string.generator.GraphemeClass.Prepend
import utf8string.generator.GraphemeClass.RegionalIndicator
import utf8string.generator.GraphemeClass.SpacingMark
import utf8string.generator.GraphemeClass.T
import utf8string.generator.GraphemeClass.V
import utf8string.generator.GraphemeClass.ZWJ

// Code points that the rules cannot tell apart: one combination of Grapheme_Cluster_Break, Extended_Pictographic and
// Indic_Conjunct_Break. The ordinal is the class number in the generated tables, and Other must stay 0.
enum class GraphemeClass(val clusterBreak: String, val extendedPictographic: Boolean, val conjunctBreak: String) {
    Other("Other", false, "None"),
    CR("CR", false, "None"),
    LF("LF", false, "None"),
    Control("Control", false, "None"),
    Extend("Extend", false, "Extend"),
    ExtendLinker("Extend", false, "Linker"),
    ExtendNotConjunct("Extend", false, "None"),
    Linker("Other", false, "Linker"),
    Consonant("Other", false, "Consonant"),
    ExtendedPictographic("Other", true, "None"),
    ZWJ("ZWJ", false, "Extend"),
    RegionalIndicator("Regional_Indicator", false, "None"),
    Prepend("Prepend", false, "None"),
    SpacingMark("SpacingMark", false, "None"),
    L("L", false, "None"),
    V("V", false, "None"),
    T("T", false, "None"),
    LV("LV", false, "None"),
    LVT("LVT", false, "None");

    val isControl: Boolean get() = clusterBreak == "CR" || clusterBreak == "LF" || clusterBreak == "Control"
    val isExtend: Boolean get() = clusterBreak == "Extend"
    val isConjunctLinker: Boolean get() = conjunctBreak == "Linker"
    val isConjunctExtend: Boolean get() = conjunctBreak == "Extend"
    val isConjunctConsonant: Boolean get() = conjunctBreak == "Consonant"
}

// The class number of every code point. Fails on a combination of properties that no class covers: a new Unicode
// version may need a new class and a review of the rules.
fun classify(properties: GraphemeProperties): IntArray {
    val byProperties = GraphemeClass.entries.associateBy { Triple(it.clusterBreak, it.extendedPictographic, it.conjunctBreak) }
    return IntArray(CODE_POINT_LIMIT) { codePoint ->
        val key = Triple(
            properties.clusterBreak[codePoint],
            properties.extendedPictographic[codePoint],
            properties.conjunctBreak[codePoint],
        )
        (byProperties[key] ?: error("U+%04X has no grapheme class for %s".format(codePoint, key))).ordinal
    }
}

// What the rules need to know about the cluster so far. The rules never look before the start of the current cluster,
// so a cluster starts from startState of its first code point.
// emoji: 1 after ExtPict Extend*, 2 after ExtPict Extend* ZWJ, 0 otherwise (GB11).
// afterLinker: after InCB=Linker InCB=Extend* (GB9c).
data class RuleState(
    val previous: GraphemeClass,
    val oddRegionalIndicators: Boolean,
    val emoji: Int,
    val afterLinker: Boolean,
)

fun startState(first: GraphemeClass): RuleState =
    RuleState(first, first == RegionalIndicator, if (first == ExtendedPictographic) 1 else 0, first.isConjunctLinker)

// UAX #29 revision 49 (Unicode 18.0), rules GB3 to GB999: whether a cluster boundary comes before next.
fun isBoundary(state: RuleState, next: GraphemeClass): Boolean {
    val previous = state.previous
    return when {
        previous == CR && next == LF -> false // GB3
        previous.isControl -> true // GB4
        next.isControl -> true // GB5
        previous == L && (next == L || next == V || next == LV || next == LVT) -> false // GB6
        (previous == LV || previous == V) && (next == V || next == T) -> false // GB7
        (previous == LVT || previous == T) && next == T -> false // GB8
        next.isExtend || next == ZWJ -> false // GB9
        next == SpacingMark -> false // GB9a
        previous == Prepend -> false // GB9b
        state.afterLinker && next.isConjunctConsonant -> false // GB9c
        state.emoji == 2 && next == ExtendedPictographic -> false // GB11
        previous == RegionalIndicator && state.oddRegionalIndicators && next == RegionalIndicator -> false // GB12, GB13
        else -> true // GB999
    }
}

// The state after next joins the cluster.
fun append(state: RuleState, next: GraphemeClass): RuleState = RuleState(
    previous = next,
    oddRegionalIndicators = next == RegionalIndicator &&
            !(state.previous == RegionalIndicator && state.oddRegionalIndicators),
    emoji = when {
        next == ExtendedPictographic -> 1
        next.isExtend && state.emoji == 1 -> 1
        next == ZWJ && state.emoji == 1 -> 2
        else -> 0
    },
    afterLinker = next.isConjunctLinker || next.isConjunctExtend && state.afterLinker,
)

// The same rules read directly from the text, without the forward state: whether a boundary comes between
// text[position - 1] and text[position]. The generator checks the automaton against it.
fun isBoundaryInText(text: List<GraphemeClass>, position: Int): Boolean {
    val previous = text[position - 1]
    val next = text[position]
    return when {
        previous == CR && next == LF -> false
        previous.isControl -> true
        next.isControl -> true
        previous == L && (next == L || next == V || next == LV || next == LVT) -> false
        (previous == LV || previous == V) && (next == V || next == T) -> false
        (previous == LVT || previous == T) && next == T -> false
        next.isExtend || next == ZWJ -> false
        next == SpacingMark -> false
        previous == Prepend -> false
        next.isConjunctConsonant && text.skipBack(position - 1) { it.isConjunctExtend }?.isConjunctLinker == true -> false
        next == ExtendedPictographic && previous == ZWJ &&
                text.skipBack(position - 2) { it.isExtend } == ExtendedPictographic -> false
        previous == RegionalIndicator && next == RegionalIndicator &&
                (position - 1 downTo 0).takeWhile { text[it] == RegionalIndicator }.size % 2 == 1 -> false
        else -> true
    }
}

// The class at the last index up to from whose class does not satisfy skip, or null if there is none.
private fun List<GraphemeClass>.skipBack(from: Int, skip: (GraphemeClass) -> Boolean): GraphemeClass? {
    var i = from
    while (i >= 0 && skip(this[i])) i--
    return getOrNull(i)
}
