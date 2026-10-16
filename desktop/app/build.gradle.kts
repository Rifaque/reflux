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
