/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.benchmarks

import kotlin.random.Random

internal object Corpora {
    fun text(name: String): String {
        val random = Random(name.hashCode())
        val text = StringBuilder()
        while (text.length < 64 * 1024) {
            when (name) {
                "ascii" -> text.append(ASCII_WORDS[random.nextInt(ASCII_WORDS.size)]).append(' ')
                "cyrillic" -> text.word(random) { 0x0430 + random.nextInt(32) }
                "cjk" -> repeat(random.nextInt(4, 20)) { text.appendCodePoint(0x4E00 + random.nextInt(0x5200)) }
                    .also { text.appendCodePoint(0x3002) }

                "hangul" -> text.word(random) { 0xAC00 + random.nextInt(11172) }
                "emoji" -> EMOJI[random.nextInt(EMOJI.size)].forEach { text.appendCodePoint(it) }.also { text.append(' ') }
                "indic" -> text.devanagariWord(random)
                "zalgo" -> text.word(random) {
                    text.appendCodePoint('a'.code + random.nextInt(26))
                    repeat(random.nextInt(1, 9)) { text.appendCodePoint(0x0300 + random.nextInt(0x70)) }
                    -1
                }

                "crlf" -> text.append("line ").append(random.nextInt(1000)).append("\r\n")
                else -> error("No corpus $name")
            }
        }
        return text.toString()
    }

    private fun StringBuilder.word(random: Random, next: () -> Int) {
        repeat(random.nextInt(2, 10)) { next().let { if (it >= 0) appendCodePoint(it) } }
        append(' ')
    }

    private fun StringBuilder.devanagariWord(random: Random) {
        repeat(random.nextInt(1, 5)) {
            appendCodePoint(0x0915 + random.nextInt(0x25))
            if (random.nextInt(3) == 0) {
                appendCodePoint(0x094D)
                appendCodePoint(0x0915 + random.nextInt(0x25))
            }
            if (random.nextBoolean()) appendCodePoint(0x093E + random.nextInt(0x0F))
        }
        append(' ')
    }

    private fun StringBuilder.appendCodePoint(codePoint: Int) {
        if (codePoint < 0x10000) {
            append(codePoint.toChar())
        } else {
            append((0xD800 + ((codePoint - 0x10000) shr 10)).toChar())
            append((0xDC00 + ((codePoint - 0x10000) and 0x3FF)).toChar())
        }
    }

    private val ASCII_WORDS = listOf(
        "the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog,", "and", "then", "sleeps", "in", "sun.", "A",
        "grapheme", "cluster", "is", "what", "user", "sees", "as", "one", "character;", "UTF-8", "bytes", "(2026)",
    )

    private val EMOJI = listOf(
        intArrayOf(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467, 0x200D, 0x1F466),
        intArrayOf(0x1F44D, 0x1F3FD),
        intArrayOf(0x1F1EA, 0x1F1F8, 0x1F1F3, 0x1F1F4),
        intArrayOf(0x0031, 0xFE0F, 0x20E3),
        intArrayOf(0x1F3F4, 0xE0067, 0xE0062, 0xE0073, 0xE0063, 0xE0074, 0xE007F),
        intArrayOf(0x1F9D1, 0x1F3FE, 0x200D, 0x1F4BB),
        intArrayOf(0x2764, 0xFE0F),
        intArrayOf(0x1F600),
    )
}
