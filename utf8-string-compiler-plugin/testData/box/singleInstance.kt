// MODULE: lib
// FILE: lib.kt
package lib

import utf8string.u8

fun libLiteral() = "from lib".u8

// Its body is inlined into the main module, which cannot reach the private holder of this file.
inline fun inlineLibLiteral() = "from lib".u8

// MODULE: main(lib)
// FILE: holder.kt
import utf8string.u8

// A top-level property with a side effect, which runs when this file is initialized.
val fileInitialized = run {
    initLog.append("holder.kt initialized")
    true
}

class Holder {
    fun literal() = "abc".u8
}

fun readFileProperty() = fileInitialized

// FILE: main.kt
import lib.inlineLibLiteral
import lib.libLiteral
import utf8string.u8

val initLog = StringBuilder()

fun literal() = "abc".u8
fun sameLiteral() = "abc".u8
fun runtime(value: String) = value.u8

fun box(): String {
    // A literal is created once, and equal literals of one file share it.
    if (literal() !== literal()) return "Fail: a new instance for each evaluation"
    if (sameLiteral() !== literal()) return "Fail: equal literals of one file are not shared"
    if (literal() != runtime("abc")) return "Fail: value"
    if (libLiteral() !== libLiteral()) return "Fail: lib"
    if (inlineLibLiteral() != runtime("from lib")) return "Fail: inline"
    // The literals of holder.kt live in their own object, so using one does not initialize holder.kt.
    val holder = Holder()
    if (holder.literal() !== holder.literal() || holder.literal() != literal()) return "Fail: class"
    if (initLog.isNotEmpty()) return "Fail: a literal initialized its file: $initLog"
    readFileProperty()
    if (initLog.toString() != "holder.kt initialized") return "Fail: file initialization: $initLog"
    return "OK"
}
