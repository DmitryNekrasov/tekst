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
import com.ibm.icu.text.BreakIterator as IcuBreakIterator
import java.text.BreakIterator as JdkBreakIterator

// Counting graphemes with ICU4J and the JDK, on UTF-16 strings, for comparison with GraphemeBenchmark.
@State(Scope.Benchmark)
class BreakIteratorBenchmark {
    @Param("ascii", "cyrillic", "cjk", "hangul", "emoji", "indic", "zalgo", "crlf")
    var corpus: String = ""

    private var source: String = ""
    private val icu = IcuBreakIterator.getCharacterInstance()
    private val jdk = JdkBreakIterator.getCharacterInstance()

    @Setup
    fun prepare() {
        source = Corpora.text(corpus)
    }

    @Benchmark
    fun icu(): Int {
        icu.setText(source)
        var count = 0
        while (icu.next() != IcuBreakIterator.DONE) count++
        return count
    }

    @Benchmark
    fun jdk(): Int {
        jdk.setText(source)
        var count = 0
        while (jdk.next() != JdkBreakIterator.DONE) count++
        return count
    }
}
