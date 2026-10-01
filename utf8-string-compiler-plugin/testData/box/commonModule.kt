// LANGUAGE: +MultiPlatformProjects
// MODULE: common
// FILE: common.kt
import utf8string.u8

const val COMMON = "common"

fun commonLiteral() = (COMMON + " \u043A\u043E\u0434").u8

// MODULE: main()()(common)
// FILE: main.kt
import utf8string.u8

fun runtime(value: String) = value.u8

fun box(): String {
    val literal = commonLiteral()
    if (literal != runtime("common \u043A\u043E\u0434")) return "Fail: differs from the run-time encoding"
    return "OK"
}
