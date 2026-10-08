plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("dev.reflux.cli.MainKt")
    applicationName = "reflux"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":library"))
    implementation(project(":sources:local"))
    implementation(project(":sources:jellyfin"))
    implementation(project(":metadata:tmdb"))
    implementation(project(":playback:mpv"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}
