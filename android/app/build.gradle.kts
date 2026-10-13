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
        versionCode = 1
        versionName = "0.1.0"
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

androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(generateTestMedia, GenerateTestMedia::output)
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
    implementation(project(":sources:jellyfin"))
    implementation(project(":sources:webdav"))
    implementation(project(":metadata:tmdb"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.androidx.activity.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(kotlin("test-junit"))
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
}
