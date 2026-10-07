/*
 * Copyright 2026 Dmitry Nekrasov and tekst library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tekst

import kotlin.test.Test
import kotlin.test.assertEquals

// The tables of random access against the model, which has no automaton.
class GraphemeBoundaryTablesTest {
    private val count = GRAPHEME_CLASS_COUNT
    private val codePoints = IntArray(count) { GraphemeModel.runStarts[GraphemeModel.runClasses.indexOf(it)] }

    @Test
    fun tableShape() {
        assertEquals(count * count, GraphemeTables.pairs.size)
        assertEquals(0, GRAPHEME_SYNC_CLASSES ushr count)
        assertEquals(GRAPHEME_CLASS_REGIONAL_INDICATOR, GraphemeModel.classOf(0x1F1E6))
    }

    @Test
    fun contextPairs() {
        for (a in 0..<count) {
            for (b in 0..<count) {
                val alone = boundaries(a, b)[1]
                val context = (0..<count).any { boundaries(it, a, b)[2] != alone }
                assertEquals(context, GraphemeTables.pairs[a * count + b].code >= 0x8000, "classes $a and $b")
            }
        }
    }

    @Test
    fun syncClasses() {
        for (cls in 0..<count) {
            var context = false
            for (next in 0..<count) {
                for (last in 0..<count) {
                    val alone = boundaries(cls, next, last)
                    context = context || (0..<count).any {
                        val after = boundaries(it, cls, next, last)
                        after[2] != alone[1] || after[3] != alone[2]
                    }
                }
            }
            assertEquals(!context, (GRAPHEME_SYNC_CLASSES ushr cls) and 1 != 0, "class $cls")
        }
    }

    private fun boundaries(vararg classes: Int): BooleanArray =
        GraphemeModel.boundaries(IntArray(classes.size) { codePoints[classes[it]] })
}
