import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.reflux.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.reflux"
        minSdk = 26
        targetSdk = 36
        // Both derive from reflux.version (docs/VERSIONING.md); never set them here.
        versionCode = rootProject.extra["reflux.versionCode"] as Int
        versionName = rootProject.extra["reflux.version"] as String
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // -Preflux.jellyfin=<url> points the on-device continuity test at a real server (see JellyfinContinuityTest).
        providers.gradleProperty("reflux.jellyfin").orNull?.takeIf { it.isNotBlank() }?.let { testInstrumentationRunnerArguments["refluxJellyfin"] = it }
        // The provider credential is an application credential supplied at build time; without one, names come from files.
        (providers.gradleProperty("reflux.tmdb.token").orNull ?: providers.environmentVariable("REFLUX_TMDB_TOKEN").orNull)
            ?.takeIf { it.isNotBlank() }?.let { resValue("string", "reflux_tmdb_token", it) }
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

/**
 * Three minutes of generated video for the on-device playback tests, made with FFmpeg the way the desktop
 * tests make theirs, so that no media is kept in the repository.
 */
abstract class GenerateTestMedia : DefaultTask() {
    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:Inject
    abstract val exec: ExecOperations

    @TaskAction
    fun generate() {
        val clip = output.get().file("clip.mp4").asFile
        exec.exec {
            commandLine(
                "ffmpeg", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "testsrc=size=320x180:rate=5", "-f", "lavfi", "-i", "sine=frequency=440",
                "-t", "180", "-c:v", "libx264", "-preset", "ultrafast", "-crf", "38", "-pix_fmt", "yuv420p", "-g", "5",
                "-c:a", "aac", "-b:a", "32k", "-metadata:s:a:0", "language=eng", clip.path,
            )
        }
    }
}

val generateTestMedia = tasks.register<GenerateTestMedia>("generateTestMedia") {
    output.set(layout.buildDirectory.dir("generated/testMedia"))
}

/**
 * The engine is plain JVM code shared with the desktop and compiled against a current JDK, so nothing stops it
 * calling a Java method the oldest supported Android lacks: Fire OS 8 (Android 11) failed on `Stream.toList()`,
 * which Android added in 14 and no emulator at a current API level could show. This reads the built package
 * (after D8, which backports some such methods itself) and fails on any `java.*` or `javax.*` method Reflux's
 * own classes call that Android added after `minSdk`. Android's own APIs are lint's, since the shell guards them.
 */
abstract class CheckPlatformApi : DefaultTask() {
    @get:InputFiles
    abstract val apk: DirectoryProperty

    @get:Internal
    abstract val loader: Property<com.android.build.api.variant.BuiltArtifactsLoader>

    @get:InputFile
    abstract val dexdump: RegularFileProperty

    @get:InputFile
    abstract val apiVersions: RegularFileProperty

    @get:Input
    abstract val minSdk: Property<Int>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @get:Inject
    abstract val exec: ExecOperations

    @TaskAction
    fun check() {
        val since = HashMap<String, Int>()
        val parents = HashMap<String, List<String>>()
        val xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(apiVersions.get().asFile)
        val classes = xml.getElementsByTagName("class")
        for (i in 0 until classes.length) {
            val element = classes.item(i) as org.w3c.dom.Element
            val name = element.getAttribute("name")
            val added = element.getAttribute("since").toIntOrNull() ?: 1
            since[name] = added
            val inherited = ArrayList<String>()
            val children = element.childNodes
            for (j in 0 until children.length) {
                val child = children.item(j) as? org.w3c.dom.Element ?: continue
                when (child.tagName) {
                    "method" -> since["$name.${child.getAttribute("name")}"] = child.getAttribute("since").toIntOrNull() ?: added
                    "extends", "implements" -> inherited += child.getAttribute("name")
                }
            }
            parents[name] = inherited
        }
        fun added(type: String, method: String): Int? {
            val queue = ArrayDeque(listOf(type))
            val seen = HashSet<String>()
            while (queue.isNotEmpty()) {
                val next = queue.removeFirst().takeIf(seen::add) ?: continue
                since["$next.$method"]?.let { return it }
                queue += parents[next].orEmpty()
            }
            return null
        }

        val apkFile = loader.get().load(apk.get())!!.elements.single().outputFile.let(::File)
        val scratch = temporaryDir.also { it.deleteRecursively(); it.mkdirs() }
        val invoke = Regex("""invoke-\w+(?:/range)? \{[^}]*}, L((?:java|javax)/[^;]+);\.([^:]+):(\([^)]*\)\S+)""")
        val owner = Regex("""^\s+Class descriptor\s+: 'L([^;]+);'""")
        val problems = sortedSetOf<String>()
        ZipFile(apkFile).use { zip ->
            for (entry in zip.entries().asSequence().filter { it.name.matches(Regex("classes\\d*\\.dex")) }) {
                val dex = File(scratch, entry.name).also { file -> zip.getInputStream(entry).use { file.outputStream().use(it::copyTo) } }
                // Only the parts of the package with Reflux's own classes in them.
                if (!dex.readBytes().decodeToString(throwOnInvalidSequence = false).contains("Ldev/reflux/")) continue
                val listing = File(scratch, entry.name + ".txt")
                listing.outputStream().use { out -> exec.exec { commandLine(dexdump.get().asFile.path, "-d", dex.path); standardOutput = out } }
                var current: String? = null
                listing.forEachLine { line ->
                    owner.find(line)?.let { current = it.groupValues[1]; return@forEachLine }
                    val caller = current?.takeIf { it.startsWith("dev/reflux/") } ?: return@forEachLine
                    val (type, method, descriptor) = invoke.find(line)?.destructured ?: return@forEachLine
                    val level = added(type, method + descriptor) ?: return@forEachLine
                    if (level > minSdk.get()) problems += "${type.replace('/', '.')}.$method$descriptor (Android $level) from ${caller.replace('/', '.')}"
                }
            }
        }
        report.get().asFile.writeText(problems.joinToString("\n"))
        if (problems.isNotEmpty()) {
            throw GradleException("Reflux calls Java methods that Android ${minSdk.get()} lacks:\n" + problems.joinToString("\n") { "  $it" })
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(generateTestMedia, GenerateTestMedia::output)
    }
    onVariants(selector().withBuildType("debug")) { variant ->
        val sdk = sdkComponents.sdkDirectory
        val check = tasks.register<CheckPlatformApi>("check${variant.name.replaceFirstChar(Char::uppercase)}PlatformApi") {
            apk.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK))
            loader.set(variant.artifacts.getBuiltArtifactsLoader())
            val tools = android.buildToolsVersion
            dexdump.set(sdk.map { it.file("build-tools/$tools/dexdump" + if (System.getProperty("os.name").startsWith("Windows")) ".exe" else "") })
            // The platform's folder is `android-37` or `android-37.0`, depending on how it was installed.
            apiVersions.set(sdk.map { dir ->
                listOf("", ".0").map { dir.file("platforms/android-${android.compileSdk}$it/data/api-versions.xml") }.firstOrNull { it.asFile.exists() }
                    ?: dir.file("platforms/android-${android.compileSdk}/data/api-versions.xml")
            })
            minSdk.set(variant.minSdk.apiLevel)
            report.set(layout.buildDirectory.file("reports/platformApi/${variant.name}.txt"))
        }
        tasks.named("check") { dependsOn(check) }
    }
}

// The engine modules are plain JVM libraries, shared with the desktop as they are. Their desktop database
// driver stays behind: Android has its own SQLite.
configurations.configureEach {
    exclude(group = "org.xerial", module = "sqlite-jdbc")
    exclude(group = "app.cash.sqldelight", module = "sqlite-driver")
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":sources:local"))
    implementation(project(":sources:jellyfin"))
    implementation(project(":sources:webdav"))
    implementation(project(":sources:smb"))
    implementation(project(":metadata:tmdb"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.androidx.activity.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    // The launcher's Watch Next row on Android TV and Google TV (DECISIONS.md, 2026-10-18).
    implementation(libs.androidx.tvprovider)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(kotlin("test-junit"))
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
