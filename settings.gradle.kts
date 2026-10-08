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

include(":core")
include(":library")
include(":design")
include(":sources:local")
include(":sources:jellyfin")
include(":sources:webdav")
include(":sources:smb")
include(":playback:mpv")
include(":metadata:tmdb")
include(":ui")
include(":desktop:engine")
include(":desktop:app")
include(":tools:cli")
