plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(libs.versions.java.jdk.get().toInt())
}

dependencies {
    testImplementation(libs.junit)
}
