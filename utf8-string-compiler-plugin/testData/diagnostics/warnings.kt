// RUN_PIPELINE_TILL: BACKEND
import utf8string.toUtf8String
import utf8string.u8

const val PI = 3.14
const val UNSIGNED = 7u

fun parameter(name: String) = name.<!U8_NOT_CONSTANT!>u8<!>
fun template(name: String) = "Hi, $name".<!U8_NOT_CONSTANT!>u8<!>
fun floating() = "pi=$PI".<!U8_NOT_CONSTANT!>u8<!>
fun unsigned() = "u=$UNSIGNED".<!U8_NOT_CONSTANT!>u8<!>
fun unpaired() = "a\uD800".<!U8_UNPAIRED_SURROGATE!>u8<!>

// trimMargin throws for a blank prefix, so it is left to run time, like a prefix or a text that is not constant.
fun blankMargin() = <!TRIM_MARGIN_BLANK_PREFIX!>"|a".trimMargin(" ")<!>.<!U8_NOT_CONSTANT!>u8<!>
fun marginParameter(prefix: String) = "#a".trimMargin(prefix).<!U8_NOT_CONSTANT!>u8<!>
fun indentParameter(text: String) = text.trimIndent().<!U8_NOT_CONSTANT!>u8<!>

fun constant() = "abc".u8
fun reference() = listOf("a").map(String::u8)

// toUtf8String encodes at run time on purpose, so it gets no warning.
fun runtime(name: String) = name.toUtf8String()

@Suppress("U8_NOT_CONSTANT")
fun suppressed(name: String) = name.u8
