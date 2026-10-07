/*
 * Copyright 2026 Dmitry Nekrasov and tekst library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tekst

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
            val intl = intlBoundaries(segmenter, source, charAndByteIndices(codePoints).first)
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
            val (charIndices, byteIndices) = charAndByteIndices(codePoints)
            val intl = intlBoundaries(segmenter, source, charIndices)
            if (checkAgainstOracle(codePoints, string.iteratedBoundaries(), intl, older)) return@repeat
            val segments = segment(segmenter, source)
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

    @Test
    fun charIndicesMatchIntlSegmenterOnRandomTexts() {
        val version = nodeUnicodeVersion() ?: return
        val older = version < UNICODE_VERSION.substringBefore('.').toInt()
        val corpus = GraphemeCorpus(excluded = if (older) CHANGED_SINCE_UNICODE_17 else emptyList())
        val segmenter = graphemeSegmenter()
        val random = Random(46)
        repeat(RANDOM_TEXT_COUNT / 10) {
            val codePoints = corpus.text(random)
            val source = codePointsToString(codePoints)
            val intl = intlBoundaries(segmenter, source, charAndByteIndices(codePoints).first)
            if (checkAgainstOracle(codePoints, source.u8.iteratedBoundaries(), intl, older)) return@repeat
            val segments = segment(segmenter, source)
            for (index in 0..source.length) {
                val message = "$index of ${codePoints.toHex()}"
                val isBoundary = index == source.length || containingStart(segments, index) == index
                assertEquals(isBoundary, source.isGraphemeBoundary(index), "isBoundary($message)")
                val end = if (index == source.length) -1 else containingEnd(segments, index)
                assertEquals(end, source.nextGraphemeBoundary(index), "next($message)")
                val previous = if (index == 0) -1 else containingStart(segments, index - 1)
                assertEquals(previous, source.previousGraphemeBoundary(index), "previous($message)")
            }
        }
    }
}

private fun nodeUnicodeVersion(): Int? {
    val version: String? = js("typeof process !== 'undefined' && process.versions && process.versions.unicode || null")
    return version?.substringBefore('.')?.toInt()
}

private fun graphemeSegmenter(): dynamic = js("new Intl.Segmenter(undefined, { granularity: 'grapheme' })")

private fun intlBoundaries(segmenter: dynamic, source: String, charIndices: IntArray): BooleanArray {
    val codePointAt = IntArray(source.length + 1)
    for ((i, charIndex) in charIndices.withIndex()) codePointAt[charIndex] = i
    val boundaries = BooleanArray(charIndices.size)
    for (index in segmentStarts(segmenter, source)) boundaries[codePointAt[index]] = true
    boundaries[charIndices.size - 1] = true
    return boundaries
}

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
