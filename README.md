# utf8-string

[![JetBrains team project](https://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![GitHub license](https://img.shields.io/badge/license-Apache%202.0-green.svg?style=flat)](https://github.com/DmitryNekrasov/utf8-string/blob/main/LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.20-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/dmitrynekrasov/utf8-string/build.yml)](https://github.com/DmitryNekrasov/utf8-string/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.dmitrynekrasov/utf8-string.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.dmitrynekrasov/utf8-string)
[![KDoc link](https://img.shields.io/badge/API_reference-KDoc-blue)](https://dmitrynekrasov.github.io/utf8-string/)

A Kotlin Multiplatform string for working with graphemes. Its `length` counts them, and `for` iterates over them. A grapheme is what a user sees as one character, such as a letter with its accents, a flag, or an emoji with a skin tone. The string is immutable and stored as UTF-8 bytes.

```kotlin
import utf8string.u8

val text = "Hi 👋🏽 🇪🇸".u8
text.length            // 6
text.toString().length // 12
text.take(4)           // Hi 👋🏽

for (grapheme in text) {
    print("[$grapheme]") // [H][i][ ][👋🏽][ ][🇪🇸]
}
```

`.u8` encodes a `String` into a `Utf8String`, and `toString()` decodes it back. `String.length` counts UTF-16 chars, so the hand with a skin tone and the flag count as 4 each, and `String.take` or `substring` can split them. The Kotlin standard library has no grapheme API. The platform APIs, like `BreakIterator` on the JVM and `Intl.Segmenter` in JavaScript, are not common code, and their results depend on the platform version. For example, `BreakIterator` before JDK 20 counts the hand and the flag as 2 graphemes each. utf8-string has one implementation for all targets, with the rules of Unicode 18.0.

Targets: JVM, JS, Wasm (JS and WASI), and Kotlin/Native for Linux, macOS arm64, Windows, iOS, watchOS, and tvOS.

utf8-string is a personal hobby project.

## Setup

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.dmitrynekrasov:utf8-string:0.2.0")
        }
    }
}
```

The library needs Kotlin 2.4.20 or newer. In a JVM or Android project, the same `implementation` line goes into `dependencies`.

## Graphemes

The graphemes are the extended grapheme clusters of [UAX #29](https://www.unicode.org/reports/tr29/) (Unicode 18.0). `length` is computed on the first access and then kept.

Like `String`, `Utf8String` is not an `Iterable`. Its `graphemes` property is a `Sequence<Grapheme>` that can be iterated more than once.

```kotlin
text.graphemes.take(4).joinToString("") // Hi 👋🏽
```

A `Grapheme` equals another one with the same UTF-8 bytes. A grapheme reads the bytes of its string instead of copying them, so it can keep all of them in memory, and `toUtf8String()` copies only the grapheme. Iteration allocates nothing for an ASCII char or CR LF, which are shared objects, and one small object for any other grapheme.

A position in a `Utf8String` is a byte index, as in `copyInto`. `isGraphemeBoundary`, `nextGraphemeBoundary` and `previousGraphemeBoundary` take any byte index, for example one from a hit test or a search, and `iterator(index)` starts at the grapheme that contains the byte. The iterator goes both ways: `previous()` returns the grapheme before it, and `skipNext()` and `skipPrevious()` move without a `Grapheme`. `take`, `drop`, `takeLast` and `dropLast` count graphemes, as `length` does.

```kotlin
text.isGraphemeBoundary(7)       // false: 👋🏽 takes bytes 3 to 10
text.previousGraphemeBoundary(7) // 3
text.takeLast(1)                 // 🇪🇸
```

A function with an index reads the grapheme around the index and the one before it, and in a run of flags also the run before it, since a boundary there depends on the number of flags before it. So a loop over the graphemes should use an iterator, while a loop of such calls over a run of flags reads the run again for each flag.

The rules also keep a conjunct of an Indic script in one grapheme:

```kotlin
"नमस्ते".u8.length // 3: न, म, स्ते
```

The tests run the Unicode conformance file `GraphemeBreakTest.txt` and the CLDR tests of Indic words, check that each RGI emoji sequence is one grapheme, and compare the graphemes of random texts with a reference model, and also with ICU4J on the JVM and with `Intl.Segmenter` on Node.js.

## UTF-8 bytes

`.u8` counts the UTF-8 length first, so it allocates only the result array. `copyInto` writes the bytes into an existing array, with the signature of `ByteArray.copyInto`, and `toString()` decodes them again on each call:

```kotlin
val utf8 = "Привет, 😀".u8
utf8.byteCount     // 18
utf8.toByteArray() // a copy of the 18 bytes
utf8.toString()    // Привет, 😀
```

To cut the bytes at a grapheme boundary, for example to fit a text into a limit of bytes, take the index from an iterator:

```kotlin
val end = text.iterator(minOf(limit, text.byteCount)).index // the last boundary that fits
text.copyInto(buffer, endIndex = end)
```

There is no function that makes a `Utf8String` from bytes. Two `Utf8String` values are equal when their bytes are equal.

An unpaired surrogate has no UTF-8 encoding, so it becomes U+FFFD (`EF BF BD`) on every platform. On the JVM, `String.encodeToByteArray()` writes `?` instead.

## Compile-time literals

With the utf8-string compiler plugin, `"...".u8` on a constant string is encoded during compilation, like `"..."u8` in C#. A constant string is a literal, a `const val`, a template or a `+` of them, and `trimIndent()` or `trimMargin()` on such a string.

Each literal is created once, in a private object of its file, and every evaluation returns the same `Utf8String`. Inside an inline function it is created on each evaluation instead, because the inlined code can end up in another file.

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.20"
    id("io.github.dmitrynekrasov.utf8-string") version "0.2.0"
}
```

The compiler plugin API changes in every Kotlin release, so the plugin works only with Kotlin 2.4.20 and fails the build on other versions.

A receiver that is not a constant, for example `name.u8` or a template with a `Float`, `Double`, or unsigned value, is encoded at run time, and the plugin reports the `U8_NOT_CONSTANT` warning. `toUtf8String()` encodes a string at run time without the warning. An unpaired surrogate in a literal gives the `U8_UNPAIRED_SURROGATE` warning.

These warnings come from the compiler backend, so the IDE does not highlight them, and `@Suppress` works for them only on a declaration, like a function, a local variable, or a file, not on an expression. With `-Werror`, use such a `@Suppress("U8_NOT_CONSTANT")` or `-Xwarning-level=U8_NOT_CONSTANT:warning`. A warning in common code is repeated for every target, since the plugin reports from each platform compilation.

Without the plugin, `"...".u8` gives an equal `Utf8String`, created on each evaluation.

## Performance

The tables give the time to count the graphemes of a text of about 65,500 UTF-16 chars, on an Apple M5 Max. `.u8.length` starts from a `String`, like the other implementations, so it encodes the string and then counts its graphemes. The number in parentheses is how many times `.u8.length` is faster than that implementation. Iteration over a `Utf8String` counts the graphemes of a string that is already encoded, with a `for` loop.

On the JVM, with JDK 25.0.4.1 and ICU4J 78.3:

| Text | `.u8.length`, us | Iteration over a `Utf8String`, us | ICU4J `BreakIterator`, us | `java.text.BreakIterator`, us |
| --- | ---: | ---: | ---: | ---: |
| ASCII | 14 | 32 | 545 (38x) | 410 (29x) |
| Cyrillic | 147 | 160 | 559 (3.8x) | 784 (5.3x) |
| CJK | 132 | 122 | 560 (4.2x) | 923 (7.0x) |
| Hangul | 171 | 196 | 566 (3.3x) | 880 (5.1x) |
| Emoji | 176 | 160 | 361 (2.1x) | 451 (2.6x) |
| Devanagari | 178 | 153 | 626 (3.5x) | 1363 (7.7x) |
| Combining marks | 176 | 86 | 249 (1.4x) | 355 (2.0x) |
| CR LF lines | 18 | 37 | 520 (29x) | 416 (23x) |

On Node.js 24.16.0:

| Text | `.u8.length`, us | Iteration over a `Utf8String`, us | `Intl.Segmenter`, us |
| --- | ---: | ---: | ---: |
| ASCII | 174 | 104 | 2183 (13x) |
| Cyrillic | 441 | 425 | 2946 (6.7x) |
| CJK | 377 | 409 | 3374 (9.0x) |
| Hangul | 476 | 663 | 3175 (6.7x) |
| Emoji | 388 | 271 | 952 (2.5x) |
| Devanagari | 484 | 511 | 1956 (4.0x) |
| Combining marks | 458 | 303 | 862 (1.9x) |
| CR LF lines | 203 | 159 | 2087 (10x) |

The texts are random words in each script, emoji sequences, Latin letters with 1 to 8 combining marks, and ASCII lines with CR LF, from `Corpora.kt` in `utf8-string-benchmarks`. All the implementations give the same grapheme counts on them, although ICU4J and Node.js implement Unicode 17.0. For an ASCII text, `length` does not run the rules, since in ASCII every char except LF after CR is a grapheme. On the JVM, the graphemes in the `for` loop do not escape it, so the JIT can remove their allocation.

Each JVM number is the mean of 5 forks, and each Node.js number is the median of 3 runs. `./gradlew :utf8-string-benchmarks:jvmComparisonBenchmark :utf8-string-benchmarks:jsComparisonBenchmark` runs the comparison once.

On the same texts, a query at a random byte index, such as `nextGraphemeBoundary`, takes 1 to 18 ns on the JVM and 1 to 70 ns on Node.js, since it reads only the code points around the index. A walk back with `previous()` takes up to 3.6 times as long as the `for` loop on the JVM and up to 2.4 times on Node.js, because each step looks for the start of the code point before it and checks the pair of their classes. `./gradlew :utf8-string-benchmarks:jvmBoundariesBenchmark :utf8-string-benchmarks:jsBoundariesBenchmark` runs these benchmarks.

## Development

`./gradlew generateUnicodeData` generates the grapheme tables and the test data from the Unicode 18.0 and CLDR 48 files in `unicode/`, and `./gradlew build` fails when the committed files differ from what it writes. A new Unicode version also needs the version constants in `utf8-string-generator/src/utf8string/generator/Main.kt` changed and the rules reviewed. The JVM tests also check the generated tables against the Unicode files.

`./gradlew :utf8-string-benchmarks:benchmark` runs the benchmarks on the JVM, JS and Wasm JS, and on Kotlin/Native for macOS arm64 or Linux x64 when the host is one of them. `:utf8-string-benchmarks:jvmBenchmarkAllocations` reports the bytes allocated per operation on the JVM.

`./gradlew dokkaGenerate` writes the API reference to `docs/`. GitHub Pages serves it from the `docs/` of the gh-pages branch.

## License

Licensed under the [Apache License 2.0](LICENSE).

The grapheme tables and test data are derived from Unicode data files, Copyright © 1991-2026 Unicode, Inc., under the [Unicode License V3](unicode/license.txt).
