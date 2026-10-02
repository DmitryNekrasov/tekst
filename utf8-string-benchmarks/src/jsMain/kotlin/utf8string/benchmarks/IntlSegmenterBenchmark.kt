/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.benchmarks

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

@State(Scope.Benchmark)
class IntlSegmenterBenchmark {
    @Param("ascii", "cyrillic", "cjk", "hangul", "emoji", "indic", "zalgo", "crlf")
    var corpus: String = ""

    private var source: String = ""
    private val segmenter: dynamic = js("new Intl.Segmenter(undefined, { granularity: 'grapheme' })")

    @Setup
    fun prepare() {
        source = Corpora.text(corpus)
    }

    @Benchmark
    fun intlSegmenter(): Int {
        val segments = segmentIterator(segmenter, source)
        var count = 0
        while (!segments.next().done.unsafeCast<Boolean>()) count++
        return count
    }
}

@Suppress("UNUSED_PARAMETER")
private fun segmentIterator(segmenter: dynamic, text: String): dynamic = js("segmenter.segment(text)[Symbol.iterator]()")
