plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.buildconfig) apply false
    alias(libs.plugins.kotlin.allopen) apply false
    alias(libs.plugins.kotlinx.benchmark) apply false
}

allprojects {
    group = "io.github.dmitrynekrasov"
    version = "0.1.0-SNAPSHOT"

    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "testing"
                    url = rootProject.layout.buildDirectory.dir("localMaven").get().asFile.toURI()
                }
            }
        }
    }
}
