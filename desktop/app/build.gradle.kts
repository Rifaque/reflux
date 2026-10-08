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
}

compose.desktop {
    application {
        mainClass = "dev.reflux.desktop.MainKt"
    }
}

tasks.withType<Test>().configureEach {
    // The end-to-end test opens a real window, so it runs only on request: -Preflux.windowTests=true
    for (name in listOf("reflux.windowTests", "reflux.screenshots")) {
        providers.gradleProperty(name).orNull?.let { systemProperty(name, it) }
    }
}
