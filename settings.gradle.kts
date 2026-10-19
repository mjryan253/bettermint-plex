rootProject.name = "bettermint-plex"

include(":app")
include(":core:api")
include(":core:data")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
