// FILE: left/Part.kt
@file:JvmName("LeftPart")
package repro

import tekst.u8

fun left() = "a".u8

// FILE: right/Part.kt
@file:JvmName("RightPart")
package repro

import tekst.u8

fun right() = "bc".u8

// FILE: one/One.kt
@file:JvmName("Shared")
@file:JvmMultifileClass
package repro

import tekst.u8

fun one() = "def".u8

// FILE: two/Two.kt
@file:JvmName("Shared")
@file:JvmMultifileClass
package repro

import tekst.u8

fun two() = "ghij".u8

// FILE: main.kt
package repro

fun box(): String {
    if (left().length != 1) return "Fail: left ${left().length}"
    if (right().length != 2) return "Fail: right ${right().length}"
    if (one().length != 3) return "Fail: one ${one().length}"
    if (two().length != 4) return "Fail: two ${two().length}"
    return "OK"
}
