import utf8string.u8

// 8 KiB of mixed text, folded from constants. Three copies make a non-ASCII Latin-1 payload well above the 65535-byte
// limit of a JVM string constant, and above the 1 KiB byteArrayOf limit on klib targets.
const val PART = "Hello, \u043C\u0438\u0440 \u65E5\u672C \uD83D\uDE00! "
const val K1 = PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART +
    PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART
const val K8 = K1 + K1 + K1 + K1 + K1 + K1 + K1 + K1

fun box(): String {
    val literal = (K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8).u8
    val expected = (K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8).encodeToByteArray()
    if (!literal.buffer.contentEquals(expected)) return "Fail: bytes, ${literal.buffer.size} vs ${expected.size}"
    val codePoints = expected.count { (it.toInt() and 0xC0) != 0x80 }
    if (literal.codePointCount != codePoints) return "Fail: code points, ${literal.codePointCount} vs $codePoints"
    return "OK"
}
