// Two files with one name in one package, which @JvmName tells apart, and the parts of a multifile class: each file
// keeps its own literals.
// FILE: left/Part.kt
@file:JvmName("LeftPart")
package repro

import utf8string.u8

fun left() = "a".u8

// FILE: right/Part.kt
@file:JvmName("RightPart")
package repro

import utf8string.u8

fun right() = "bc".u8

// FILE: one/One.kt
@file:JvmName("Shared")
@file:JvmMultifileClass
package repro

import utf8string.u8

fun one() = "def".u8

// FILE: two/Two.kt
@file:JvmName("Shared")
@file:JvmMultifileClass
package repro

import utf8string.u8

fun two() = "ghij".u8

// FILE: main.kt
package repro

fun box(): String {
    if (left().byteCount != 1) return "Fail: left ${left().byteCount}"
    if (right().byteCount != 2) return "Fail: right ${right().byteCount}"
    if (one().byteCount != 3) return "Fail: one ${one().byteCount}"
    if (two().byteCount != 4) return "Fail: two ${two().byteCount}"
    return "OK"
}
