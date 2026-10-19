plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.bettermint.plex.data"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

dependencies {
    api(project(":core:api"))
    api(libs.androidx.paging.common)
    implementation(libs.androidx.datastore.preferences)
}
