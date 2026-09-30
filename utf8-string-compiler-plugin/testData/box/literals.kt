import utf8string.Utf8String
import utf8string.u8

// The run-time encoding, which a folded literal must equal.
fun runtime(value: String): Utf8String = value.u8

// A well-formed string's UTF-8 size is the stdlib's; each code point has one lead (non-continuation) byte.
fun check(actual: Utf8String, expected: String): String? {
    val bytes = expected.encodeToByteArray()
    if (actual.byteCount != bytes.size) return "byte count of '$expected': ${actual.byteCount}"
    val codePoints = bytes.count { (it.toInt() and 0xC0) != 0x80 }
    if (actual.codePointCount != codePoints) return "code points of '$expected': ${actual.codePointCount}"
    if (actual != runtime(expected)) return "bytes of '$expected' differ from the run-time encoding"
    return null
}

fun box(): String {
    check("".u8, "")?.let { return it }
    check("abc".u8, "abc")?.let { return it }
    check("\u043F\u0440\u0438\u0432\u0435\u0442".u8, "\u043F\u0440\u0438\u0432\u0435\u0442")?.let { return it }
    check("\u65E5\u672C".u8, "\u65E5\u672C")?.let { return it }
    check("\uD83D\uDE00".u8, "\uD83D\uDE00")?.let { return it }
    check("\u007F\u0080\u07FF\u0800\uFFFF".u8, "\u007F\u0080\u07FF\u0800\uFFFF")?.let { return it }
    check("a\nb\t\u0000\\\"".u8, "a\nb\t\u0000\\\"")?.let { return it }
    check("""raw $ "x" \n""".u8, "raw \$ \"x\" \\n")?.let { return it }
    check("${'$'}{not a template}".u8, "\${not a template}")?.let { return it }
    // An unpaired surrogate is folded too: it becomes U+FFFD and counts as one code point.
    val unpaired = "a\uD800".u8
    if (unpaired != runtime("a\uD800")) return "unpaired: differs from the run-time encoding"
    if (unpaired.byteCount != 4) return "unpaired byte count: ${unpaired.byteCount}"
    if (unpaired.codePointCount != 2) return "unpaired code points: ${unpaired.codePointCount}"
    return "OK"
}
