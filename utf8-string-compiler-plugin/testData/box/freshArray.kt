import utf8string.u8

fun literal() = "abc".u8

fun box(): String {
    val first = literal()
    val second = literal()
    if (first.buffer === second.buffer) return "Fail: the literal shares its array"
    first.buffer[0] = 0
    if (second.buffer[0] != 0x61.toByte()) return "Fail: a change to one literal is visible in another"
    return "OK"
}
