pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        maven(url = "https://repo.spring.io/release")
        maven(url = "https://repo.spring.io/snapshot")
    }
}

plugins {
    // Enable automatic JVM downloads via the Foojay resolver.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "book_review"
