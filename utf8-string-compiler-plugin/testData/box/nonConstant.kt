import utf8string.u8

const val PI = 3.14
const val UNSIGNED = 7u

// These are encoded at run time, with the same result as without the plugin.
fun parameter(name: String) = name.u8
fun template(name: String) = "Hi, $name".u8
fun unsigned() = "u=$UNSIGNED".u8

fun box(): String {
    if (parameter("ab") != "ab".u8) return "Fail: parameter"
    if (template("x") != parameter("Hi, x")) return "Fail: template"
    if ("pi=$PI".u8 != parameter("pi=$PI")) return "Fail: floating"
    if (unsigned() != parameter("u=7")) return "Fail: unsigned"
    if (parameter("a\uD800") != "a\uFFFD".u8) return "Fail: unpaired"
    // trimMargin throws for a blank prefix, so the plugin leaves the call to run time.
    try {
        "|a".trimMargin(" ").u8
        return "Fail: blank margin"
    } catch (e: IllegalArgumentException) {
    }
    return "OK"
}
