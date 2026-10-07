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
import tekst.isGraphemeBoundary
import tekst.nextGraphemeBoundary
import tekst.previousGraphemeBoundary
import java.text.StringCharacterIterator
import kotlin.random.Random
import com.ibm.icu.text.BreakIterator as IcuBreakIterator
import java.text.BreakIterator as JdkBreakIterator

// The BreakIterator of ICU4J and of the JDK takes the text in setText, so the queries after an edit call it before each
// query, as an editor must after each change of its text: on the line of the index, about 80 chars, or on the whole
// text.
@State(Scope.Benchmark)
class BreakIteratorBoundaryBenchmark {
    @Param("ascii", "cyrillic", "cjk", "hangul", "emoji", "indic", "zalgo", "crlf")
    var corpus: String = ""

    private var source: String = ""
    private var indices: IntArray = IntArray(0)
    private var lineStarts: IntArray = IntArray(0)
    private var lineEnds: IntArray = IntArray(0)
    private val icu = IcuBreakIterator.getCharacterInstance()
    private val jdk = JdkBreakIterator.getCharacterInstance()

    @Setup
    fun prepare() {
        source = Corpora.text(corpus)
        val random = Random(corpus.hashCode())
        // Before the end, where the following(end) of the JDK returns the end, not DONE.
        indices = IntArray(QUERIES) { random.nextInt(source.length) }
        // A line starts and ends at a grapheme boundary of the text, as a line of a document does.
        lineStarts = IntArray(QUERIES) {
            val start = maxOf(0, indices[it] - LINE_LENGTH / 2)
            if (source.isGraphemeBoundary(start)) start else source.previousGraphemeBoundary(start)
        }
        lineEnds = IntArray(QUERIES) {
            val end = minOf(source.length, indices[it] + LINE_LENGTH / 2)
            if (source.isGraphemeBoundary(end)) end else source.nextGraphemeBoundary(end)
        }
        icu.setText(source)
        jdk.setText(source)
        checkThatTheImplementationsAgree()
    }

    @Benchmark
    fun utf8StringIsBoundary(): Int {
        var count = 0
        for (index in indices) if (source.isGraphemeBoundary(index)) count++
        return count
    }

    @Benchmark
    fun icuIsBoundary(): Int {
        var count = 0
        for (index in indices) if (icu.isBoundary(index)) count++
        return count
    }

    @Benchmark
    fun jdkIsBoundary(): Int {
        var count = 0
        for (index in indices) if (jdk.isBoundary(index)) count++
        return count
    }

    @Benchmark
    fun utf8StringNext(): Int {
        var sum = 0
        for (index in indices) sum += source.nextGraphemeBoundary(index)
        return sum
    }

    @Benchmark
    fun icuNext(): Int {
        var sum = 0
        for (index in indices) sum += icu.following(index)
        return sum
    }

    @Benchmark
    fun jdkNext(): Int {
        var sum = 0
        for (index in indices) sum += jdk.following(index)
        return sum
    }

    @Benchmark
    fun utf8StringPrevious(): Int {
        var sum = 0
        for (index in indices) sum += source.previousGraphemeBoundary(index)
        return sum
    }

    @Benchmark
    fun icuPrevious(): Int {
        var sum = 0
        for (index in indices) sum += icu.preceding(index)
        return sum
    }

    @Benchmark
    fun jdkPrevious(): Int {
        var sum = 0
        for (index in indices) sum += jdk.preceding(index)
        return sum
    }

    @Benchmark
    fun utf8StringPreviousAfterEditInLine(): Int {
        var sum = 0
        for (i in 0..<QUERIES) sum += source.previousGraphemeBoundary(indices[i], lineStarts[i], lineEnds[i])
        return sum
    }

    @Benchmark
    fun icuPreviousAfterEditInLine(): Int {
        var sum = 0
        for (i in 0..<QUERIES) {
            icu.setText(StringCharacterIterator(source, lineStarts[i], lineEnds[i], lineStarts[i]))
            sum += icu.preceding(indices[i])
        }
        return sum
    }

    @Benchmark
    fun jdkPreviousAfterEditInLine(): Int {
        var sum = 0
        for (i in 0..<QUERIES) {
            jdk.setText(StringCharacterIterator(source, lineStarts[i], lineEnds[i], lineStarts[i]))
            sum += jdk.preceding(indices[i])
        }
        return sum
    }

    @Benchmark
    fun utf8StringPreviousAfterEditInText(): Int {
        var sum = 0
        for (i in 0..<QUERIES_IN_TEXT) sum += source.previousGraphemeBoundary(indices[i])
        return sum
    }

    @Benchmark
    fun icuPreviousAfterEditInText(): Int {
        var sum = 0
        for (i in 0..<QUERIES_IN_TEXT) {
            icu.setText(source)
            sum += icu.preceding(indices[i])
        }
        return sum
    }

    @Benchmark
    fun jdkPreviousAfterEditInText(): Int {
        var sum = 0
        for (i in 0..<QUERIES_IN_TEXT) {
            jdk.setText(source)
            sum += jdk.preceding(indices[i])
        }
        return sum
    }

    // The benchmarks compare the same work only when the 3 implementations give the same boundaries.
    private fun checkThatTheImplementationsAgree() {
        val icuInLine = IcuBreakIterator.getCharacterInstance()
        val jdkInLine = JdkBreakIterator.getCharacterInstance()
        for (i in 0..<QUERIES) {
            val index = indices[i]
            val expected = listOf(
                source.isGraphemeBoundary(index),
                source.nextGraphemeBoundary(index),
                source.previousGraphemeBoundary(index),
            )
            check(listOf(icu.isBoundary(index), icu.following(index), icu.preceding(index)) == expected) {
                "ICU4J differs at $index of $corpus"
            }
            check(listOf(jdk.isBoundary(index), jdk.following(index), jdk.preceding(index)) == expected) {
                "The JDK differs at $index of $corpus"
            }
            val inLine = source.previousGraphemeBoundary(index, lineStarts[i], lineEnds[i])
            icuInLine.setText(StringCharacterIterator(source, lineStarts[i], lineEnds[i], lineStarts[i]))
            check(icuInLine.preceding(index) == inLine) { "ICU4J differs in the line at $index of $corpus" }
            jdkInLine.setText(StringCharacterIterator(source, lineStarts[i], lineEnds[i], lineStarts[i]))
            check(jdkInLine.preceding(index) == inLine) { "The JDK differs in the line at $index of $corpus" }
        }
    }

    private companion object {
        const val QUERIES = 1024
        const val QUERIES_IN_TEXT = 16
        const val LINE_LENGTH = 80
    }
}
