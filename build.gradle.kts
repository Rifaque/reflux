plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

// Developers can drop libmpv into .reflux/native (ignored by Git) instead of installing it system-wide.
val developmentNatives = rootDir.resolve(".reflux/native").takeIf { it.isDirectory }?.absolutePath

subprojects {
    if (developmentNatives != null) {
        tasks.withType<Test>().configureEach { systemProperty("jna.library.path", developmentNatives) }
        tasks.withType<JavaExec>().configureEach { systemProperty("jna.library.path", developmentNatives) }
    }
}
