import utf8string.u8

// Mixed text folded from constants. K8 is 6912 bytes of UTF-8 and takes 11008 bytes in a Latin-1 string constant, where
// a non-ASCII byte takes 2. A JVM string constant holds 65535 bytes, so twelve copies make the compiler split it. They
// are also above the 1 KiB byteArrayOf limit on klib targets.
const val PART = "Hello, \u043C\u0438\u0440 \u65E5\u672C \uD83D\uDE00! "
const val K1 = PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART +
    PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART
const val K8 = K1 + K1 + K1 + K1 + K1 + K1 + K1 + K1

// The run-time encoding, which a folded literal must equal.
fun runtime(value: String) = value.u8

fun box(): String {
    val literal = (K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8).u8
    val text = K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8
    if (literal != runtime(text)) return "Fail: bytes differ from the run-time encoding"
    return "OK"
}
