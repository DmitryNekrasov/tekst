import utf8string.u8

const val PI = 3.14
const val UNSIGNED = 7u

// These are encoded at run time, with the same result as without the plugin.
fun parameter(name: String) = name.u8
fun template(name: String) = "Hi, $name".u8
fun unsigned() = "u=$UNSIGNED".u8

fun box(): String {
    if (parameter("ab").byteCount != 2) return "Fail: parameter"
    if (template("x") != parameter("Hi, x")) return "Fail: template"
    if ("pi=$PI".u8 != parameter("pi=$PI")) return "Fail: floating"
    if (unsigned() != parameter("u=7")) return "Fail: unsigned"
    val unpaired = parameter("a\uD800")
    if (unpaired.byteCount != 4) return "Fail: unpaired byte count ${unpaired.byteCount}"
    if (unpaired.codePointCount != 2) return "Fail: unpaired code points"
    // trimMargin throws for a blank prefix, so the plugin leaves the call to run time.
    try {
        "|a".trimMargin(" ").u8
        return "Fail: blank margin"
    } catch (e: IllegalArgumentException) {
    }
    return "OK"
}
