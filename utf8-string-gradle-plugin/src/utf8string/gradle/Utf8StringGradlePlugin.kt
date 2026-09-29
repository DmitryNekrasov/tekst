/*
 * Copyright 2026 Dmitry Nekrasov and utf8-string library contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package utf8string.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@Suppress("unused") // Applied by its plugin id.
class Utf8StringGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        // The compiler plugin API changes in every Kotlin release, so the plugin works only with the version it is built for.
        target.plugins.withType(KotlinBasePlugin::class.java).configureEach { kotlinPlugin ->
            if (kotlinPlugin.pluginVersion != BuildConfig.KOTLIN_VERSION) {
                throw GradleException(
                    "utf8-string ${BuildConfig.COMPILER_PLUGIN_VERSION} requires Kotlin ${BuildConfig.KOTLIN_VERSION}, " +
                        "but the project uses Kotlin ${kotlinPlugin.pluginVersion}.",
                )
            }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = BuildConfig.KOTLIN_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = BuildConfig.COMPILER_PLUGIN_GROUP,
        artifactId = BuildConfig.COMPILER_PLUGIN_NAME,
        version = BuildConfig.COMPILER_PLUGIN_VERSION,
    )

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> =
        kotlinCompilation.target.project.provider { emptyList() }
}
