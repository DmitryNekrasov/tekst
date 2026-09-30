// RUN_PIPELINE_TILL: BACKEND
// LANGUAGE: +MultiPlatformProjects
// Every receiver form that the plugin folds. None has a marker, so a U8_NOT_CONSTANT here is a missed rewrite.

// MODULE: lib
// FILE: lib.kt
package lib

const val LIB_GREETING = "from lib"

// MODULE: common
// FILE: common.kt
import utf8string.u8

const val COMMON = "common"

fun commonLiteral() = (COMMON + " code").u8

// MODULE: main(lib)()(common)
// FILE: main.kt
import lib.LIB_GREETING
import utf8string.u8

const val GREETING = "Hi"
const val NUMBER = 42
const val BIG = -1234567890123L
const val SMALL: Byte = -7
const val SHORT: Short = 300
const val FLAG = true
const val LETTER = 'z'

object Holder {
    const val NAME = "holder"
    const val PREFIX = "#"
}

fun holder(): Holder = Holder

fun literal() = "abc".u8
fun template() = "$GREETING, $NUMBER $BIG $SMALL $SHORT $FLAG $LETTER ${null}".u8
fun nestedTemplate() = "<${"$GREETING!"}>".u8
fun stringPlus() = ("<" + GREETING + ">" + NUMBER + 'c' + null).u8
fun explicitPlus() = "a".plus(1).u8
fun nullablePlus() = (null + "x").u8
fun charPlus() = ('c' + "d").u8
fun objectConstant() = Holder.NAME.u8
fun receiverWithSideEffects() = holder().NAME.u8
fun templateWithSideEffects() = "${holder().NAME}-${holder().NAME}".u8
fun otherModule() = "$LIB_GREETING!".u8

fun trimIndent() = """
    line 1
      line 2
""".trimIndent().u8

fun trimMargin() = """
    |line 1
    |line 2
""".trimMargin().u8

fun trimMarginPrefix() = """
    #line 1
    #line 2
""".trimMargin(holder().PREFIX).u8
