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
    implementation(project(":sources:webdav"))
    implementation(project(":sources:smb"))
    implementation(project(":metadata:tmdb"))
    implementation(project(":playback:mpv"))
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.slf4j.nop) // smbj logs through SLF4J; the CLI reports problems itself
    testImplementation(kotlin("test"))
}
