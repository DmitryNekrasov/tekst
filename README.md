# utf8-string

A Kotlin Multiplatform string stored as UTF-8 bytes.

`.u8` encodes a `String`. It counts the UTF-8 length first, so it allocates only the result array. `toString()` decodes the bytes back into a `String` on each call:

```kotlin
import utf8string.u8

val utf8 = "Привет, 😀".u8 // 18 bytes of UTF-8
utf8.toString() // Привет, 😀
```

An unpaired surrogate has no UTF-8 encoding, so it becomes U+FFFD (`EF BF BD`) on every platform. On the JVM, `String.encodeToByteArray()` writes `?` instead.

Targets: JVM, JS, Wasm (JS and WASI), and Kotlin/Native for Linux, macOS, Windows, iOS, watchOS, and tvOS.

## Graphemes

`for` iterates over the extended grapheme clusters of a `Utf8String` ([UAX #29](https://www.unicode.org/reports/tr29/)): what a user sees as one character, such as a letter with its accents, a flag, or an emoji with a skin tone.

```kotlin
val text = "Hi 👋🏽 🇪🇸".u8
for (grapheme in text) {
    print("[$grapheme]") // [H][i][ ][👋🏽][ ][🇪🇸]
}
text.length // 6
```

`length` counts graphemes, unlike `String.length`, which counts UTF-16 chars. It is computed on the first access and then kept.

A `Grapheme` equals another one with the same UTF-8 bytes. It reads the bytes of its string instead of copying them, so it keeps them in memory; `toUtf8String()` copies it. Iteration allocates nothing for an ASCII char or CR LF, which are shared objects, and one small object for any other grapheme.

The rules are those of Unicode 18.0 (UAX #29 revision 49, extended grapheme clusters), and the tests run the Unicode conformance file `GraphemeBreakTest.txt` on every target that runs tests; the Kotlin/Native device targets run none. `./gradlew generateUnicodeData` generates the tables and the test data from the Unicode 18.0 files in `unicode/`, and `./gradlew build` fails when the committed files differ from what it writes. A new Unicode version also needs the version constants in `utf8-string-generator/src/utf8string/generator/Main.kt` changed and the rules reviewed.

`./gradlew :utf8-string-benchmarks:benchmark` runs the benchmarks on the JVM, JS and Wasm JS, and on Kotlin/Native for macOS arm64 or Linux x64 when the host is one of them; `:utf8-string-benchmarks:jvmBenchmarkAllocations` reports the bytes allocated per operation on the JVM.

## Compile-time literals

With the utf8-string compiler plugin, `"...".u8` on a constant string is encoded during compilation, like `"..."u8` in C#. A constant string is a literal, a `const val`, a template or a `+` of them, and `trimIndent()` or `trimMargin()` on such a string.

Each literal is created once: the plugin keeps the literals of a file in a private object of that file, and every evaluation returns the same `Utf8String`. A literal inside an inline function is created on each evaluation instead, because the inlined code can end up in another file, which cannot reach that object.

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.20"
    id("io.github.dmitrynekrasov.utf8-string") version "0.1.0-SNAPSHOT"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.dmitrynekrasov:utf8-string:0.1.0-SNAPSHOT")
        }
    }
}
```

The library and the plugin are not published yet. The compiler plugin API changes in every Kotlin release, so the plugin works only with Kotlin 2.4.20 and fails the build on other versions.

A receiver that is not a constant, for example `name.u8` or a template with a `Float`, `Double`, or unsigned value, is encoded at run time, and the plugin reports the `U8_NOT_CONSTANT` warning. An unpaired surrogate in a literal gives the `U8_UNPAIRED_SURROGATE` warning. These warnings come from the compiler backend, so the IDE editor does not highlight them, and `@Suppress` works for them only on a declaration, like a function, a local variable, or a file, not on an expression. With `-Werror`, use such a `@Suppress("U8_NOT_CONSTANT")` or `-Xwarning-level=U8_NOT_CONSTANT:warning`. The plugin reports from each platform compilation, so a warning in common code is repeated for every target.

Without the plugin, `"...".u8` gives an equal `Utf8String`, created on each evaluation.

## License

Licensed under the [Apache License 2.0](LICENSE).

The grapheme tables and test data are derived from Unicode data files, Copyright © 1991-2026 Unicode, Inc., under the [Unicode License V3](unicode/license.txt).
