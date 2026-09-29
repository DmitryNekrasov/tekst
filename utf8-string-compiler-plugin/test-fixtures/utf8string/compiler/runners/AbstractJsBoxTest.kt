/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.compiler.runners

import org.jetbrains.kotlin.js.test.runners.AbstractJsTest
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider
import utf8string.compiler.services.configurePlugin

open class AbstractJsBoxTest(testDir: String = "box") : AbstractJsTest(
    pathToTestDir = "utf8-string-compiler-plugin/testData/$testDir",
    testGroupOutputDirPrefix = "$testDir/",
    parser = FirParser.LightTree,
) {
    override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider {
        return EnvironmentBasedStandardLibrariesPathProvider
    }

    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        builder.configurePlugin()
    }
}

// JS-only tests, such as IR dumps, which differ from the JVM ones.
open class AbstractJsOnlyBoxTest : AbstractJsBoxTest("js")
