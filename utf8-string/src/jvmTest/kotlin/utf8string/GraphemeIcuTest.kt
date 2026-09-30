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
import kotlin.test.assertTrue
import kotlin.test.fail

// ICU4J as an independent implementation. Until it implements Unicode 18.0, the texts leave out the code points whose
// properties changed, and a difference must come from the new GB9c.
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
            breaker.setText(source)
            val icu = BooleanArray(codePoints.size + 1)
            var boundary = breaker.first()
            while (boundary != BreakIterator.DONE) {
                icu[source.codePointCount(0, boundary)] = true
                boundary = breaker.next()
            }
            val string = source.u8
            val matches = byteBoundaries(codePoints, icu) == string.iteratedBoundaries()
            if (!matches) {
                val message = codePoints.joinToString(" ") { it.toString(16) }
                assertTrue(older && icu.contentEquals(GraphemeModel.boundaries(codePoints, legacyConjuncts = true)), message)
                newRuleDifferences++
            }
        }
        // The texts are random enough to meet the changed rule.
        if (older) assertTrue(newRuleDifferences > 0)
    }

    // "Grapheme_Cluster_Break Extended_Pictographic Indic_Conjunct_Break" in ICU, as in GRAPHEME_CLASS_PROPERTIES.
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
