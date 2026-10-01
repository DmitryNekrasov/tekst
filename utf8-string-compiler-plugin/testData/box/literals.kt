import utf8string.Utf8String
import utf8string.u8

// The run-time encoding, which a folded literal must equal.
fun runtime(value: String): Utf8String = value.u8

fun check(actual: Utf8String, expected: String): String? {
    if (actual != runtime(expected)) return "'$expected' differs from the run-time encoding: '$actual'"
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
    // An unpaired surrogate is folded too: it becomes U+FFFD.
    val unpaired = "a\uD800".u8
    if (unpaired != runtime("a\uD800")) return "unpaired: differs from the run-time encoding"
    if (unpaired != runtime("a\uFFFD")) return "unpaired: not U+FFFD"
    return "OK"
}
