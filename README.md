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

The library uses experimental companion blocks, so Kotlin marks its binaries as pre-release, and a project that uses the library needs the `-Xskip-prerelease-check` compiler flag. `Utf8String.fromString` is a companion block member, so calling it also needs `-Xcompanion-blocks-and-extensions`.

Licensed under the [Apache License 2.0](LICENSE).
