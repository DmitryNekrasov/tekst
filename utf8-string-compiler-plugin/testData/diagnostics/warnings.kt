// RUN_PIPELINE_TILL: BACKEND
import utf8string.u8

const val PI = 3.14
const val UNSIGNED = 7u

fun parameter(name: String) = name.<!U8_NOT_CONSTANT!>u8<!>
fun template(name: String) = "Hi, $name".<!U8_NOT_CONSTANT!>u8<!>
fun floating() = "pi=$PI".<!U8_NOT_CONSTANT!>u8<!>
fun unsigned() = "u=$UNSIGNED".<!U8_NOT_CONSTANT!>u8<!>
fun unpaired() = "a\uD800".<!U8_UNPAIRED_SURROGATE!>u8<!>

fun constant() = "abc".u8
fun reference() = listOf("a").map(String::u8)

@Suppress("U8_NOT_CONSTANT")
fun suppressed(name: String) = name.u8
