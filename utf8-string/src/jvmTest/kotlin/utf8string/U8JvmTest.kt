/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class U8JvmTest {
    // Classes compiled with the plugin call the entry points as static methods of utf8string.U8Kt.
    @Test
    fun entryPointsStayInU8Kt() {
        val u8Kt = Class.forName("utf8string.U8Kt")
        for (method in listOf(
            u8Kt.getMethod("u8Literal", ByteArray::class.java, Int::class.javaPrimitiveType),
            u8Kt.getMethod("u8LiteralLatin1", String::class.java, Int::class.javaPrimitiveType),
        )) {
            assertTrue(Modifier.isStatic(method.modifiers), method.name)
            assertEquals(Utf8String::class.java, method.returnType, method.name)
        }
    }
}
