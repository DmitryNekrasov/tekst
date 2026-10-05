/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import kotlin.test.Test
import kotlin.test.fail

class GraphemeBoundaryJvmTest {
    // Every code point twice, so that each length of UTF-8 and each class meets the reads back of random access.
    @Test
    fun everyCodePointTwice() {
        for (codePoint in 0..0x10FFFF) {
            if (codePoint in 0xD800..0xDFFF) continue
            val codePoints = intArrayOf(codePoint, codePoint)
            val boundaries = byteBoundaries(codePoints, GraphemeModel.boundaries(codePoints))
            val string = codePointsToString(codePoints).u8
            for (index in 0..string.byteCount) {
                val isBoundary = index in boundaries
                val next = boundaries.firstOrNull { it > index } ?: -1
                val previous = boundaries.lastOrNull { it < index } ?: -1
                if (string.isGraphemeBoundary(index) != isBoundary ||
                    string.nextGraphemeBoundary(index) != next ||
                    string.previousGraphemeBoundary(index) != previous
                ) {
                    fail("U+%04X twice at %d: expected %b, %d and %d".format(codePoint, index, isBoundary, next, previous))
                }
            }
        }
    }
}
