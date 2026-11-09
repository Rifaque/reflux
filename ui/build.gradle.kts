plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// The Android variant exists where there is an Android SDK to build it with (see settings.gradle.kts).
val refluxAndroid: Boolean by extra
if (refluxAndroid) apply(plugin = "com.android.kotlin.multiplatform.library")

/**
 * The About page's notices, generated from `legal/` (components.txt and the license texts beside it; and the
 * repository's own LICENSE once there is one), so the application shows exactly what is checked in.
 */
abstract class GenerateNotices : DefaultTask() {
    @get:InputDirectory
    abstract val legal: DirectoryProperty

    @get:InputFiles
    @get:Optional
    abstract val license: RegularFileProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun generate() {
        fun literal(text: String) = "\"" + text.replace("\r\n", "\n").replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("\n", "\\n") + "\""
        val lines = legal.file("components.txt").get().asFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split("|").map(String::trim) }
        val texts = legal.dir("licenses").get().asFile.listFiles { file -> file.name.endsWith(".txt") }!!.sortedBy { it.name }
        val known = texts.map { it.name }.toSet()
        val components = lines.map { (name, spdx, files, targets, _, note) ->
            val named = files.split(" ").filter(String::isNotEmpty)
            named.firstOrNull { it !in known }?.let { throw GradleException("legal/components.txt: $name names $it, which is not in legal/licenses") }
            val platforms = targets.split(" ").filter(String::isNotEmpty).joinToString(", ") { "NoticeTarget.${it.uppercase()}" }
            "        ThirdPartyComponent(${literal(name)}, ${literal(spdx)}, listOf(${named.joinToString(", ") { literal(it) }}), setOf($platforms), ${note.takeIf(String::isNotEmpty)?.let(::literal) ?: "null"}),"
        }
        val own = license.orNull?.asFile?.takeIf { it.exists() }?.readText()
        val file = output.get().file("dev/reflux/ui/legal/NoticeData.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                append("package dev.reflux.ui.legal\n\n")
                append("/** Generated from legal/ by the ui build (GenerateNotices); never edited. */\n")
                append("internal object NoticeData {\n")
                append("    val components: List<ThirdPartyComponent> = listOf(\n").append(components.joinToString("\n")).append("\n    )\n\n")
                append("    val refluxLicense: String? = ").append(own?.let(::literal) ?: "null").append("\n\n")
                append("    fun text(file: String): String? = when (file) {\n")
                texts.forEach { append("        ${literal(it.name)} -> Texts.").append(it.nameWithoutExtension.replace(Regex("[^A-Za-z0-9]"), "_").uppercase()).append('\n') }
                append("        else -> null\n    }\n\n")
                // Constants of their own object: loaded when a notice is opened, not with the About page.
                // A class file holds a constant of at most 64 KB, and some license texts are longer (the GCC runtime's),
                // so each text is kept in pieces and joined when it is read.
                append("    private object Texts {\n")
                texts.forEach { text ->
                    val name = text.nameWithoutExtension.replace(Regex("[^A-Za-z0-9]"), "_").uppercase()
                    val pieces = text.readText().chunked(12_000)
                    pieces.forEachIndexed { i, piece -> append("        private const val ${name}_$i = ").append(literal(piece)).append('\n') }
                    append("        val $name: String get() = arrayOf(${pieces.indices.joinToString(", ") { "${name}_$it" }}).joinToString(\"\")\n")
                }
                append("    }\n}\n")
            },
        )
    }

    private operator fun <T> List<T>.component6(): T = this[5]
}

val generateNotices by tasks.registering(GenerateNotices::class) {
    legal.set(rootProject.layout.projectDirectory.dir("legal"))
    license.set(rootProject.layout.projectDirectory.file("LICENSE"))
    output.set(layout.buildDirectory.dir("generated/notices"))
}

kotlin {
    jvmToolchain(21)
    jvm()
    if (refluxAndroid) {
        extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget> {
            namespace = "dev.reflux.ui"
            compileSdk = 37
            minSdk = 26
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateNotices)
        }
        commonMain.dependencies {
            api(project(":core"))
            api(project(":library"))
            api(project(":design"))
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            implementation(libs.compose.animation)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
            implementation(project(":sources:local"))
            implementation(project(":metadata:tmdb")) // its real attribution, for the About page's tests
        }
    }
}

tasks.withType<Test>().configureEach {
    // How long one test body may run (kotlinx-coroutines-test, which the Compose test harness runs on; its default is a
    // minute). Tests that move the clock past the player's controls hiding render every frame of it in software: about
    // 12 s on a desktop, 45 s in a 4-CPU container, more than a minute on GitHub's runner (M7's first hosted runs).
    // Nothing takes this long when it works, and a test that hangs still fails.
    systemProperty("kotlinx.coroutines.test.default_timeout", "5m")
    // -Preflux.screenshots=<dir> makes the screen tests save what they rendered, for visual review.
    providers.gradleProperty("reflux.screenshots").orNull?.let { systemProperty("reflux.screenshots", it) }
    // -Preflux.showcase=<dir> renders the website's product images (WebsiteShowcaseTest) from the artwork in <dir>/art.
    providers.gradleProperty("reflux.showcase").orNull?.let { systemProperty("reflux.showcase", it) }
}
