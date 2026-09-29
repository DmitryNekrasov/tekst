/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.tests

import utf8string.Utf8String
import utf8string.u8
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

const val GREETING = "\u041F\u0440\u0438\u0432\u0435\u0442"
const val COUNT = 3
const val PART = "Hello, \u043C\u0438\u0440 \u65E5\u672C \uD83D\uDE00! "
const val K1 = PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART +
    PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART

object Holder {
    const val NAME = "holder"
    const val PREFIX = "#"
    const val TEXT = """
        #a
        #b
    """
}

class U8LiteralsTest {
    @Test
    fun literals() {
        assertEncodes("", "".u8)
        assertEncodes("ascii", "ascii".u8)
        assertEncodes("\u041F\u0440\u0438\u0432\u0435\u0442, \uD83D\uDE00", "\u041F\u0440\u0438\u0432\u0435\u0442, \uD83D\uDE00".u8)
        assertEncodes("\u65E5\u672C\u8A9E", "\u65E5\u672C\u8A9E".u8)
        assertEncodes("\u0000\u007F\u0080\u07FF\u0800\uFFFF", "\u0000\u007F\u0080\u07FF\u0800\uFFFF".u8)
    }

    @Test
    fun folding() {
        assertEncodes("\u041F\u0440\u0438\u0432\u0435\u0442, 3!", "$GREETING, $COUNT!".u8)
        assertEncodes("ab", ("a" + "b").u8)
        assertEncodes("line 1\n  line 2", """
            line 1
              line 2
        """.trimIndent().u8)
    }

    @Test
    fun largeLiteral() {
        assertEncodes(K1 + K1, (K1 + K1).u8)
    }

    @Test
    fun unpairedSurrogate() {
        val literal = "a\uD800".u8
        assertContentEquals(byteArrayOf(0x61, 0xEF.toByte(), 0xBF.toByte(), 0xBD.toByte()), literal.buffer)
        assertEquals(2, literal.codePointCount)
    }

    @Test
    fun nonConstant() {
        assertEncodes("x \u043C\u0438\u0440", runtime("x \u043C\u0438\u0440"))
    }

    @Test
    fun sideEffectsOfReceivers() {
        val log = StringBuilder()
        fun holder(tag: String): Holder = Holder.also { log.append(tag) }
        fun thrower(): Holder = throw IllegalStateException()

        assertEncodes("holder-holder", "${holder("1").NAME}-${holder("2").NAME}".u8)
        assertEncodes("a\nb", holder("3").TEXT.trimMargin(holder("4").PREFIX).u8)
        assertFailsWith<IllegalStateException> { (holder("5").NAME + thrower().NAME + holder("6").NAME).u8 }
        assertEquals("12345", log.toString())
    }

    @Test
    fun freshArrayForEachEvaluation() {
        val first = constant()
        val second = constant()
        assertNotSame(first.buffer, second.buffer)
        first.buffer[0] = 0
        assertEquals(0x61, second.buffer[0].toInt())
    }

    private fun constant() = "abc".u8

    private fun runtime(value: String) = value.u8

    // A well-formed string's UTF-8 bytes are the stdlib's; each code point has one lead (non-continuation) byte.
    private fun assertEncodes(expected: String, actual: Utf8String) {
        val bytes = expected.encodeToByteArray()
        assertContentEquals(bytes, actual.buffer)
        assertEquals(bytes.count { (it.toInt() and 0xC0) != 0x80 }, actual.codePointCount)
    }
}
