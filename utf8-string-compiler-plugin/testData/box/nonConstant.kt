import utf8string.u8

const val PI = 3.14
const val UNSIGNED = 7u

// These are encoded at run time, with the same result as without the plugin.
fun parameter(name: String) = name.u8
fun template(name: String) = "Hi, $name".u8
fun unsigned() = "u=$UNSIGNED".u8

fun box(): String {
    if (parameter("ab").buffer.size != 2) return "Fail: parameter"
    if (template("x").buffer.decodeToString() != "Hi, x") return "Fail: template"
    if ("pi=$PI".u8.buffer.decodeToString() != "pi=$PI") return "Fail: floating"
    if (unsigned().buffer.decodeToString() != "u=7") return "Fail: unsigned"
    val unpaired = parameter("a\uD800")
    if (unpaired.buffer.toList() != listOf<Byte>(0x61, -17, -65, -67)) return "Fail: unpaired ${unpaired.buffer.toList()}"
    if (unpaired.codePointCount != 2) return "Fail: unpaired code points"
    // trimMargin throws for a blank prefix, so the plugin leaves the call to run time.
    try {
        "|a".trimMargin(" ").u8
        return "Fail: blank margin"
    } catch (e: IllegalArgumentException) {
    }
    return "OK"
}
