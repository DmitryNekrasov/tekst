plugins {
    alias(libs.plugins.kotlin.jvm)
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(emptyList<String>())
    }
    test {
        java.setSrcDirs(listOf("test"))
        resources.setSrcDirs(emptyList<String>())
    }
}

dependencies {
    testImplementation(libs.kotlin.test.junit5)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

val unicodeDir = rootProject.layout.projectDirectory.dir("unicode")
val librarySourceDir = rootProject.layout.projectDirectory.dir("tekst/src")
val generatedFiles = listOf(
    "commonMain/kotlin/tekst/GraphemeData.kt",
    "commonTest/kotlin/tekst/GraphemeBreakTestData.kt",
    "commonTest/kotlin/tekst/GraphemeClassTestData.kt",
    "commonTest/kotlin/tekst/EmojiTestData.kt",
)

tasks.register<JavaExec>("generateUnicodeData") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("tekst.generator.MainKt")
    args(unicodeDir.asFile.absolutePath, librarySourceDir.asFile.absolutePath)
}

val checkUnicodeData = tasks.register<JavaExec>("checkUnicodeData") {
    val marker = layout.buildDirectory.file("checkUnicodeData/ok")
    inputs.dir(unicodeDir).withPropertyName("unicode").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(generatedFiles.map { librarySourceDir.file(it) })
        .withPropertyName("generatedFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(marker).withPropertyName("marker")

    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("tekst.generator.MainKt")
    args(unicodeDir.asFile.absolutePath, librarySourceDir.asFile.absolutePath, "--check", marker.get().asFile.absolutePath)
}

tasks.named("check") {
    dependsOn(checkUnicodeData)
}

tasks.test {
    useJUnitPlatform()
    inputs.dir(unicodeDir).withPropertyName("unicode").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("tekst.unicodeDir", unicodeDir.asFile.absolutePath)
}
