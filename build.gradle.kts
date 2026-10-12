plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

// Developers can drop libmpv into .reflux/native (ignored by Git) instead of installing it system-wide.
// It is a convenience for running real playback locally: nothing compiles against it, and the tests
// that need an engine skip themselves where none can be loaded.
val developmentNatives = rootDir.resolve(".reflux/native").takeIf { it.isDirectory }?.absolutePath

// The shared source sets (`commonMain`) are meant for every platform, but while the JVM is their only
// target the compiler accepts JVM and desktop APIs in them. This check holds the line until a second
// target does: it fails on imports and calls that only exist on one platform.
val sharedSources = fileTree(rootDir) {
    include("*/src/commonMain/**/*.kt", "*/*/src/commonMain/**/*.kt")
    exclude("**/build/**")
}
val verifySharedSources by tasks.registering {
    group = "verification"
    description = "Fails when platform-specific APIs are used in shared (commonMain) sources."
    val sources: FileCollection = sharedSources
    val root = rootDir
    inputs.files(sources)
    doLast {
        val platformOnly = listOf(
            Regex("""^\s*import\s+(java|javax|sun|com\.sun|jdk|android|org\.jetbrains\.skia|org\.jetbrains\.skiko|app\.cash\.sqldelight\.driver)\."""),
            Regex("""\b(java|javax)\.(io|nio|net|util|awt|lang|time|text|security)\.[A-Za-z]"""),
            Regex("""\bSystem\.(getProperty|getenv|currentTimeMillis|nanoTime|out|err|exit)\b"""),
            Regex("""\b(Thread\.(sleep|currentThread)|runBlocking\s*[({]|Dispatchers\.IO\b|::class\.java\b|\.javaClass\b)"""),
            Regex("""(@Synchronized|@JvmStatic|@JvmOverloads|@Throws|\bsynchronized\s*\(|\bString\.format\()"""),
        )
        val violations = sources.files.sorted().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//")
                if (platformOnly.any { it.containsMatchIn(code) }) "${file.relativeTo(root).invariantSeparatorsPath}:${index + 1}: ${line.trim()}" else null
            }
        }
        check(sources.files.isNotEmpty()) { "No shared sources were found; the check is looking in the wrong place." }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Platform-specific APIs in shared sources. Put them behind an interface implemented per platform:\n" + violations.joinToString("\n"),
            )
        }
    }
}

subprojects {
    if (developmentNatives != null) {
        tasks.withType<Test>().configureEach { systemProperty("jna.library.path", developmentNatives) }
        tasks.withType<JavaExec>().configureEach { systemProperty("jna.library.path", developmentNatives) }
    }
    tasks.matching { it.name == "check" }.configureEach { dependsOn(":verifySharedSources") }
}
