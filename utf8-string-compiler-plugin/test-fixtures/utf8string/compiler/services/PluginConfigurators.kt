/*
 * Copyright 2026 Dmitry Nekrasov and tekst library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.compiler.services

import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.RuntimeClasspathProvider
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.targetPlatform
import utf8string.compiler.Utf8StringCompilerPluginRegistrar
import java.io.File

fun TestConfigurationBuilder.configurePlugin() {
    useConfigurators(::PluginRegistrarConfigurator, ::Utf8StringRuntimeConfigurator)
    useCustomRuntimeClasspathProviders(::Utf8StringRuntimeClasspathProvider)
}

private class PluginRegistrarConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    private val registrar = Utf8StringCompilerPluginRegistrar()

    override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
        module: TestModule,
        configuration: CompilerConfiguration,
    ) {
        with(registrar) { registerExtensions(configuration) }
    }
}

private class Utf8StringRuntimeConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    override fun configureCompilerConfiguration(configuration: CompilerConfiguration, module: TestModule) {
        val platform = module.targetPlatform(testServices)
        when {
            platform.isJvm() -> configuration.addJvmClasspathRoots(utf8StringJvmRuntimeClasspath)
            platform.isJs() -> {
                val libraries = configuration.getList(JSConfigurationKeys.LIBRARIES)
                configuration.put(JSConfigurationKeys.LIBRARIES, libraries + utf8StringJsRuntimeClasspath.map { it.absolutePath })
            }
        }
    }
}

private class Utf8StringRuntimeClasspathProvider(testServices: TestServices) : RuntimeClasspathProvider(testServices) {
    override fun runtimeClassPaths(module: TestModule): List<File> {
        val platform = module.targetPlatform(testServices)
        return when {
            platform.isJvm() -> utf8StringJvmRuntimeClasspath
            platform.isJs() -> utf8StringJsRuntimeClasspath
            else -> emptyList()
        }
    }
}

private val utf8StringJvmRuntimeClasspath = classpathFiles("utf8StringRuntime.jvm.classpath")
private val utf8StringJsRuntimeClasspath = classpathFiles("utf8StringRuntime.js.classpath")

private fun classpathFiles(property: String): List<File> {
    val value = System.getProperty(property) ?: error("The '$property' system property is not set")
    return value.split(File.pathSeparator).map(::File)
}
