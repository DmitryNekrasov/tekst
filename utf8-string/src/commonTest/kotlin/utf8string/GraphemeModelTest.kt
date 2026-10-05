/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal expect val RANDOM_TEXT_COUNT: Int

class GraphemeModelTest {
    @Test
    fun iterationMatchesTheModelOnRandomTexts() {
        val corpus = GraphemeCorpus()
        val random = Random(29)
        repeat(RANDOM_TEXT_COUNT) {
            val codePoints = corpus.text(random)
            val string = codePointsToString(codePoints).u8
            val expected = byteBoundaries(codePoints, GraphemeModel.boundaries(codePoints))
            val message = codePoints.toHex()
            assertEquals(expected, string.iteratedBoundaries(), message)
            assertEquals(expected.size - 1, string.length, message)
        }
    }

    @Test
    fun oracleCheckAcceptsOnlyTheNewConjunctRule() {
        val conjunct = intArrayOf(0x61, 0x094D, 0x0924)
        val unicode17 = booleanArrayOf(true, false, true, true)
        assertTrue(checkAgainstOracle(conjunct, listOf(0, 7), unicode17, older = true))
        assertFailsWith<AssertionError> { checkAgainstOracle(conjunct, listOf(0, 7), unicode17, older = false) }
        assertFailsWith<AssertionError> {
            checkAgainstOracle(intArrayOf(0x61, 0x62), listOf(0, 2), booleanArrayOf(true, true, true), older = true)
        }
    }
}
