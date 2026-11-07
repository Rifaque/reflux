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
// Windows needs MAJOR.MINOR.(PATCH * 100 + S); Linux packages sort a stage with `~` (docs/VERSIONING.md,
// "Desktop package versions"). The channel names who a build is for, from its stage.
val refluxStage: String = Regex("""-(alpha|beta|rc)\.""").find(refluxVersion)?.groupValues?.get(1) ?: "stable"
val refluxWindowsVersion: String = refluxVersionCode.let { "${it / 1_000_000}.${it / 10_000 % 100}.${it % 10_000}" }
val refluxLinuxVersion: String = refluxVersion.replaceFirst("-", "~")
extra["reflux.version"] = refluxVersion
extra["reflux.versionCode"] = refluxVersionCode
extra["reflux.windowsVersion"] = refluxWindowsVersion
extra["reflux.linuxVersion"] = refluxLinuxVersion
extra["reflux.channel"] = refluxStage

/**
 * The commit a build is made from and whether the tree had changes (docs/VERSIONING.md, "Build metadata"), as
 * `commit=<short hash>` and `modified=<true|false>` lines; empty where there is no Git checkout to ask. Read
 * when a task needs it, never while configuring, so an edit does not invalidate the configuration cache.
 */
abstract class GitIdentity : ValueSource<String, GitIdentity.Parameters> {
    interface Parameters : ValueSourceParameters {
        val root: DirectoryProperty
    }

    @get:Inject
    abstract val exec: ExecOperations

    override fun obtain(): String {
        fun git(vararg args: String): String? = runCatching {
            val out = java.io.ByteArrayOutputStream()
            val result = exec.exec {
                commandLine("git", *args)
                workingDir = parameters.root.get().asFile
                standardOutput = out
                errorOutput = java.io.OutputStream.nullOutputStream()
                isIgnoreExitValue = true
            }
            out.toString().trim().takeIf { result.exitValue == 0 }
        }.getOrNull()
        val commit = git("rev-parse", "--short=7", "HEAD")?.takeIf { it.isNotEmpty() } ?: return ""
        val modified = git("status", "--porcelain")?.isNotEmpty() ?: true
        return "commit=$commit\nmodified=$modified\n"
    }
}

extra["reflux.gitIdentity"] = providers.of(GitIdentity::class) { parameters.root.set(layout.projectDirectory) }

/**
 * Fails when the third-party notices (`legal/components.txt`, shown on the About page) no longer describe a
 * shell's runtime classpath: a module no line covers, or a line for this target whose modules are all gone.
 */
abstract class CheckThirdPartyNotices : DefaultTask() {
    @get:InputFile
    abstract val components: RegularFileProperty

    @get:Input
    abstract val target: Property<String>

    @get:Input
    abstract val modules: SetProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val lines = components.get().asFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split("|").map(String::trim) }
        fun pattern(glob: String) = Regex(glob.split("*").joinToString(".*") { Regex.escape(it) })
        val mine = lines.filter { target.get() in it[3].split(" ") }.map { it[0] to it[4].split(" ").filter(String::isNotEmpty).map(::pattern) }
        val present = modules.get()
        val uncovered = present.filter { module -> mine.none { (_, patterns) -> patterns.any { it.matches(module) } } }.sorted()
        val gone = mine.filter { (_, patterns) -> patterns.isNotEmpty() && present.none { module -> patterns.any { it.matches(module) } } }.map { it.first }
        report.get().asFile.writeText((uncovered.map { "uncovered: $it" } + gone.map { "gone: $it" }).joinToString("\n"))
        if (uncovered.isNotEmpty() || gone.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("legal/components.txt does not match the ${target.get()} build (docs/RELEASE.md, \"Third-party notices\").\n")
                    if (uncovered.isNotEmpty()) append("Modules no line covers; read their licenses and add them:\n").append(uncovered.joinToString("\n") { "  $it" }).append('\n')
                    if (gone.isNotEmpty()) append("Lines whose modules are no longer in the build:\n").append(gone.joinToString("\n") { "  $it" })
                },
            )
        }
    }
}

// Each shell's notices are checked against what that shell ships: its release runtime classpath.
val noticeTargets = mapOf(":desktop:app" to ("desktop" to "runtimeClasspath"), ":android:app" to ("android" to "releaseRuntimeClasspath"))
subprojects {
    val (noticeTarget, classpath) = noticeTargets[path] ?: return@subprojects
    val checkNotices = tasks.register<CheckThirdPartyNotices>("checkThirdPartyNotices") {
        group = "verification"
        description = "Fails when legal/components.txt does not cover this shell's third-party modules."
        components.set(rootProject.layout.projectDirectory.file("legal/components.txt"))
        target.set(noticeTarget)
        modules.set(
            provider { configurations.getByName(classpath) }.flatMap { it.incoming.resolutionResult.rootComponent }.map { root ->
                val visited = HashSet<org.gradle.api.artifacts.component.ComponentIdentifier>()
                fun walk(component: org.gradle.api.artifacts.result.ResolvedComponentResult) {
                    for (dependency in component.dependencies) {
                        val next = (dependency as? org.gradle.api.artifacts.result.ResolvedDependencyResult)?.selected ?: continue
                        if (visited.add(next.id)) walk(next)
                    }
                }
                walk(root)
                visited.filterIsInstance<org.gradle.api.artifacts.component.ModuleComponentIdentifier>().map { "${it.group}:${it.module}" }.toSet()
            },
        )
        report.set(layout.buildDirectory.file("reports/thirdPartyNotices.txt"))
    }
    tasks.matching { it.name == "check" }.configureEach { dependsOn(checkNotices) }
}

/**
 * The release identity of this tree as JSON (docs/RELEASE.md, "Release system"): the product version and every
 * number derived from it, the channel, and the commit. The release tooling and the website read this; neither
 * computes a version of its own.
 */
val releaseInfo by tasks.registering {
    group = "release"
    description = "Writes the release identity (version, derived numbers, channel, commit) to build/release/release-info.json."
    @Suppress("UNCHECKED_CAST")
    val identity = project.extra["reflux.gitIdentity"] as Provider<String>
    val output = layout.buildDirectory.file("release/release-info.json")
    val fields = mapOf(
        "product" to "Reflux", "version" to refluxVersion, "versionCode" to refluxVersionCode.toString(),
        "windowsVersion" to refluxWindowsVersion, "linuxVersion" to refluxLinuxVersion, "channel" to refluxStage,
    )
    inputs.property("identity", identity)
    inputs.property("fields", fields)
    outputs.file(output)
    doLast {
        val git = identity.get().lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
        fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val json = (fields.map { (key, value) -> if (key == "versionCode") "  \"$key\": $value" else "  \"$key\": ${quote(value)}" } +
            "  \"commit\": ${git["commit"]?.let(::quote) ?: "null"}" + "  \"modified\": ${git["modified"] ?: "true"}")
            .joinToString(",\n", "{\n", "\n}\n")
        output.get().asFile.apply { parentFile.mkdirs() }.writeText(json)
        println(json)
    }
}

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
