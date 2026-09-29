// DUMP_IR
import utf8string.u8

fun literal() = "\u043F\u0440\u0438".u8

fun box(): String = if (literal().codePointCount == 3) "OK" else "Fail"
