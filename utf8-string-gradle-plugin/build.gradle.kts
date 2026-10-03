import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.buildconfig)
    `java-gradle-plugin`
    `maven-publish`
    alias(libs.plugins.plugin.publish)
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
    website.set("https://github.com/DmitryNekrasov/utf8-string")
    vcsUrl.set("https://github.com/DmitryNekrasov/utf8-string")
    plugins {
        create("utf8String") {
            id = "${project.group}.utf8-string"
            displayName = "utf8-string"
            description = "Computes the UTF-8 bytes of constant \"...\".u8 literals at compile time"
            implementationClass = "utf8string.gradle.Utf8StringGradlePlugin"
            tags.set(listOf("kotlin", "kotlin-multiplatform", "compiler-plugin", "utf-8", "unicode"))
        }
    }
}

tasks.test {
    dependsOn(
        ":utf8-string:publishKotlinMultiplatformPublicationToTestingRepository",
        ":utf8-string:publishJvmPublicationToTestingRepository",
        ":utf8-string-compiler-plugin:publishAllPublicationsToTestingRepository",
        "publishAllPublicationsToTestingRepository",
    )
    // dependsOn adds no inputs, so without these the tests stay up to date after a change to the compiler plugin or the
    // library. The repository itself is not an input, since every publication adds new timestamped snapshot files.
    val compilerPlugin = project(":utf8-string-compiler-plugin")
    val library = project(":utf8-string")
    inputs.files(compilerPlugin.tasks.named("jar"), library.tasks.named("jvmJar"))
        .withPropertyName("publishedJars")
        .withNormalizer(ClasspathNormalizer::class)
    inputs.files(
        compilerPlugin.tasks.named("generateMetadataFileForMavenPublication"),
        library.tasks.named("generateMetadataFileForKotlinMultiplatformPublication"),
        library.tasks.named("generateMetadataFileForJvmPublication"),
    ).withPropertyName("publishedMetadata").withPathSensitivity(PathSensitivity.NONE)
    useJUnitPlatform()
    systemProperty("localMavenRepository", rootProject.layout.buildDirectory.dir("localMaven").get().asFile.absolutePath)
}
