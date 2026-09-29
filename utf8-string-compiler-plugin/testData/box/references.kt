import utf8string.u8

// A callable reference is not a literal: it encodes at run time, without a diagnostic.
fun box(): String {
    val unbound = String::u8
    if (unbound("ab").codePointCount != 2) return "Fail: unbound"
    val bound = "abc"::u8
    if (bound().codePointCount != 3) return "Fail: bound"
    val mapped = listOf("a", "\u043F\u0440").map(String::u8)
    if (mapped.map { it.buffer.size } != listOf(1, 4)) return "Fail: mapped ${mapped.map { it.buffer.size }}"
    return "OK"
}
