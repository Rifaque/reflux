rootProject.name = "reflux"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

plugins {
    // Downloads the JDK 21 toolchain when the machine has none, so the wrapper is the only requirement.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Compose Multiplatform depends on AndroidX libraries published only here.
        google {
            mavenContent {
                includeGroupByRegex("""androidx\..*""")
                includeGroupByRegex("""com\.google\..*""")
                includeGroupByRegex("""com\.android\..*""")
            }
        }
    }
}

// The Android client needs the Android SDK. Nothing else does, so a machine without one builds everything
// but the Android modules and the Android variant of the shared UI (-Preflux.android=false does the same).
val androidSdk = listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").firstNotNullOfOrNull { providers.environmentVariable(it).orNull }
    ?: providers.fileContents(layout.rootDirectory.file("local.properties")).asText.orNull?.lineSequence()?.firstOrNull { it.trim().startsWith("sdk.dir") }
val refluxAndroid = androidSdk != null && providers.gradleProperty("reflux.android").orNull != "false"
gradle.beforeProject { extensions.extraProperties["refluxAndroid"] = refluxAndroid }

include(":core")
include(":library")
include(":design")
include(":sources:local")
include(":sources:jellyfin")
include(":sources:webdav")
include(":sources:smb")
include(":sources:plex")
include(":playback:mpv")
include(":metadata:tmdb")
include(":ui")
include(":desktop:engine")
include(":desktop:app")
include(":tools:cli")
if (refluxAndroid) include(":android:app")
