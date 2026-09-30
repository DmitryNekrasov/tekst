/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.tests

import utf8string.Utf8String
import utf8string.u8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

const val GREETING = "\u041F\u0440\u0438\u0432\u0435\u0442"
const val COUNT = 3
const val PART = "Hello, \u043C\u0438\u0440 \u65E5\u672C \uD83D\uDE00! "
const val K1 = PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART +
    PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART + PART

// Every char below U+0100, then U+0800 and U+10000. In UTF-8 they give NUL, the C0 controls, DEL, every continuation
// byte and the lead bytes C2, C3, E0 and F0. NUL followed by a digit catches an octal escape in the generated code.
const val LATIN1 =
    "\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\u0008\u0009\u000A\u000B\u000C\u000D\u000E\u000F" +
    "\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001A\u001B\u001C\u001D\u001E\u001F" +
    "\u0020\u0021\u0022\u0023\u0024\u0025\u0026\u0027\u0028\u0029\u002A\u002B\u002C\u002D\u002E\u002F" +
    "\u0030\u0031\u0032\u0033\u0034\u0035\u0036\u0037\u0038\u0039\u003A\u003B\u003C\u003D\u003E\u003F" +
    "\u0040\u0041\u0042\u0043\u0044\u0045\u0046\u0047\u0048\u0049\u004A\u004B\u004C\u004D\u004E\u004F" +
    "\u0050\u0051\u0052\u0053\u0054\u0055\u0056\u0057\u0058\u0059\u005A\u005B\u005C\u005D\u005E\u005F" +
    "\u0060\u0061\u0062\u0063\u0064\u0065\u0066\u0067\u0068\u0069\u006A\u006B\u006C\u006D\u006E\u006F" +
    "\u0070\u0071\u0072\u0073\u0074\u0075\u0076\u0077\u0078\u0079\u007A\u007B\u007C\u007D\u007E\u007F" +
    "\u0080\u0081\u0082\u0083\u0084\u0085\u0086\u0087\u0088\u0089\u008A\u008B\u008C\u008D\u008E\u008F" +
    "\u0090\u0091\u0092\u0093\u0094\u0095\u0096\u0097\u0098\u0099\u009A\u009B\u009C\u009D\u009E\u009F" +
    "\u00A0\u00A1\u00A2\u00A3\u00A4\u00A5\u00A6\u00A7\u00A8\u00A9\u00AA\u00AB\u00AC\u00AD\u00AE\u00AF" +
    "\u00B0\u00B1\u00B2\u00B3\u00B4\u00B5\u00B6\u00B7\u00B8\u00B9\u00BA\u00BB\u00BC\u00BD\u00BE\u00BF" +
    "\u00C0\u00C1\u00C2\u00C3\u00C4\u00C5\u00C6\u00C7\u00C8\u00C9\u00CA\u00CB\u00CC\u00CD\u00CE\u00CF" +
    "\u00D0\u00D1\u00D2\u00D3\u00D4\u00D5\u00D6\u00D7\u00D8\u00D9\u00DA\u00DB\u00DC\u00DD\u00DE\u00DF" +
    "\u00E0\u00E1\u00E2\u00E3\u00E4\u00E5\u00E6\u00E7\u00E8\u00E9\u00EA\u00EB\u00EC\u00ED\u00EE\u00EF" +
    "\u00F0\u00F1\u00F2\u00F3\u00F4\u00F5\u00F6\u00F7\u00F8\u00F9\u00FA\u00FB\u00FC\u00FD\u00FE\u00FF" +
    "\u0800\uD800\uDC00\u00001\u00007"

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
    fun controlCharsInLargeLiteral() {
        val literal = (LATIN1 + LATIN1 + LATIN1).u8
        // Above 1 KiB, so every target gets the bytes from a Latin-1 string constant.
        assertEquals(1185, literal.byteCount)
        assertEncodes(LATIN1 + LATIN1 + LATIN1, literal)
    }

    @Test
    fun unpairedSurrogate() {
        val literal = "a\uD800".u8
        assertEquals(runtime("a\uD800"), literal)
        assertEquals(4, literal.byteCount)
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

    private fun runtime(value: String) = value.u8

    // A well-formed string's UTF-8 size is the stdlib's; each code point has one lead (non-continuation) byte.
    private fun assertEncodes(expected: String, actual: Utf8String) {
        val bytes = expected.encodeToByteArray()
        assertEquals(bytes.size, actual.byteCount)
        assertEquals(bytes.count { (it.toInt() and 0xC0) != 0x80 }, actual.codePointCount)
        assertEquals(runtime(expected), actual)
    }
}
