/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

// The number of random texts per test: more on the JVM, where the tests run fastest.
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
            val message = codePoints.joinToString(" ") { it.toString(16) }
            assertEquals(expected, string.iteratedBoundaries(), message)
            assertEquals(expected.size - 1, string.length, message)
        }
    }
}
