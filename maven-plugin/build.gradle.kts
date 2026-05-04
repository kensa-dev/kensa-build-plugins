plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.mavenPluginDevelopment)
    `maven-publish`
}

dependencies {
    implementation(project(":site-common"))
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.core)
    compileOnly(libs.maven.plugin.annotations)

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
// We DO bundle one tiny resource: kensa-core-version.txt, so the mojo has a sensible
// default for <kensaCoreVersion> matching this plugin release. Users can override via
// the mojo parameter if they want to pin to a different kensa-core.
val kensaCoreVersion = rootProject.file("kensa-core-version.txt").readText().trim()

val writeKensaCoreVersionResource = tasks.register("writeKensaCoreVersionResource") {
    val outputDir = layout.buildDirectory.dir("generated/resources/main")
    inputs.property("kensaCoreVersion", kensaCoreVersion)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("META-INF/dev/kensa/kensa-core-version.txt").asFile
        file.parentFile.mkdirs()
        file.writeText(kensaCoreVersion)
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
            url.set("https://kensa.dev/docs/site-mode/maven")
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
    dependsOn(":site-common:publishToMavenLocal")
    systemProperty("plugin.version", project.version.toString())
    systemProperty("kensa.core.version", kensaCoreVersion)
    systemProperty("maven.home", mavenHome)
}
