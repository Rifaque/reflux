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

compose.desktop {
    application {
        mainClass = "dev.reflux.desktop.MainKt"
    }
}

tasks.withType<Test>().configureEach {
    // Tests that open real windows run only on request: -Preflux.windowTests=true (and -Preflux.controller=true
    // for the one that needs a person holding a controller).
    for (name in listOf("reflux.windowTests", "reflux.screenshots", "reflux.controller")) {
        providers.gradleProperty(name).orNull?.let { systemProperty(name, it) }
    }
}
