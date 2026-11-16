// Maven Central rate-limits shared CI runners (HTTP 429), so plugins and dependencies come from Google's mirror of
// Central first, with the Plugin Portal and Central itself after it for anything the mirror lacks.
pluginManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
    }
}

rootProject.name = "osrsps"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
