import utf8string.u8

// A callable reference is not a literal: it encodes at run time, without a diagnostic.
fun box(): String {
    val unbound = String::u8
    if (unbound("ab") != "ab".u8) return "Fail: unbound"
    val bound = "abc"::u8
    if (bound() != "abc".u8) return "Fail: bound"
    val mapped = listOf("a", "\u043F\u0440").map(String::u8)
    if (mapped != listOf("a".u8, "\u043F\u0440".u8)) return "Fail: mapped"
    return "OK"
}
