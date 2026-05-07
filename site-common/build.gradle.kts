plugins {
    alias(libs.plugins.kotlinJvm)
    `maven-publish`
}

description = "Kensa site-mode shared shell extraction utilities"

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
