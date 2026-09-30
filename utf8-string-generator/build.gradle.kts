plugins {
    alias(libs.plugins.kotlin.jvm)
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(emptyList<String>())
    }
    test {
        java.setSrcDirs(emptyList<String>())
        resources.setSrcDirs(emptyList<String>())
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

val unicodeDir = rootProject.layout.projectDirectory.dir("unicode")
val librarySourceDir = rootProject.layout.projectDirectory.dir("utf8-string/src")
val generatedFiles = listOf(
    "commonMain/kotlin/utf8string/GraphemeData.kt",
    "commonTest/kotlin/utf8string/GraphemeBreakTestData.kt",
    "commonTest/kotlin/utf8string/GraphemeClassTestData.kt",
    "commonTest/kotlin/utf8string/EmojiTestData.kt",
)

// Writes the grapheme tables of utf8-string and their test data from the Unicode files in unicode/.
tasks.register<JavaExec>("generateUnicodeData") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("utf8string.generator.MainKt")
    args(unicodeDir.asFile.absolutePath, librarySourceDir.asFile.absolutePath)
}

// Fails when a generated file differs from what the Unicode files give, so that the build catches stale or edited data.
val checkUnicodeData = tasks.register<JavaExec>("checkUnicodeData") {
    val marker = layout.buildDirectory.file("checkUnicodeData/ok")
    inputs.dir(unicodeDir).withPropertyName("unicode").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(generatedFiles.map { librarySourceDir.file(it) })
        .withPropertyName("generatedFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(marker).withPropertyName("marker")

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("utf8string.generator.MainKt")
    args(unicodeDir.asFile.absolutePath, librarySourceDir.asFile.absolutePath, "--check", marker.get().asFile.absolutePath)
}

tasks.named("check") {
    dependsOn(checkUnicodeData)
}
