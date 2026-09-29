import utf8string.u8

fun box(): String {
    val literal = "abc".u8
    if (literal.buffer.size != 3 || literal.codePointCount != 3) return "Fail: ${literal.buffer.size} ${literal.codePointCount}"
    return "OK"
}
