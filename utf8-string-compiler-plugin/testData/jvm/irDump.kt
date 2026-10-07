// DUMP_IR
import tekst.u8

fun literal() = "\u043F\u0440\u0438".u8

fun box(): String = if (literal().length == 3) "OK" else "Fail"
