# string-utf8

A Kotlin Multiplatform string stored as UTF-8 bytes.

`StringUTF8.fromString` counts the UTF-8 length of a `String` first, so it allocates only the result array:

```kotlin
import stringutf8.StringUTF8

val utf8 = StringUTF8.fromString("Привет, 😀")
utf8.buffer.size     // 18
utf8.codePointNumber // 9
```

An unpaired surrogate has no UTF-8 encoding, so it becomes U+FFFD (`EF BF BD`) on every platform and counts as one code point. On the JVM, `String.encodeToByteArray()` writes `?` instead.

Targets: JVM, JS, Wasm (JS and WASI), and Kotlin/Native for Linux, macOS, Windows, iOS, watchOS, and tvOS.

Licensed under the [Apache License 2.0](LICENSE).
