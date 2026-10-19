plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

group = "osrsps"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

dependencies {
    // RSProt for revision 241; wired into login in the next step.
    implementation(libs.rsprot.api)
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.logback.classic)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("osrsps.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
