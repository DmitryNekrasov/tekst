import utf8string.u8

// Mixed text folded from constants. K8 is 6912 bytes of UTF-8 and takes 11008 bytes in a Latin-1 string constant, where
// a non-ASCII byte takes 2. A JVM string constant holds 65535 bytes, so twelve copies make the compiler split it. They
// are also above the 1 KiB byteArrayOf limit on klib targets.
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
