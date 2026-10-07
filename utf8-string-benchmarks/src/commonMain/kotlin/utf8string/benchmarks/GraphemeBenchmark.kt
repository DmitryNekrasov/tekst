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
import utf8string.Grapheme
import utf8string.Utf8String
import utf8string.nextGraphemeBoundary
import utf8string.u8

@State(Scope.Benchmark)
class GraphemeBenchmark {
    @Param("ascii", "cyrillic", "cjk", "hangul", "emoji", "indic", "zalgo", "crlf")
    var corpus: String = ""

    private var source: String = ""
    private var string: Utf8String = "".u8
    private val kept = ArrayList<Grapheme>()

    @Setup
    fun prepare() {
        source = Corpora.text(corpus)
        string = source.u8
    }

    @Benchmark
    fun iterate(): Int {
        var count = 0
        for (grapheme in string) count++
        return count
    }

    // startIndex at the last boundary keeps the walk linear.
    @Benchmark
    fun iterateString(): Int {
        var count = 0
        var index = 0
        while (index < source.length) {
            index = source.nextGraphemeBoundary(index, startIndex = index, endIndex = source.length)
            count++
        }
        return count
    }

    @Benchmark
    fun iterateAndKeep(): Int {
        kept.clear()
        for (grapheme in string) kept += grapheme
        return kept.size
    }

    @Benchmark
    fun lengthOfNewString(): Int = source.u8.length

    @Benchmark
    fun encode(): Utf8String = source.u8
}
