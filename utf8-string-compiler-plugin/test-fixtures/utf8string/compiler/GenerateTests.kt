/*
 * Copyright 2026 Dmitry Nekrasov and tekst library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.compiler

import org.jetbrains.kotlin.generators.dsl.junit5.generateTestGroupSuiteWithJUnit5
import utf8string.compiler.runners.AbstractJsBoxTest
import utf8string.compiler.runners.AbstractJsOnlyBoxTest
import utf8string.compiler.runners.AbstractJvmBoxTest
import utf8string.compiler.runners.AbstractJvmDiagnosticTest

fun main(args: Array<String>) {
    generateTestGroupSuiteWithJUnit5 {
        testGroup(testsRoot = args[0], testDataRoot = args[1]) {
            testClass<AbstractJvmDiagnosticTest> {
                model("diagnostics")
            }
            testClass<AbstractJvmBoxTest> {
                model("box")
                model("jvm")
            }
            testClass<AbstractJsBoxTest> {
                model("box")
            }
            testClass<AbstractJsOnlyBoxTest> {
                model("js")
            }
        }
    }
}
