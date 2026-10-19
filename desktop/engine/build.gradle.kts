plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core"))
    api(project(":library"))
    api(project(":playback:mpv"))
    implementation(project(":sources:local"))
    implementation(project(":sources:jellyfin"))
    implementation(project(":sources:webdav"))
    implementation(project(":sources:smb"))
    implementation(project(":metadata:tmdb"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.jna) // the Windows Credential Manager, for source connections
    runtimeOnly(libs.slf4j.nop) // smbj logs through SLF4J; shells report problems themselves
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json) // reading a live server's answers in tests
}
