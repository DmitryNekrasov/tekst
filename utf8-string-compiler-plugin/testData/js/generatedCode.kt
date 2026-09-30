// CHECK_CALLED_IN_SCOPE: function=u8Literal scope=U8Literals$GeneratedCodeKt
// CHECK_CALLED_IN_SCOPE: function=u8LiteralLatin1 scope=U8Literals$GeneratedCodeKt
// CHECK_NOT_CALLED_IN_SCOPE: function=u8Literal scope=literal
// CHECK_NOT_CALLED_IN_SCOPE: function=u8LiteralLatin1 scope=largeLiteral
import utf8string.u8

const val P64 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
const val K1 = P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64

// The holder object of the file creates each literal once: a small one from new Int8Array([...]), one above 1 KiB
// from a Latin-1 string.
fun literal() = "\u043F\u0440\u0438".u8
fun largeLiteral() = (K1 + "!").u8

fun box(): String {
    if (literal().codePointCount != 3) return "Fail: literal"
    if (largeLiteral().byteCount != 1025) return "Fail: large literal ${largeLiteral().byteCount}"
    return "OK"
}
