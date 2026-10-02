/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.tests

import utf8string.u8
import kotlin.test.Test
import kotlin.test.assertEquals

class HolderNamesTest {
    @Test
    fun filesWithOneNameKeepTheirLiterals() {
        assertEquals(listOf(4, 10), listOf(leftLiteral().length, rightLiteral().length))
        assertEquals(runtime("left"), leftLiteral())
        assertEquals(runtime("right side"), rightLiteral())
    }

    private fun runtime(value: String) = value.u8
}
