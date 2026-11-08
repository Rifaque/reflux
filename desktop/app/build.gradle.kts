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
// The metadata provider's application credential, when the build environment gives one (REFLUX_TMDB_TOKEN, a CI
// secret; DECISIONS.md, "TMDB: working non-commercial classification"). Never in the repository; absent, the packaged
// app makes no lookups.
val generateProviderCredential by tasks.registering {
    val token = providers.environmentVariable("REFLUX_TMDB_TOKEN").orElse(providers.gradleProperty("reflux.tmdb.token")).orElse("")
    val output = layout.buildDirectory.dir("generated/provider")
    inputs.property("digest", token.map { value -> MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { b -> "%02x".format(b) } })
    outputs.dir(output)
    doLast {
        val file = output.get().file("dev/reflux/desktop/provider.properties").asFile
        file.parentFile.mkdirs()
        if (token.get().isBlank()) file.delete() else file.writeText("tmdb=" + token.get().trim() + System.lineSeparator())
    }
}
sourceSets.main { resources.srcDir(generateBuildIdentity); resources.srcDir(generateProviderCredential) }

// Release packaging (docs/RELEASE.md, "Desktop artifacts"). The version and every number derived from it come
// from the root build (docs/VERSIONING.md); nothing here declares one.
val refluxVersion = rootProject.extra["reflux.version"] as String
val windows = System.getProperty("os.name").startsWith("Windows")
val libmpvPin = Properties().apply { file("packaging/libmpv-windows.properties").reader().use(::load) }

/**
 * The Windows libmpv: Reflux's own build (tools/libmpv), taken from `build/libmpv` (or `-Preflux.libmpv=<dir>`) and
 * refused unless its SHA-256 is the pinned one, beside its source bundle, refused likewise. Only `libmpv-2.dll` goes
 * into the folder the packaged app reads its resources from. Linux uses the distribution's libmpv.
 */
abstract class PrepareLibmpv : DefaultTask() {
    @get:InputFiles abstract val built: DirectoryProperty
    @get:Input abstract val dllSha256: Property<String>
    @get:Input abstract val sourceSha256: Property<String>
    @get:OutputDirectory abstract val output: DirectoryProperty

    @TaskAction
    fun prepare() {
        fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        val folder = built.get().asFile
        val how = "build it with tools/libmpv (tools/libmpv/README.md) into ${folder.path}"
        val dll = folder.resolve("libmpv-2.dll").takeIf { it.isFile } ?: throw GradleException("No libmpv-2.dll: $how")
        val source = folder.resolve("libmpv-source.tar.xz").takeIf { it.isFile } ?: throw GradleException("No libmpv-source.tar.xz beside it: $how")
        digest(dll).let { if (it != dllSha256.get()) throw GradleException("libmpv-2.dll has SHA-256 $it, pinned ${dllSha256.get()}: $how") }
        digest(source).let { if (it != sourceSha256.get()) throw GradleException("libmpv-source.tar.xz has SHA-256 $it, pinned ${sourceSha256.get()}: $how") }
        val target = output.get().dir("windows-x64").asFile.apply { deleteRecursively(); mkdirs() }
        dll.copyTo(target.resolve("libmpv-2.dll"))
    }
}

val prepareLibmpv = tasks.register<PrepareLibmpv>("prepareLibmpv") {
    group = "release"
    description = "Takes Reflux's own LGPL libmpv build for Windows packages and verifies it and its source against the pins."
    built.set(providers.gradleProperty("reflux.libmpv").map { rootProject.layout.projectDirectory.dir(it) }
        .orElse(rootProject.layout.buildDirectory.dir("libmpv")))
    dllSha256.set(libmpvPin.getProperty("dllSha256"))
    sourceSha256.set(libmpvPin.getProperty("sourceSha256"))
    output.set(layout.buildDirectory.dir("packaging/resources"))
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

if (windows) tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(prepareLibmpv) }

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
