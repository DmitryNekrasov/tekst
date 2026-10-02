/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class GraphemeClassTableTest {
    @Test
    fun classOfEveryCodePoint() {
        val starts = GraphemeModel.runStarts
        for (run in starts.indices) {
            val end = if (run + 1 < starts.size) starts[run + 1] else 0x110000
            for (codePoint in starts[run]..<end) {
                val bytes = utf8Bytes(codePoint)
                val length = utf8LengthAt(bytes, 0)
                val cls = graphemeClassAt(bytes, 0, length, GraphemeTables.classes, GraphemeTables.index)
                if (length != bytes.size || cls != GraphemeModel.runClasses[run]) {
                    fail("U+${codePoint.toString(16)}: class $cls, length $length, expected ${GraphemeModel.runClasses[run]}")
                }
            }
        }
    }

    @Test
    fun tableShape() {
        assertEquals(GRAPHEME_CLASS_COUNT, GraphemeModel.classCount)
        assertEquals(GRAPHEME_CLASS_COUNT, GraphemeTables.startStates.size)
        assertEquals(0, GraphemeTables.transitions.size % GRAPHEME_CLASS_COUNT)
        assertEquals(2112, GraphemeTables.index.size)
    }
}
