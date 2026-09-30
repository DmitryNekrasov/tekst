# utf8-string

A Kotlin Multiplatform string stored as UTF-8 bytes.

`.u8` encodes a `String`. It counts the UTF-8 length first, so it allocates only the result array:

```kotlin
import utf8string.u8

val utf8 = "Привет, 😀".u8
utf8.buffer.size    // 18
utf8.codePointCount // 9
```

An unpaired surrogate has no UTF-8 encoding, so it becomes U+FFFD (`EF BF BD`) on every platform and counts as one code point. On the JVM, `String.encodeToByteArray()` writes `?` instead.

Targets: JVM, JS, Wasm (JS and WASI), and Kotlin/Native for Linux, macOS, Windows, iOS, watchOS, and tvOS.

The library uses experimental companion blocks, so Kotlin marks its binaries as pre-release, and a project that uses the library needs the `-Xskip-prerelease-check` compiler flag. `Utf8String.fromString` is a companion block member, so a project that calls it needs `-Xcompanion-blocks-and-extensions` instead. That flag also accepts pre-release binaries, but it marks the project's own binaries as pre-release too.

## Compile-time literals

With the utf8-string compiler plugin, `"...".u8` on a constant string is encoded during compilation, like `"..."u8` in C#. The generated code only copies the bytes into a new array for each evaluation. A constant string is a literal, a `const val`, a template or a `+` of them, and `trimIndent()` or `trimMargin()` on such a string.

On the JVM, the bytes are stored in a string constant, which holds 65535 bytes, and a non-ASCII byte takes 2 of them. So the Kotlin compiler splits a literal from about 32 KiB of non-ASCII text into pieces and joins them with a `StringBuilder` on each evaluation, which allocates 3 times the size of the literal.

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.20"
    id("io.github.dmitrynekrasov.utf8-string") version "0.1.0-SNAPSHOT"
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.dmitrynekrasov:utf8-string:0.1.0-SNAPSHOT")
        }
    }
}
```

The library and the plugin are not published yet. The compiler plugin API changes in every Kotlin release, so the plugin works only with Kotlin 2.4.20 and fails the build on other versions.

A receiver that is not a constant, for example `name.u8` or a template with a `Float`, `Double`, or unsigned value, is encoded at run time, and the plugin reports the `U8_NOT_CONSTANT` warning. An unpaired surrogate in a literal gives the `U8_UNPAIRED_SURROGATE` warning. These warnings come from the compiler backend, so the IDE editor does not highlight them, and `@Suppress` works for them only on a declaration, like a function, a local variable, or a file, not on an expression. With `-Werror`, use such a `@Suppress("U8_NOT_CONSTANT")` or `-Xwarning-level=U8_NOT_CONSTANT:warning`. The plugin reports from each platform compilation, so a warning in common code is repeated for every target.

Without the plugin, `"...".u8` gives the same result at run time.

## License

Licensed under the [Apache License 2.0](LICENSE).
