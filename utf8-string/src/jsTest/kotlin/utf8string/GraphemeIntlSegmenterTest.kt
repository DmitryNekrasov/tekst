/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Browsers do not tell their Unicode version, so the test runs only on Node.
class GraphemeIntlSegmenterTest {
    @Test
    fun iterationMatchesIntlSegmenterOnRandomTexts() {
        val version = nodeUnicodeVersion() ?: return
        val tablesVersion = UNICODE_VERSION.substringBefore('.').toInt()
        val older = version < tablesVersion
        assertTrue(version == tablesVersion || older && version == 17, "Unexpected Node Unicode version $version")
        val corpus = GraphemeCorpus(excluded = if (older) CHANGED_SINCE_UNICODE_17 else emptyList())
        val segmenter = graphemeSegmenter()
        val random = Random(32)
        var newRuleDifferences = 0
        repeat(RANDOM_TEXT_COUNT) {
            val codePoints = corpus.text(random)
            val source = codePointsToString(codePoints)
            val codePointAt = IntArray(source.length + 1)
            var offset = 0
            for ((i, codePoint) in codePoints.withIndex()) {
                codePointAt[offset] = i
                offset += if (codePoint < 0x10000) 1 else 2
            }
            codePointAt[offset] = codePoints.size
            val intl = BooleanArray(codePoints.size + 1)
            for (index in segmentStarts(segmenter, source)) intl[codePointAt[index]] = true
            intl[codePoints.size] = true
            if (checkAgainstOracle(codePoints, source.u8.iteratedBoundaries(), intl, older)) newRuleDifferences++
        }
        if (older) assertTrue(newRuleDifferences > 0)
    }

    @Test
    fun randomAccessMatchesIntlSegmenterOnRandomTexts() {
        val version = nodeUnicodeVersion() ?: return
        val older = version < UNICODE_VERSION.substringBefore('.').toInt()
        val corpus = GraphemeCorpus(excluded = if (older) CHANGED_SINCE_UNICODE_17 else emptyList())
        val segmenter = graphemeSegmenter()
        val random = Random(40)
        repeat(RANDOM_TEXT_COUNT / 10) {
            val codePoints = corpus.text(random)
            val source = codePointsToString(codePoints)
            val string = source.u8
            val charIndices = IntArray(codePoints.size + 1)
            val byteIndices = IntArray(source.length + 1)
            for ((i, codePoint) in codePoints.withIndex()) {
                charIndices[i + 1] = charIndices[i] + if (codePoint < 0x10000) 1 else 2
                byteIndices[charIndices[i + 1]] = byteIndices[charIndices[i]] + utf8Length(codePoint)
            }
            val segments = segment(segmenter, source)
            // The texts where only GB9c of Unicode 18 differs are for iterationMatchesIntlSegmenterOnRandomTexts.
            val intl = BooleanArray(codePoints.size + 1) {
                it == codePoints.size || containingStart(segments, charIndices[it]) == charIndices[it]
            }
            if (checkAgainstOracle(codePoints, string.iteratedBoundaries(), intl, older)) return@repeat
            for (i in 0..<codePoints.size) {
                val charIndex = charIndices[i]
                val index = byteIndices[charIndex]
                val message = "$index of ${codePoints.toHex()}"
                val start = byteIndices[containingStart(segments, charIndex)]
                assertEquals(start, string.iterator(index).index, "iterator($message)")
                val end = byteIndices[containingEnd(segments, charIndex)]
                assertEquals(end, string.nextGraphemeBoundary(index), "next($message)")
                val previous = if (charIndex == 0) -1 else byteIndices[containingStart(segments, charIndex - 1)]
                assertEquals(previous, string.previousGraphemeBoundary(index), "previous($message)")
            }
        }
    }
}

private fun nodeUnicodeVersion(): Int? {
    val version: String? = js("typeof process !== 'undefined' && process.versions && process.versions.unicode || null")
    return version?.substringBefore('.')?.toInt()
}

private fun graphemeSegmenter(): dynamic = js("new Intl.Segmenter(undefined, { granularity: 'grapheme' })")

@Suppress("UNUSED_PARAMETER")
private fun segmentStarts(segmenter: dynamic, text: String): Array<Int> =
    js("Array.from(segmenter.segment(text), function (segment) { return segment.index })")

@Suppress("UNUSED_PARAMETER")
private fun segment(segmenter: dynamic, text: String): dynamic = js("segmenter.segment(text)")

@Suppress("UNUSED_PARAMETER")
private fun containingStart(segments: dynamic, index: Int): Int = js("segments.containing(index).index")

@Suppress("UNUSED_PARAMETER")
private fun containingEnd(segments: dynamic, index: Int): Int =
    js("(function (segment) { return segment.index + segment.segment.length })(segments.containing(index))")
