plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(project(":library"))
            api(project(":design"))
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            implementation(libs.compose.animation)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
            implementation(project(":sources:local"))
        }
    }
}

tasks.withType<Test>().configureEach {
    // -Preflux.screenshots=<dir> makes the screen tests save what they rendered, for visual review.
    providers.gradleProperty("reflux.screenshots").orNull?.let { systemProperty("reflux.screenshots", it) }
}
