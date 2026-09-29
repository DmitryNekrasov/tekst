import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.buildconfig)
    `java-gradle-plugin`
    `maven-publish`
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("resources"))
    }
    test {
        java.setSrcDirs(listOf("test"))
        resources.setSrcDirs(listOf("testResources"))
    }
}

dependencies {
    compileOnly(libs.kotlin.gradle.plugin.api)

    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.test.junit5)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // The Kotlin that Gradle 9 embeds.
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
    }
}

buildConfig {
    useKotlinOutput {
        internalVisibility = true
    }

    packageName("utf8string.gradle")
    buildConfigField("String", "KOTLIN_PLUGIN_ID", "\"${project.group}.utf8-string\"")
    buildConfigField("String", "KOTLIN_VERSION", "\"${libs.versions.kotlin.get()}\"")

    val compilerPlugin = project(":utf8-string-compiler-plugin")
    buildConfigField("String", "COMPILER_PLUGIN_GROUP", "\"${compilerPlugin.group}\"")
    buildConfigField("String", "COMPILER_PLUGIN_NAME", "\"${compilerPlugin.name}\"")
    buildConfigField("String", "COMPILER_PLUGIN_VERSION", "\"${compilerPlugin.version}\"")
}

gradlePlugin {
    plugins {
        create("utf8String") {
            id = "${project.group}.utf8-string"
            displayName = "utf8-string"
            description = "Computes the UTF-8 bytes of constant \"...\".u8 literals at compile time"
            implementationClass = "utf8string.gradle.Utf8StringGradlePlugin"
        }
    }
}

// The fixture project resolves the plugin, the compiler plugin and the library from build/localMaven.
tasks.test {
    dependsOn(
        ":utf8-string:publishKotlinMultiplatformPublicationToTestingRepository",
        ":utf8-string:publishJvmPublicationToTestingRepository",
        ":utf8-string-compiler-plugin:publishAllPublicationsToTestingRepository",
        "publishAllPublicationsToTestingRepository",
    )
    useJUnitPlatform()
    systemProperty("localMavenRepository", rootProject.layout.buildDirectory.dir("localMaven").get().asFile.absolutePath)
}
