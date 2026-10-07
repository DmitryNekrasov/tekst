plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.buildconfig) apply false
    alias(libs.plugins.kotlin.allopen) apply false
    alias(libs.plugins.kotlinx.benchmark) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.nmcp) apply false
    alias(libs.plugins.plugin.publish) apply false
    alias(libs.plugins.nmcp.aggregation)
}

allprojects {
    group = "io.github.dmitrynekrasov"
    version = "0.3.0"

    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "testing"
                    url = rootProject.layout.buildDirectory.dir("localMaven").get().asFile.toURI()
                }
            }

            publications.withType<MavenPublication>().configureEach {
                pom {
                    url.set("https://github.com/DmitryNekrasov/tekst")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("DmitryNekrasov")
                            name.set("Dmitry Nekrasov")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/DmitryNekrasov/tekst.git")
                        developerConnection.set("scm:git:ssh://github.com/DmitryNekrasov/tekst.git")
                        url.set("https://github.com/DmitryNekrasov/tekst")
                    }
                }
            }
        }

        // The key is in ~/.gradle/gradle.properties, so a build without it, such as CI, publishes unsigned.
        val keyFile = providers.gradleProperty("signing.keyFile").orNull?.takeIf { it.isNotBlank() }
        val keyPassword = providers.gradleProperty("signing.password").orNull
        if (keyFile != null && keyPassword != null) {
            val keyPath = keyFile.replaceFirst("~", System.getProperty("user.home"))
            val key = file(keyPath).takeIf { it.isFile }?.readText()
                ?: throw GradleException("signing.keyFile points to $keyPath, which does not exist")
            val publications = extensions.getByType<PublishingExtension>().publications
            apply(plugin = "signing")
            extensions.configure<SigningExtension> {
                useInMemoryPgpKeys(key, keyPassword)
                sign(publications)
            }
        }
    }
}

nmcpAggregation {
    centralPortal {
        username = providers.gradleProperty("sonatype.username")
        password = providers.gradleProperty("sonatype.password")
        publishingType = "USER_MANAGED"
    }
}

dependencies {
    nmcpAggregation(project(":tekst"))
    nmcpAggregation(project(":utf8-string-compiler-plugin"))
}
