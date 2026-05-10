plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.buildConfig)
    `java-gradle-plugin`
    alias(libs.plugins.gradlePluginPublish)
}

description = "Gradle plugin for Kensa compiler plugin integration and site-mode wiring"

// Default kensa-core (and kensa-compiler-plugin) coordinate this plugin pairs with — used as
// the convention for `kensa.kensaCoreVersion` when the consumer doesn't override it.
// Independent of `version.txt`: the plugin and kensa-core release on their own cadences.
val kensaCoreVersion = rootProject.file("kensa-core-version.txt").readText().trim()

// Lower bound for the kensa-core override. Versions below this are rejected at apply time
// (the plugin can't guarantee runtime/compiler-plugin compatibility with older kensa-cores).
val minKensaCoreVersion = rootProject.file("kensa-core-min-version.txt").readText().trim()

buildConfig {
    packageName("dev.kensa.gradle")
    useKotlinOutput { topLevelConstants = true }
    buildConfigField("KENSA_VERSION", provider { "${project.version}" })
    buildConfigField("KENSA_CORE_VERSION", kensaCoreVersion)
    buildConfigField("MIN_KENSA_CORE_VERSION", minKensaCoreVersion)
    buildConfigField("MIN_KOTLIN_VERSION", libs.versions.kotlin.get())
}

dependencies {
    // site-common's classes are bundled into the plugin jar (see jar task below) — declared
    // compileOnly so it does NOT appear as a runtime dep in the published POM. Consumers
    // of the plugin only see one artifact and don't need site-common to be on Central.
    compileOnly(project(":site-common"))
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api")
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable")
    compileOnly(gradleApi())

    testImplementation(project(":site-common"))
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
    // Single source of truth: tests publish their fake kensa-core at the same coordinates
    // the plugin will resolve, so a bump to kensa-core-version.txt only changes one file.
    systemProperty("kensa.core.version", kensaCoreVersion)
}

dependencies {
    "functionalTestImplementation"(project(":site-common"))
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
            website.set("https://kensa.dev/docs/build-plugins/gradle-plugin")
            vcsUrl.set("https://github.com/kensa-dev/build-plugins")
            tags.set(listOf("kotlin", "compiler-plugin", "testing", "kensa"))
        }
    }
}

val smokeTestRepoDir = layout.buildDirectory.dir("smoke-test-repo")

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            url.set("https://kensa.dev/docs/build-plugins/gradle-plugin")
        }
    }
    repositories {
        maven {
            name = "smokeTest"
            url = uri(smokeTestRepoDir)
        }
    }
}

// Verifies the *published* plugin (POM + module metadata + jar) resolves cleanly from a
// real Maven repo. Functional tests use withPluginClasspath() which injects the local
// runtime classpath and hides missing transitive deps — so they passed despite the
// site-common dep being unresolvable for real consumers (the clearwave-example failure).
val smokeTest by sourceSets.creating
val smokeTestTask = tasks.register<Test>("smokeTest") {
    description = "Smoke-tests the published plugin from a local Maven repo (catches missing transitive deps)."
    group = "verification"
    testClassesDirs = smokeTest.output.classesDirs
    classpath = smokeTest.runtimeClasspath
    useJUnitPlatform()
    dependsOn("publishAllPublicationsToSmokeTestRepository")
    systemProperty("smoke.test.repo", smokeTestRepoDir.get().asFile.absolutePath)
    systemProperty("smoke.test.plugin.version", project.version.toString())
}

dependencies {
    "smokeTestImplementation"(libs.junit.jupiter)
    "smokeTestImplementation"(libs.kotest.assertions.core)
    "smokeTestImplementation"(gradleTestKit())
    "smokeTestRuntimeOnly"(libs.junit.platform.launcher)
}

tasks.named("check") {
    dependsOn(smokeTestTask)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

// site-common is bundled into the plugin jar (declared compileOnly) so the plugin ships
// as a single self-contained artifact — no need to publish site-common to a public repo.
val siteCommonMain = project(":site-common").extensions
    .getByType<JavaPluginExtension>().sourceSets.named("main")

tasks.named<Jar>("jar") {
    from(siteCommonMain.map { it.output })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// `withPluginClasspath()` reads the plugin classpath from pluginUnderTestMetadata, which
// derives from main's runtimeClasspath. Since site-common is compileOnly here, add its
// classes explicitly so functional tests see the same classpath as a real plugin install.
tasks.named<org.gradle.plugin.devel.tasks.PluginUnderTestMetadata>("pluginUnderTestMetadata") {
    pluginClasspath.from(siteCommonMain.map { it.output })
}

// Shell resources (kensa.js, logo.svg) are NOT bundled into the plugin jar.
// AssembleKensaSiteTask resolves dev.kensa:kensa-core at task-execution time and extracts
// kensa.js / logo.svg from that jar. This decouples kensa-core releases from plugin releases:
// updating the kensa UI no longer requires republishing the gradle plugin to refresh the bundled shell.
