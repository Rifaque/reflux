rootProject.name = "reflux"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":core")
include(":library")
include(":sources:local")
include(":sources:jellyfin")
include(":playback:mpv")
include(":metadata:tmdb")
include(":tools:cli")
