plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kmp.library) apply false
}

// Developers can drop libmpv into .reflux/native (ignored by Git) instead of installing it system-wide.
// It is a convenience for running real playback locally: nothing compiles against it, and the tests
// that need an engine skip themselves where none can be loaded.
val developmentNatives = rootDir.resolve(".reflux/native").takeIf { it.isDirectory }?.absolutePath

// The one product version (docs/VERSIONING.md). Everything that reports a version derives it from this
// property; the numbers stores and installers need are computed here and never written by hand.
val refluxVersion: String = providers.gradleProperty("reflux.version").get()
val refluxVersionCode: Int = run {
    val match = Regex("""(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)\.(\d+))?""").matchEntire(refluxVersion)
        ?: throw GradleException("reflux.version '$refluxVersion' is not MAJOR.MINOR.PATCH[-alpha|beta|rc.N] (docs/VERSIONING.md)")
    val (major, minor, patch) = match.destructured.toList().take(3).map(String::toInt)
    val stage = match.groupValues[4]
    val n = match.groupValues[5].toIntOrNull()
    if (minor > 99 || patch > 99) throw GradleException("reflux.version '$refluxVersion': MINOR and PATCH stay below 100 (docs/VERSIONING.md)")
    if (n != null && n !in 1..29) throw GradleException("reflux.version '$refluxVersion': a stage number is 1 to 29 (docs/VERSIONING.md)")
    val s = when (stage) { "alpha" -> n!!; "beta" -> 30 + n!!; "rc" -> 60 + n!!; else -> 99 }
    major * 1_000_000 + minor * 10_000 + patch * 100 + s
}
extra["reflux.version"] = refluxVersion
extra["reflux.versionCode"] = refluxVersionCode

val printVersion by tasks.registering {
    group = "help"
    description = "Prints the product version and the Android versionCode derived from it."
    val version = refluxVersion
    val code = refluxVersionCode
    doLast { println("$version ($code)") }
}

// The shared source sets (`commonMain`) are meant for every platform. The engine modules are compiled once,
// for the JVM, and Android uses them as they are, so the compiler accepts JVM and desktop APIs in them.
// This check holds the line: it fails on imports and calls that only exist on one platform. (`ui` is
// compiled for Android as well, where an Android SDK is installed, and there the compiler does it.)
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
