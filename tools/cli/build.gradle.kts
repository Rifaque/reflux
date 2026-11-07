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
    implementation(project(":desktop:engine"))
    implementation(project(":sources:jellyfin"))
    implementation(project(":sources:plex"))
    implementation(project(":sources:webdav"))
    implementation(project(":sources:smb"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
}
