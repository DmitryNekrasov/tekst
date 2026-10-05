/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import com.ibm.icu.lang.UCharacter
import com.ibm.icu.lang.UProperty
import com.ibm.icu.text.BreakIterator
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class GraphemeIcuTest {
    private val icuVersion = UCharacter.getUnicodeVersion().major
    private val tablesVersion = UNICODE_VERSION.substringBefore('.').toInt()

    @Test
    fun onlyTheListedCodePointsChangedSinceUnicode17() {
        if (icuVersion != 17) return
        val classProperties = GRAPHEME_CLASS_PROPERTIES.lines()
        for (codePoint in 0..0x10FFFF) {
            val icu = icuProperties(codePoint)
            val ours = classProperties[GraphemeModel.classOf(codePoint)]
            if (isChangedSinceUnicode17(codePoint) != (icu != ours)) fail("U+%04X: ICU %s, tables %s".format(codePoint, icu, ours))
        }
    }

    @Test
    fun iterationMatchesIcuOnRandomTexts() {
        val older = icuVersion < tablesVersion
        assertTrue(icuVersion == tablesVersion || older && icuVersion == 17, "Unexpected ICU Unicode version $icuVersion")
        val corpus = GraphemeCorpus(excluded = if (older) CHANGED_SINCE_UNICODE_17 else emptyList())
        val breaker = BreakIterator.getCharacterInstance()
        val random = Random(31)
        var newRuleDifferences = 0
        repeat(RANDOM_TEXT_COUNT) {
            val codePoints = corpus.text(random)
            val source = codePointsToString(codePoints)
            val icu = icuBoundaries(breaker, source, codePoints.size)
            if (checkAgainstOracle(codePoints, source.u8.iteratedBoundaries(), icu, older)) newRuleDifferences++
        }
        if (older) assertTrue(newRuleDifferences > 0)
    }

    // ICU does not round an index inside a surrogate pair to a code point, so the test asks only between code points.
    @Test
    fun randomAccessMatchesIcuOnRandomTexts() {
        val older = icuVersion < tablesVersion
        val corpus = GraphemeCorpus(excluded = if (older) CHANGED_SINCE_UNICODE_17 else emptyList())
        val breaker = BreakIterator.getCharacterInstance()
        val random = Random(39)
        repeat(RANDOM_TEXT_COUNT / 10) {
            val codePoints = corpus.text(random)
            val source = codePointsToString(codePoints)
            val string = source.u8
            // The texts where only GB9c of Unicode 18 differs are for iterationMatchesIcuOnRandomTexts.
            val icu = icuBoundaries(breaker, source, codePoints.size)
            if (checkAgainstOracle(codePoints, string.iteratedBoundaries(), icu, older)) return@repeat
            val charIndices = IntArray(codePoints.size + 1)
            val byteIndices = IntArray(source.length + 1)
            for ((i, codePoint) in codePoints.withIndex()) {
                charIndices[i + 1] = charIndices[i] + Character.charCount(codePoint)
                byteIndices[charIndices[i + 1]] = byteIndices[charIndices[i]] + utf8Length(codePoint)
            }
            fun byteIndex(charIndex: Int): Int = if (charIndex == BreakIterator.DONE) -1 else byteIndices[charIndex]
            for (charIndex in charIndices) {
                val index = byteIndices[charIndex]
                val message = "$index of ${codePoints.toHex()}"
                assertEquals(breaker.isBoundary(charIndex), string.isGraphemeBoundary(index), "isBoundary($message)")
                assertEquals(
                    byteIndex(breaker.following(charIndex)),
                    string.nextGraphemeBoundary(index),
                    "next($message)",
                )
                assertEquals(
                    byteIndex(breaker.preceding(charIndex)),
                    string.previousGraphemeBoundary(index),
                    "previous($message)",
                )
            }
        }
    }

    // The boundaries of ICU, one for each index between code points.
    private fun icuBoundaries(breaker: BreakIterator, source: String, codePointCount: Int): BooleanArray {
        breaker.setText(source)
        val icu = BooleanArray(codePointCount + 1)
        var boundary = breaker.first()
        while (boundary != BreakIterator.DONE) {
            icu[source.codePointCount(0, boundary)] = true
            boundary = breaker.next()
        }
        return icu
    }

    private fun icuProperties(codePoint: Int): String {
        fun name(property: Int): String = UCharacter.getPropertyValueName(
            property,
            UCharacter.getIntPropertyValue(codePoint, property),
            UProperty.NameChoice.LONG,
        )
        val pictographic = UCharacter.hasBinaryProperty(codePoint, UProperty.EXTENDED_PICTOGRAPHIC)
        return "${name(UProperty.GRAPHEME_CLUSTER_BREAK)} $pictographic ${name(UProperty.INDIC_CONJUNCT_BREAK)}"
    }
}
