import tekst.Utf8String
import tekst.u8

object Holder {
    const val NAME = "holder"
    const val PREFIX = "#"
    const val TEXT = """
        #a
        #b
    """
}

val log = StringBuilder()

fun holder(tag: String): Holder {
    log.append(tag)
    return Holder
}

fun thrower(): Holder = throw IllegalStateException("boom")

fun runtime(value: String): Utf8String = value.u8

fun check(name: String, actual: Utf8String, expected: String, expectedLog: String): String? {
    if (actual != runtime(expected)) return "Fail: $name differs from the run-time encoding of '$expected': '$actual'"
    if (log.toString() != expectedLog) return "Fail: $name log '$log'"
    log.clear()
    return null
}

fun box(): String {
    check("single", holder("1").NAME.u8, "holder", "1")?.let { return it }
    check("plus", (holder("1").NAME + holder("2").NAME).u8, "holderholder", "12")?.let { return it }
    check("template", "${holder("1").NAME}-${holder("2").NAME}".u8, "holder-holder", "12")?.let { return it }
    check("trimMargin", holder("1").TEXT.trimMargin(holder("2").PREFIX).u8, "a\nb", "12")?.let { return it }

    // Not a constant, so the call stays, and each side effect still runs once, in order.
    val name = "n"
    check("mixed", ("a" + holder("1").NAME + name + holder("2").NAME).u8, "aholdernholder", "12")?.let { return it }

    try {
        (holder("1").NAME + thrower().NAME + holder("2").NAME).u8
        return "Fail: no exception"
    } catch (e: IllegalStateException) {
        if (e.message != "boom") return "Fail: exception ${e.message}"
    }
    if (log.toString() != "1") return "Fail: exception log '$log'"
    return "OK"
}
