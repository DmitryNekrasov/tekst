/*
 * Copyright 2026 Dmitry Nekrasov and tekst library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package tekst.benchmarks

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import tekst.Utf8String
import tekst.isGraphemeBoundary
import tekst.nextGraphemeBoundary
import tekst.previousGraphemeBoundary
import tekst.u8
import kotlin.random.Random

@State(Scope.Benchmark)
class GraphemeBoundaryBenchmark {
    @Param("ascii", "cyrillic", "cjk", "hangul", "emoji", "indic", "zalgo", "crlf")
    var corpus: String = ""

    private var source: String = ""
    private var string: Utf8String = "".u8
    private var indices: IntArray = IntArray(0)
    private var charIndices: IntArray = IntArray(0)

    @Setup
    fun prepare() {
        source = Corpora.text(corpus)
        string = source.u8
        val random = Random(corpus.hashCode())
        indices = IntArray(1024) { random.nextInt(string.byteCount + 1) }
        charIndices = IntArray(1024) { random.nextInt(source.length + 1) }
    }

    @Benchmark
    fun skipNext(): Int {
        val graphemes = string.iterator()
        var count = 0
        while (graphemes.hasNext()) {
            graphemes.skipNext()
            count++
        }
        return count
    }

    @Benchmark
    fun iterateBackward(): Int {
        val graphemes = string.iterator(string.byteCount)
        var count = 0
        while (graphemes.hasPrevious()) {
            graphemes.previous()
            count++
        }
        return count
    }

    @Benchmark
    fun skipPrevious(): Int {
        val graphemes = string.iterator(string.byteCount)
        var count = 0
        while (graphemes.hasPrevious()) {
            graphemes.skipPrevious()
            count++
        }
        return count
    }

    @Benchmark
    fun isBoundaryAtRandomIndices(): Int {
        var count = 0
        for (index in indices) if (string.isGraphemeBoundary(index)) count++
        return count
    }

    @Benchmark
    fun nextBoundaryAtRandomIndices(): Int {
        var sum = 0
        for (index in indices) sum += string.nextGraphemeBoundary(index)
        return sum
    }

    @Benchmark
    fun previousBoundaryAtRandomIndices(): Int {
        var sum = 0
        for (index in indices) sum += string.previousGraphemeBoundary(index)
        return sum
    }

    @Benchmark
    fun isBoundaryAtRandomCharIndices(): Int {
        var count = 0
        for (index in charIndices) if (source.isGraphemeBoundary(index)) count++
        return count
    }

    @Benchmark
    fun nextBoundaryAtRandomCharIndices(): Int {
        var sum = 0
        for (index in charIndices) sum += source.nextGraphemeBoundary(index)
        return sum
    }

    @Benchmark
    fun previousBoundaryAtRandomCharIndices(): Int {
        var sum = 0
        for (index in charIndices) sum += source.previousGraphemeBoundary(index)
        return sum
    }
}
