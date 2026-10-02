import utf8string.u8

// K8 takes 11008 bytes in a Latin-1 string constant, so twelve copies exceed the 65535 bytes of a JVM string constant.
// They are also above the 1 KiB byteArrayOf limit of klib targets.
const val PART = "Hello, \u043C\u0438\u0440 \u65E5\u672C \uD83D\uDE00! "
const val K1 = PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART +
    PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART
const val K8 = K1 + K1 + K1 + K1 + K1 + K1 + K1 + K1

fun runtime(value: String) = value.u8

fun box(): String {
    val literal = (K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8).u8
    val text = K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8 + K8
    if (literal != runtime(text)) return "Fail: differs from the run-time encoding"
    return "OK"
}
