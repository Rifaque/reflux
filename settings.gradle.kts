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
include(":design")
include(":sources:local")
include(":sources:jellyfin")
include(":sources:webdav")
include(":playback:mpv")
include(":metadata:tmdb")
include(":tools:cli")
