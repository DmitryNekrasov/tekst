// MODULE: lib
// FILE: lib.kt
package lib

const val LIB_GREETING = "from lib"

// MODULE: main(lib)
// FILE: main.kt
import lib.LIB_GREETING
import utf8string.Utf8String
import utf8string.u8

const val GREETING = "Hi"
const val NUMBER = 42
const val BIG = 1234567890123L
const val SMALL: Byte = -7
const val SHORT: Short = 300
const val FLAG = true
const val LETTER = 'z'

object Holder {
    const val NAME = "holder"
}

// The run-time encoding, which a folded literal must equal.
fun runtime(value: String): Utf8String = value.u8

fun check(actual: Utf8String, expected: String): String? {
    if (actual != runtime(expected)) return "'$expected' differs from the run-time encoding: '$actual'"
    return null
}

fun box(): String {
    check(("a" + "b").u8, "ab")?.let { return it }
    check((GREETING + "!").u8, "Hi!")?.let { return it }
    check(("<" + GREETING + ">" + NUMBER).u8, "<Hi>42")?.let { return it }
    check("$GREETING, $NUMBER $BIG $SMALL $SHORT $FLAG $LETTER".u8, "Hi, 42 1234567890123 -7 300 true z")?.let { return it }
    check(Holder.NAME.u8, "holder")?.let { return it }
    check(LIB_GREETING.u8, "from lib")?.let { return it }
    check("$LIB_GREETING!".u8, "from lib!")?.let { return it }
    check(("x" + null).u8, "xnull")?.let { return it }
    check(('c' + "d").u8, "cd")?.let { return it }
    check(
        """
            |line 1
            |  line 2
        """.trimMargin().u8,
        "line 1\n  line 2",
    )?.let { return it }
    check(
        """
            #line 1
            #line 2
        """.trimMargin("#").u8,
        "line 1\nline 2",
    )?.let { return it }
    check(
        """
            line 1
              line 2
        """.trimIndent().u8,
        "line 1\n  line 2",
    )?.let { return it }
    return "OK"
}
