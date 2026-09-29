# utf8-string

A Kotlin Multiplatform string stored as UTF-8 bytes.

`Utf8String.fromString` counts the UTF-8 length of a `String` first, so it allocates only the result array:

```kotlin
import utf8string.Utf8String

val utf8 = Utf8String.fromString("Привет, 😀")
utf8.buffer.size    // 18
utf8.codePointCount // 9
```

An unpaired surrogate has no UTF-8 encoding, so it becomes U+FFFD (`EF BF BD`) on every platform and counts as one code point. On the JVM, `String.encodeToByteArray()` writes `?` instead.

Targets: JVM, JS, Wasm (JS and WASI), and Kotlin/Native for Linux, macOS, Windows, iOS, watchOS, and tvOS.

Licensed under the [Apache License 2.0](LICENSE).
