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

    // The graphemes do not escape, so a JIT may remove their allocation.
    @Benchmark
    fun iterate(): Int {
        var byteCount = 0
        for (grapheme in string) byteCount += grapheme.byteCount
        return byteCount
    }

    // The graphemes escape into a list, as when code keeps them.
    @Benchmark
    fun iterateAndKeep(): Int {
        kept.clear()
        for (grapheme in string) kept += grapheme
        return kept.size
    }

    // A new string does not have its length cached; encode gives the cost of making it.
    @Benchmark
    fun lengthOfNewString(): Int = source.u8.length

    @Benchmark
    fun encode(): Int = source.u8.byteCount
}
