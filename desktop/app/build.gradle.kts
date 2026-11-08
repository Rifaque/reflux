import java.io.OutputStream
import java.net.URI
import java.security.MessageDigest
import java.io.File
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":ui"))
    implementation(project(":desktop:engine"))
    implementation(compose.desktop.currentOs)
    implementation(libs.jna)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":sources:local"))
}

// The commit this build is from (docs/VERSIONING.md, "Build metadata"), read by the About page and the
// diagnostics log. The version itself comes from core's generated RefluxBuild.
val generateBuildIdentity by tasks.registering {
    @Suppress("UNCHECKED_CAST")
    val identity = rootProject.extra["reflux.gitIdentity"] as Provider<String>
    val output = layout.buildDirectory.dir("generated/buildIdentity")
    inputs.property("identity", identity)
    outputs.dir(output)
    doLast {
        val file = output.get().file("dev/reflux/desktop/build-identity.properties").asFile
        file.parentFile.mkdirs()
        file.writeText(identity.get())
    }
}
sourceSets.main { resources.srcDir(generateBuildIdentity) }

// Release packaging (docs/RELEASE.md, "Desktop artifacts"). The version and every number derived from it come
// from the root build (docs/VERSIONING.md); nothing here declares one.
val refluxVersion = rootProject.extra["reflux.version"] as String
val windows = System.getProperty("os.name").startsWith("Windows")
val libmpvPin = Properties().apply { file("packaging/libmpv-windows.properties").reader().use(::load) }

/**
 * The Windows libmpv, fetched once from its pinned address and refused unless its SHA-256 is the pinned one. Only
 * `libmpv-2.dll` is kept, beside the LGPL text and the source offer, in the folder the packaged app reads its
 * resources from. Linux uses the distribution's libmpv.
 */
abstract class FetchLibmpv : DefaultTask() {
    @get:Input abstract val url: Property<String>
    @get:Input abstract val sha256: Property<String>
    @get:Input abstract val asset: Property<String>
    @get:OutputDirectory abstract val output: DirectoryProperty
    @get:Internal abstract val cache: DirectoryProperty
    @get:Inject abstract val exec: ExecOperations

    @TaskAction
    fun fetch() {
        val archive = cache.get().file(asset.get()).asFile
        fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        if (!archive.exists() || digest(archive) != sha256.get()) {
            archive.parentFile.mkdirs()
            URI(url.get()).toURL().openStream().use { input -> archive.outputStream().use { input.copyTo(it) } }
        }
        val actual = digest(archive)
        if (actual != sha256.get()) throw GradleException("libmpv archive ${asset.get()} has SHA-256 $actual, pinned ${sha256.get()}")
        val unpacked = temporaryDir.resolve("unpacked").apply { deleteRecursively(); mkdirs() }
        // 7-Zip where there is one (CI); otherwise Windows' own tar, which reads 7z through libarchive.
        val sevenZip = listOf("7z", "7z.exe", "C:\\Program Files\\7-Zip\\7z.exe").firstOrNull { name ->
            runCatching { exec.exec { commandLine(name, "i"); standardOutput = OutputStream.nullOutputStream(); isIgnoreExitValue = true } }.isSuccess
        }
        exec.exec {
            if (sevenZip != null) commandLine(sevenZip, "x", "-y", "-o" + unpacked.path, archive.path)
            else commandLine("tar", "-xf", archive.path, "-C", unpacked.path)
            standardOutput = OutputStream.nullOutputStream()
        }
        val target = output.get().dir("windows-x64").asFile.apply { deleteRecursively(); mkdirs() }
        unpacked.walk().first { it.name == "libmpv-2.dll" }.copyTo(target.resolve("libmpv-2.dll"))
    }
}

val fetchLibmpv = tasks.register<FetchLibmpv>("fetchLibmpv") {
    group = "release"
    description = "Fetches the pinned LGPL libmpv for Windows packages and verifies its SHA-256."
    url.set(libmpvPin.getProperty("url"))
    sha256.set(libmpvPin.getProperty("sha256"))
    asset.set(libmpvPin.getProperty("asset"))
    output.set(layout.buildDirectory.dir("packaging/resources"))
    cache.set(rootProject.layout.projectDirectory.dir(".reflux/cache"))
}

compose.desktop {
    application {
        mainClass = "dev.reflux.desktop.MainKt"
        // The Java runtime packed with the application: a pinned LTS (Eclipse Temurin 21) from the Gradle toolchain,
        // provisioned the same way on every machine, never whichever JDK happens to be installed.
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.ADOPTIUM)
        }.get().metadata.installationPath.asFile.absolutePath
        nativeDistributions {
            packageName = "Reflux"
            vendor = "RSX Develops"
            description = "Reflux, a media player for your own media"
            copyright = "Copyright (c) RSX Develops. All rights reserved."
            // The JDK modules the application reaches (suggestRuntimeModules), in the runtime packed with it.
            modules("java.instrument", "java.naming", "java.net.http", "java.security.jgss", "java.sql", "jdk.unsupported")
            appResourcesRootDir.set(layout.buildDirectory.dir("packaging/resources"))
            windows {
                packageVersion = rootProject.extra["reflux.windowsVersion"] as String
                iconFile.set(file("packaging/reflux.ico"))
                menuGroup = "Reflux"
                // Fixed for the life of the product, so a later installer replaces this one.
                upgradeUuid = "4f0d1d0e-5d2b-4c7e-9a52-7f3b6c1e8a21"
            }
            linux {
                packageVersion = rootProject.extra["reflux.linuxVersion"] as String
                iconFile.set(file("packaging/reflux.png"))
                packageName = "reflux"
            }
        }
    }
}

if (windows) tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(fetchLibmpv) }

/**
 * The desktop release archive for this machine's OS (docs/RELEASE.md, "Desktop artifacts"): the application with its
 * own Java runtime (and, on Windows, libmpv), Reflux's notice, the third-party license texts, and the libmpv
 * source offer, named `reflux-<version>-<os>-x64`. Not minified: the desktop keeps JNA's interfaces as they are.
 */
val packageDesktopArchive = tasks.register("packageDesktopArchive") {
    group = "release"
    description = "Packs the desktop distributable into build/release/reflux-<version>-<os>-x64.(zip|tar.gz)."
    dependsOn("createDistributable")
    val windows = windows
    val image = layout.buildDirectory.dir("compose/binaries/main/app/Reflux")
    val name = "reflux-$refluxVersion-${if (windows) "windows" else "linux"}-x64"
    val output = layout.buildDirectory.file("release/$name" + if (windows) ".zip" else ".tar.gz")
    val legal = rootProject.layout.projectDirectory.dir("legal")
    val notice = rootProject.layout.projectDirectory.file("LICENSE")
    val offer = layout.projectDirectory.file("packaging/libmpv-SOURCE-OFFER.txt")
    inputs.dir(image); inputs.dir(legal); inputs.file(notice); inputs.file(offer)
    outputs.file(output)
    doLast {
        val staging = temporaryDir.resolve(name).apply { deleteRecursively(); mkdirs() }
        image.get().asFile.copyRecursively(staging)
        // copyRecursively keeps no permissions: the launcher, the runtime's tools, and its helpers must stay executable.
        image.get().asFile.walkTopDown().filter { it.isFile && it.canExecute() }.forEach { source ->
            staging.resolve(source.relativeTo(image.get().asFile)).setExecutable(true, false)
        }
        if (!windows && !staging.resolve("bin/Reflux").canExecute()) throw GradleException("bin/Reflux is not executable in the archive")
        notice.asFile.copyTo(staging.resolve("LICENSE.txt"))
        legal.asFile.copyRecursively(staging.resolve("third-party"))
        if (windows) offer.asFile.copyTo(staging.resolve("third-party/libmpv-SOURCE-OFFER.txt"))
        val archive = output.get().asFile.apply { parentFile.mkdirs(); delete() }
        if (windows) {
            // Fixed timestamps and order, so the same tree packs to the same bytes.
            ZipOutputStream(archive.outputStream()).use { zip ->
                staging.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(staging.parentFile).invariantSeparatorsPath }.forEach { file ->
                    val entry = ZipEntry(file.relativeTo(staging.parentFile).invariantSeparatorsPath).apply { time = 315532800000L }
                    zip.putNextEntry(entry); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
        } else {
            val tar = ProcessBuilder("tar", "--sort=name", "--mtime=1980-01-01", "--owner=0", "--group=0", "--numeric-owner", "-czf", archive.path, "-C", staging.parentFile.path, name)
                .inheritIO().start()
            if (tar.waitFor() != 0) throw GradleException("tar failed")
        }
        println("packed ${archive.path}")
    }
}

tasks.withType<Test>().configureEach {
    // Tests that open real windows run only on request: -Preflux.windowTests=true (and -Preflux.controller=true
    // for the one that needs a person holding a controller).
    for (name in listOf("reflux.windowTests", "reflux.screenshots", "reflux.controller")) {
        providers.gradleProperty(name).orNull?.let { systemProperty(name, it) }
    }
}
