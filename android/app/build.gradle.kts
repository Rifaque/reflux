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
    }

    // A release is signed only with a key that lives outside the repository (docs/RELEASE.md, "Signing"): a
    // keystore named by REFLUX_SIGNING_STORE_FILE, or `reflux.signing.*` properties in the user's own
    // ~/.gradle/gradle.properties. Without one, a release builds unsigned, and the release tooling refuses it.
    val signing = listOf("STORE_FILE", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD").associateWith { key ->
        providers.environmentVariable("REFLUX_SIGNING_$key").orNull
            ?: providers.gradleProperty("reflux.signing." + key.lowercase().replace('_', '.')).orNull
    }
    if (signing.values.all { !it.isNullOrBlank() }) {
        signingConfigs.create("release") {
            storeFile = file(signing.getValue("STORE_FILE")!!)
            storePassword = signing.getValue("STORE_PASSWORD")
            keyAlias = signing.getValue("KEY_ALIAS")
            keyPassword = signing.getValue("KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            // The metadata provider's credential reaches development builds only: public builds carry no TMDB
            // credential until its terms are settled (DECISIONS.md, 2026-11-08, "TMDB in public releases").
            (providers.gradleProperty("reflux.tmdb.token").orNull ?: providers.environmentVariable("REFLUX_TMDB_TOKEN").orNull)
                ?.takeIf { it.isNotBlank() }?.let { resValue("string", "reflux_tmdb_token", it) }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
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
        // English in DTS and French in AAC: what tells a plan's track from whatever else would play.
        exec.exec {
            commandLine(
                "ffmpeg", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "testsrc=size=320x180:rate=5", "-f", "lavfi", "-i", "sine=frequency=440", "-f", "lavfi", "-i", "sine=frequency=660",
                "-t", "20", "-map", "0:v", "-map", "1:a", "-map", "2:a",
                "-c:v", "libx264", "-preset", "ultrafast", "-crf", "38", "-pix_fmt", "yuv420p", "-g", "5",
                "-c:a:0", "dca", "-strict", "-2", "-ar", "48000", "-ac", "2", "-c:a:1", "aac", "-b:a:1", "64k",
                "-metadata:s:a:0", "language=eng", "-metadata:s:a:1", "language=fre", output.get().file("tracks.mkv").asFile.path,
            )
        }
        // Picture with DTS sound, which most devices cannot decode: what silent playback is tested with.
        exec.exec {
            commandLine(
                "ffmpeg", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "testsrc=size=320x180:rate=5", "-f", "lavfi", "-i", "sine=frequency=440",
                "-t", "20", "-c:v", "libx264", "-preset", "ultrafast", "-crf", "38", "-pix_fmt", "yuv420p", "-g", "5",
                "-c:a", "dca", "-strict", "-2", "-ar", "48000", "-ac", "2", output.get().file("dts.mkv").asFile.path,
            )
        }
        // A plain Matroska file, and the same file laid out as some real ones are: its first SeekHead only points to a
        // second one at the end, which is the one that names the cues. Valid, and the same media byte for byte.
        val seekable = output.get().file("seekable.mkv").asFile
        exec.exec {
            commandLine(
                "ffmpeg", "-loglevel", "error", "-y", "-f", "lavfi", "-i", "testsrc=size=320x180:rate=5", "-f", "lavfi", "-i", "sine=frequency=440",
                "-t", "60", "-c:v", "libx264", "-preset", "ultrafast", "-crf", "38", "-pix_fmt", "yuv420p", "-g", "5", "-c:a", "aac", "-b:a", "32k", seekable.path,
            )
        }
        output.get().file("chained-seekhead.mkv").asFile.writeBytes(chainSeekHead(seekable.readBytes()))
        // Formats the real library lacks, each answering one question (docs/PLAYBACK.md, section 16). The desktop's
        // FormatFixtures makes the same files from the same arguments.
        for ((name, arguments) in formatFixtures) {
            exec.exec { commandLine(listOf("ffmpeg", "-loglevel", "error", "-y") + arguments + output.get().file(name).asFile.path) }
        }
    }

    private val mastering = "master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1):max-cll=1000,400"
    private fun picture(size: String, rate: Int, seconds: Int) = listOf("-f", "lavfi", "-i", "testsrc2=size=$size:rate=$rate", "-f", "lavfi", "-i", "sine=frequency=440", "-t", "$seconds")
    private val aac = listOf("-c:a", "aac", "-ac", "2", "-b:a", "64k")
    private val formatFixtures = listOf(
        "av1-1080p-8bit.mkv" to picture("1920x1080", 24, 2) + listOf("-c:v", "libaom-av1", "-cpu-used", "8", "-row-mt", "1", "-b:v", "400k", "-pix_fmt", "yuv420p") + aac,
        "av1-1080p-10bit.mkv" to picture("1920x1080", 24, 2) + listOf("-c:v", "libaom-av1", "-cpu-used", "8", "-row-mt", "1", "-b:v", "400k", "-pix_fmt", "yuv420p10le") + aac,
        "av1-2160p-10bit.mkv" to picture("3840x2160", 5, 1) + listOf("-c:v", "libaom-av1", "-cpu-used", "8", "-row-mt", "1", "-tiles", "2x2", "-b:v", "800k", "-pix_fmt", "yuv420p10le") + aac,
        "hevc-2160p-hdr10.mkv" to picture("3840x2160", 5, 1) + listOf(
            "-c:v", "libx265", "-preset", "ultrafast", "-pix_fmt", "yuv420p10le", "-color_primaries", "bt2020", "-color_trc", "smpte2084", "-colorspace", "bt2020nc",
            "-x265-params", "log-level=error:colorprim=bt2020:transfer=smpte2084:colormatrix=bt2020nc:hdr10=1:$mastering",
        ) + aac,
        "hevc-2160p-hlg.mkv" to picture("3840x2160", 5, 1) + listOf(
            "-c:v", "libx265", "-preset", "ultrafast", "-pix_fmt", "yuv420p10le", "-color_primaries", "bt2020", "-color_trc", "arib-std-b67", "-colorspace", "bt2020nc",
            "-x265-params", "log-level=error:colorprim=bt2020:transfer=arib-std-b67:colormatrix=bt2020nc",
        ) + aac,
        "truehd.mkv" to picture("320x180", 10, 2) + listOf("-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", "-c:a", "truehd", "-strict", "-2", "-ar", "48000", "-ac", "2"),
        "vp9-1080p.webm" to picture("1920x1080", 24, 2) + listOf("-c:v", "libvpx-vp9", "-deadline", "realtime", "-cpu-used", "8", "-b:v", "400k", "-pix_fmt", "yuv420p", "-c:a", "libopus", "-b:a", "64k"),
    )

    private fun chainSeekHead(file: ByteArray): ByteArray {
        fun find(id: Int, from: Int = 0): Int = (from until file.size - 4).first { at -> (0..3).all { file[at + it] == (id ushr (24 - 8 * it)).toByte() } }
        fun length(at: Int): Int = Integer.numberOfLeadingZeros(file[at].toInt() and 0xFF) - 23
        val segmentSize = find(0x18538067) + 4
        val data = segmentSize + length(segmentSize)
        val head = find(0x114D9B74, data)
        check(head == data && length(segmentSize) == 8) { "ffmpeg laid this file out differently" }
        val sizeBytes = length(head + 4)
        val body = (1 until sizeBytes).fold(file[head + 4].toInt() and (0xFF ushr sizeBytes)) { size, it -> (size shl 8) or (file[head + 4 + it].toInt() and 0xFF) }
        val headEnd = head + 4 + sizeBytes + body
        val tail = file.copyOfRange(head, headEnd)
        val tailAt = (file.size - data).toLong()
        val seekId = byteArrayOf(0x53, 0xAB.toByte(), 0x84.toByte(), 0x11, 0x4D, 0x9B.toByte(), 0x74)
        val seekPosition = byteArrayOf(0x53, 0xAC.toByte(), 0x88.toByte()) + ByteArray(8) { (tailAt ushr (56 - 8 * it)).toByte() }
        val seek = byteArrayOf(0x4D, 0xBB.toByte(), (0x80 or (seekId.size + seekPosition.size)).toByte()) + seekId + seekPosition
        val chain = byteArrayOf(0x11, 0x4D, 0x9B.toByte(), 0x74, (0x80 or seek.size).toByte()) + seek
        val rest = headEnd - head - chain.size - 2
        check(rest in 0..126) { "no room for the chain" }
        val out = file + tail
        chain.copyInto(out, head)
        out[head + chain.size] = 0xEC.toByte()
        out[head + chain.size + 1] = (0x80 or rest).toByte()
        out.fill(0, head + chain.size + 2, headEnd)
        val size = (out.size - data).toLong()
        for (at in 1..7) out[segmentSize + at] = (size ushr (56 - 8 * at)).toByte()
        return out
    }
}

val generateTestMedia = tasks.register<GenerateTestMedia>("generateTestMedia") {
    output.set(layout.buildDirectory.dir("generated/testMedia"))
}

/** The commit this build is from (docs/VERSIONING.md, "Build metadata"), as an asset the About page reads. */
abstract class GenerateBuildIdentity : DefaultTask() {
    @get:Input
    abstract val identity: Property<String>

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun generate() {
        output.get().file("build-identity.properties").asFile.writeText(identity.get())
    }
}

val generateBuildIdentity = tasks.register<GenerateBuildIdentity>("generateBuildIdentity") {
    @Suppress("UNCHECKED_CAST")
    identity.set(rootProject.extra["reflux.gitIdentity"] as Provider<String>)
    output.set(layout.buildDirectory.dir("generated/buildIdentity"))
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
        variant.sources.assets?.addGeneratedSourceDirectory(generateBuildIdentity, GenerateBuildIdentity::output)
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
    implementation(project(":sources:plex"))
    implementation(project(":sources:webdav"))
    implementation(project(":sources:smb"))
    implementation(project(":metadata:tmdb"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.androidx.activity.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls) // a server's converted stream; DefaultMediaSourceFactory finds it by itself
    implementation(libs.media3.session)
    // The launcher's Watch Next row on Android TV and Google TV (DECISIONS.md, 2026-10-18).
    implementation(libs.androidx.tvprovider)
    // WebDAV's PROPFIND, which HttpURLConnection refuses; used by the WebDAV transport only (DECISIONS.md, 2026-10-18).
    implementation(libs.okhttp)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(kotlin("test-junit"))
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
