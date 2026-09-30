pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "utf8-string"

include("utf8-string")
include("utf8-string-compiler-plugin")
include("utf8-string-plugin-tests")
include("utf8-string-gradle-plugin")
include("utf8-string-generator")
include("utf8-string-benchmarks")
