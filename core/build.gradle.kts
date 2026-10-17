plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// What this build is, for code that reports it (an About page, a server's client list). Generated from the one
// declared version (docs/VERSIONING.md), so no source file declares a version of its own.
val generateBuildInfo by tasks.registering {
    val version = providers.gradleProperty("reflux.version")
    val output = layout.buildDirectory.dir("generated/buildInfo")
    inputs.property("version", version)
    outputs.dir(output)
    doLast {
        val file = output.get().file("dev/reflux/core/RefluxBuild.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package dev.reflux.core
            |
            |/** What this build of Reflux is (docs/VERSIONING.md). Generated from `reflux.version`; never edited. */
            |object RefluxBuild {
            |    const val VERSION: String = "${version.get()}"
            |}
            |""".trimMargin(),
        )
    }
}

kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildInfo)
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
