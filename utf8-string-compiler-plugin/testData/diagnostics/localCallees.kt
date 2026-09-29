// RUN_PIPELINE_TILL: BACKEND
import utf8string.u8

// A local function or a member of a local class is not the stdlib function with the same name.
fun localFunction(): Int {
    fun plus(a: String, b: String) = b + a
    return plus("a", "b").<!U8_NOT_CONSTANT!>u8<!>.codePointCount
}

fun anonymousObject(): Int {
    val anonymous = object {
        operator fun plus(other: String) = "x$other"
    }
    return ("a" + (anonymous + "y")).<!U8_NOT_CONSTANT!>u8<!>.codePointCount
}

fun localClass(): Int {
    class Local {
        fun trimIndent() = "z"
    }
    return "${Local().trimIndent()}".<!U8_NOT_CONSTANT!>u8<!>.codePointCount
}
