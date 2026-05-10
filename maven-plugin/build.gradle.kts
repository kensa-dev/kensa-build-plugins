plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.mavenPluginDevelopment)
    `maven-publish`
}

description = "Maven plugin for assembling Kensa multi-source site bundles"

dependencies {
    // site-common's classes are bundled into the plugin jar (see jar task below) — declared
    // compileOnly so it does NOT appear as a runtime dep in the published POM.
    compileOnly(project(":site-common"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.core)
    compileOnly(libs.maven.plugin.annotations)

    testImplementation(project(":site-common"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.maven.invoker)
    testRuntimeOnly(libs.junit.platform.launcher)
}

mavenPlugin {
    artifactId.set("kensa-maven-plugin")
    name.set("Kensa Maven Plugin")
    description.set("Maven plugin for assembling Kensa multi-source site bundles")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// site-common is bundled into the plugin jar (declared compileOnly) so the plugin ships
// as a single self-contained artifact — no need to publish site-common to a public repo.
val siteCommonMain = project(":site-common").extensions
    .getByType<JavaPluginExtension>().sourceSets.named("main")

tasks.named<Jar>("jar") {
    from(siteCommonMain.map { it.output })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// The gradlex maven-plugin-development plugin only adds the Java classes dir to the
// mojo scanner, but our mojo is written in Kotlin. Replace classesDirs with the
// full set of main output class directories so the annotation-based extractor
// finds AssembleKensaSiteMojo. Supplying all dirs in one FileCollection means
// the scanner iterates a single merged classpath rather than one MavenProject
// per directory, avoiding DuplicateMojoDescriptorException for the help mojo.
tasks.named<org.gradlex.maven.plugin.development.task.GenerateMavenPluginDescriptorTask>(
    "generateMavenPluginDescriptor"
) {
    classesDirs.setFrom(sourceSets.named("main").map { it.output.classesDirs })
}

// kensa-core's UI bundle (kensa.js, logo.svg) is NOT bundled into the plugin jar.
// AssembleKensaSiteMojo resolves dev.kensa:kensa-core via Aether at execution time
// and extracts the shell from that jar. This decouples kensa-core releases from plugin
// releases — updating the kensa UI no longer requires republishing the maven plugin.
//
// We DO bundle two tiny resources: kensa-core-version.txt (the default the mojo uses for
// <kensaCoreVersion> when unset) and kensa-core-min-version.txt (the lower bound the mojo
// rejects overrides below at apply time). Users can override <kensaCoreVersion> on the
// mojo to pin a different kensa-core, within the supported range.
val kensaCoreVersion = rootProject.file("kensa-core-version.txt").readText().trim()
val minKensaCoreVersion = rootProject.file("kensa-core-min-version.txt").readText().trim()

val writeKensaCoreVersionResource = tasks.register("writeKensaCoreVersionResource") {
    val outputDir = layout.buildDirectory.dir("generated/resources/main")
    inputs.property("kensaCoreVersion", kensaCoreVersion)
    inputs.property("minKensaCoreVersion", minKensaCoreVersion)
    outputs.dir(outputDir)
    doLast {
        val versionFile = outputDir.get().file("META-INF/dev/kensa/kensa-core-version.txt").asFile
        versionFile.parentFile.mkdirs()
        versionFile.writeText(kensaCoreVersion)
        outputDir.get().file("META-INF/dev/kensa/kensa-core-min-version.txt").asFile
            .writeText(minKensaCoreVersion)
    }
}

sourceSets.named("main") {
    resources.srcDir(writeKensaCoreVersionResource)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "kensa-maven-plugin"
            from(components["java"])
        }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            url.set("https://kensa.dev/docs/build-plugins/maven-plugin")
        }
    }
}

// Resolve Maven home at configuration time so the invoker test can find the mvn executable.
// Priority: M2_HOME env var → MAVEN_HOME env var → resolve via `which mvn` (two dirs up from the binary).
val mavenHome: String = run {
    System.getenv("M2_HOME")
        ?: System.getenv("MAVEN_HOME")
        ?: ProcessBuilder("bash", "-c", "mvn -version 2>&1 | grep 'Maven home' | sed 's/Maven home: //'")
            .start()
            .inputStream.bufferedReader().readText()
            .trim()
            .ifEmpty {
                val mvnBin = ProcessBuilder("bash", "-c", "which mvn").start()
                    .inputStream.bufferedReader().readText().trim()
                File(mvnBin).parentFile?.parentFile?.absolutePath ?: ""
            }
}

tasks.named<Test>("test") {
    dependsOn("publishToMavenLocal")
    // site-common is bundled into the plugin jar — no separate maven-local publish needed.
    systemProperty("plugin.version", project.version.toString())
    systemProperty("kensa.core.version", kensaCoreVersion)
    systemProperty("maven.home", mavenHome)
}
