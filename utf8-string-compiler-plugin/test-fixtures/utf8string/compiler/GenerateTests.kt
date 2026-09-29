/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.compiler

import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5
import utf8string.compiler.runners.AbstractJsBoxTest
import utf8string.compiler.runners.AbstractJvmBoxTest

fun main(args: Array<String>) {
    generateTestGroupSuiteWithJUnit5 {
        testGroup(testsRoot = args[0], testDataRoot = args[1]) {
            testClass<AbstractJvmBoxTest> {
                model("box")
            }
            testClass<AbstractJsBoxTest> {
                model("box")
            }
        }
    }
}
