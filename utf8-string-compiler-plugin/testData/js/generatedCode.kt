// CHECK_CALLED_IN_SCOPE: function=u8Literal scope=U8Literals$GeneratedCodeKt
// CHECK_CALLED_IN_SCOPE: function=u8LiteralLatin1 scope=U8Literals$GeneratedCodeKt
// CHECK_NOT_CALLED_IN_SCOPE: function=u8Literal scope=literal
// CHECK_NOT_CALLED_IN_SCOPE: function=u8LiteralLatin1 scope=largeLiteral
import tekst.u8

const val P64 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
const val K1 = P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64 + P64

fun literal() = "\u043F\u0440\u0438".u8
fun largeLiteral() = (K1 + "!").u8

fun box(): String {
    if (literal().length != 3) return "Fail: literal"
    if (largeLiteral().length != 1025) return "Fail: large literal ${largeLiteral().length}"
    return "OK"
}
