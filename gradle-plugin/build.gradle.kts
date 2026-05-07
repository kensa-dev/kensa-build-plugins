plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.buildConfig)
    `java-gradle-plugin`
    alias(libs.plugins.gradlePluginPublish)
}

description = "Gradle plugin for Kensa compiler plugin integration and site-mode wiring"

// Version of kensa-core (and kensa-compiler-plugin) this plugin release targets. Lives in a
// sibling file to version.txt so a release can bump both lines in one commit. Distinct from the
// plugin's own version because build-plugins and kensa repo version independently.
val kensaCoreVersion = rootProject.file("kensa-core-version.txt").readText().trim()

buildConfig {
    packageName("dev.kensa.gradle")
    useKotlinOutput { topLevelConstants = true }
    buildConfigField("KENSA_VERSION", provider { "${project.version}" })
    buildConfigField("KENSA_CORE_VERSION", kensaCoreVersion)
    buildConfigField("MIN_KOTLIN_VERSION", libs.versions.kotlin.get())
}

dependencies {
    implementation(project(":site-common"))
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api")
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    compileOnly(gradleApi())

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(gradleApi())
    testImplementation(gradleTestKit())
    testRuntimeOnly(libs.junit.platform.launcher)
}

val functionalTest by sourceSets.creating
val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs Gradle TestKit functional tests."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
}

dependencies {
    "functionalTestImplementation"(libs.junit.jupiter)
    "functionalTestImplementation"(libs.kotest.assertions.core)
    "functionalTestImplementation"(gradleApi())
    "functionalTestImplementation"(gradleTestKit())
    "functionalTestRuntimeOnly"(libs.junit.platform.launcher)
}

tasks.named("check") {
    dependsOn(functionalTestTask)
}

gradlePlugin {
    testSourceSets(functionalTest)
    plugins {
        create("kensaPlugin") {
            id = "dev.kensa.gradle-plugin"
            implementationClass = "dev.kensa.gradle.KensaGradlePlugin"
            displayName = "Kensa Gradle Plugin"
            description = "Gradle plugin for Kensa compiler plugin integration"
            website.set("https://kensa.dev/docs/site-mode/gradle")
            vcsUrl.set("https://github.com/kensa-dev/build-plugins")
            tags.set(listOf("kotlin", "compiler-plugin", "testing", "kensa"))
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            url.set("https://kensa.dev/docs/site-mode/gradle")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

// Shell resources (kensa.js, logo.svg) are NOT bundled into the plugin jar.
// AssembleKensaSiteTask resolves dev.kensa:kensa-core at task-execution time and extracts
// kensa.js / logo.svg from that jar. This decouples kensa-core releases from plugin releases:
// updating the kensa UI no longer requires republishing the gradle plugin to refresh the bundled shell.
