// LANGUAGE: +MultiPlatformProjects
// MODULE: common
// FILE: common.kt
import utf8string.u8

const val COMMON = "common"

fun commonLiteral() = (COMMON + " \u043A\u043E\u0434").u8

// MODULE: main()()(common)
// FILE: main.kt
fun box(): String {
    val literal = commonLiteral()
    if (!literal.buffer.contentEquals("common \u043A\u043E\u0434".encodeToByteArray())) return "Fail: bytes"
    if (literal.codePointCount != 10) return "Fail: code points ${literal.codePointCount}"
    return "OK"
}
