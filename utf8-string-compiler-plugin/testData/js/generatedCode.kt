// CHECK_CALLED_IN_SCOPE: function=u8Literal scope=literal
// CHECK_CALLED_IN_SCOPE: function=u8LiteralLatin1 scope=largeLiteral
import utf8string.u8

const val P64 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
const val K1 = P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64

// A small literal becomes u8Literal(new Int8Array([...])), and one above 1 KiB a Latin-1 string.
fun literal() = "\u043F\u0440\u0438".u8
fun largeLiteral() = (K1 + "!").u8

fun box(): String {
    if (literal().codePointCount != 3) return "Fail: literal"
    if (largeLiteral().buffer.size != 1025) return "Fail: large literal ${largeLiteral().buffer.size}"
    return "OK"
}
