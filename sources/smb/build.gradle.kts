plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.smbj)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
