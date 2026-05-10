package dev.kensa.gradle

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class SiteModeFunctionalTest {

    // Injected by the functionalTest gradle task from kensa-core-version.txt — same source the plugin reads.
    private val kensaCoreVersion: String = System.getProperty("kensa.core.version")
        ?: error("System property 'kensa.core.version' not set; configure it on the functionalTest task.")


    @Test
    fun `assembleKensaSite produces shell artifacts and a manifest aggregating all present sourcesets`(@TempDir projectDir: Path) {
        writeFixtureProject(projectDir)
        prePopulateSource(projectDir, "uiTest", titleText = "UI Tests")
        prePopulateSource(projectDir, "test", titleText = "Acceptance Tests")

        val result = runner(projectDir).build()

        result.task(":assembleKensaSite")?.outcome shouldBe TaskOutcome.SUCCESS

        val siteRoot = projectDir.resolve("build/kensa-site")
        Files.exists(siteRoot.resolve("manifest.json")) shouldBe true
        Files.exists(siteRoot.resolve("index.html")) shouldBe true
        Files.exists(siteRoot.resolve("kensa.js")) shouldBe true
        Files.exists(siteRoot.resolve("logo.svg")) shouldBe true

        val manifestText = siteRoot.resolve("manifest.json").toFile().readText()
        manifestText shouldContain "\"id\": \"uiTest\""
        manifestText shouldContain "\"title\": \"UI Tests\""
        manifestText shouldContain "\"id\": \"test\""
        manifestText shouldContain "\"title\": \"Acceptance Tests\""
    }

    @Test
    fun `partial run — manifest contains only present source and warns about missing one`(@TempDir projectDir: Path) {
        writeFixtureProject(projectDir)
        prePopulateSource(projectDir, "uiTest", titleText = "UI Tests")

        val result = runner(projectDir).build()

        result.task(":assembleKensaSite")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "Kensa source 'test' was expected but no bundle is present"

        val manifestText = projectDir.resolve("build/kensa-site/manifest.json").toFile().readText()
        manifestText shouldContain "\"id\": \"uiTest\""
        manifestText.shouldNotContainSourceId("test")
    }

    @Test
    fun `stale source dirs are deleted when sourceSets list changes`(@TempDir projectDir: Path) {
        writeFixtureProject(projectDir)
        prePopulateSource(projectDir, "uiTest", titleText = "UI Tests")
        prePopulateSource(projectDir, "removedSet", titleText = "Old")

        runner(projectDir).build()

        Files.exists(projectDir.resolve("build/kensa-site/sources/removedSet")) shouldBe false
        Files.exists(projectDir.resolve("build/kensa-site/sources/uiTest")) shouldBe true
    }

    @Test
    fun `source id collision fails the build with an actionable error`(@TempDir projectDir: Path) {
        val repo = projectDir.resolve("test-repo")
        publishFakeKensaCore(repo)
        projectDir.resolve("settings.gradle.kts").toFile().writeText(
            """
            rootProject.name = "fixture"
            """.trimIndent()
        )
        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                id("dev.kensa.gradle-plugin")
                id("org.jetbrains.kotlin.jvm") version "2.3.21"
            }

            repositories {
                maven { url = uri("${repo.toUri()}") }
            }

            kensa {
                site = true
                sourceSets = setOf("uiTest", "test")
            }

            tasks.register<Test>("uiTest") {
                useJUnitPlatform()
                systemProperty("kensa.source.id", "test")
            }
            """.trimIndent()
        )

        val result = runner(projectDir).buildAndFail()

        result.output shouldContain "source id collision on 'test'"
    }

    @Test
    fun `kensa source title override flows through configuration json into manifest title`(@TempDir projectDir: Path) {
        writeFixtureProject(projectDir)
        // Pre-populate with the *overridden* titleText that the runtime would have written.
        prePopulateSource(projectDir, "uiTest", titleText = "Cool UI Tests")
        prePopulateSource(projectDir, "test", titleText = "Acceptance Tests")

        runner(projectDir).build()

        val manifestText = projectDir.resolve("build/kensa-site/manifest.json").toFile().readText()
        manifestText shouldContain "\"title\": \"Cool UI Tests\""
    }

    @Test
    fun `task is UP_TO_DATE on a second run with no input changes`(@TempDir projectDir: Path) {
        writeFixtureProject(projectDir)
        prePopulateSource(projectDir, "uiTest", titleText = "UI Tests")
        prePopulateSource(projectDir, "test", titleText = "Acceptance Tests")

        runner(projectDir).build()
        val second = runner(projectDir).build()

        second.task(":assembleKensaSite")?.outcome shouldBe TaskOutcome.UP_TO_DATE
    }

    @Test
    fun `task re-runs when a per-source titleText changes`(@TempDir projectDir: Path) {
        writeFixtureProject(projectDir)
        prePopulateSource(projectDir, "uiTest", titleText = "UI Tests")
        prePopulateSource(projectDir, "test", titleText = "Acceptance Tests")

        runner(projectDir).build()

        prePopulateSource(projectDir, "uiTest", titleText = "Renamed UI Tests")

        val second = runner(projectDir).build()

        second.task(":assembleKensaSite")?.outcome shouldBe TaskOutcome.SUCCESS
        projectDir.resolve("build/kensa-site/manifest.json").toFile().readText() shouldContain "\"title\": \"Renamed UI Tests\""
    }

    @Test
    fun `republishing kensa-core with a new shell invalidates the cache and updates kensa_js without a plugin republish`(@TempDir projectDir: Path) {
        val repo = projectDir.resolve("test-repo")
        publishFakeKensaCore(repo, kensaJsBytes = "// shell v1\n".toByteArray())
        writeFixtureProject(projectDir, repo)
        prePopulateSource(projectDir, "uiTest", titleText = "UI Tests")
        prePopulateSource(projectDir, "test", titleText = "Acceptance Tests")

        val first = runner(projectDir).build()
        first.task(":assembleKensaSite")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.readString(projectDir.resolve("build/kensa-site/kensa.js")) shouldBe "// shell v1\n"

        publishFakeKensaCore(repo, kensaJsBytes = "// shell v2\n".toByteArray())

        val second = runner(projectDir).build()
        second.task(":assembleKensaSite")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.readString(projectDir.resolve("build/kensa-site/kensa.js")) shouldBe "// shell v2\n"
    }

    private fun runner(projectDir: Path): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("assembleKensaSite", "--refresh-dependencies", "--stacktrace")
            .withPluginClasspath()

    private fun String.shouldNotContainSourceId(id: String) {
        if (contains("\"id\": \"$id\"")) error("Expected manifest sources NOT to contain id='$id', but did:\n$this")
    }

    private fun writeFixtureProject(
        projectDir: Path,
        repo: Path = defaultRepo(projectDir),
        kotlinVersion: String = "2.3.21",
    ) {
        if (!Files.exists(repo.resolve("dev/kensa/kensa-core/$kensaCoreVersion/kensa-core-$kensaCoreVersion.jar"))) {
            publishFakeKensaCore(repo)
        }
        projectDir.resolve("settings.gradle.kts").toFile().writeText(
            """
            rootProject.name = "fixture"
            """.trimIndent()
        )
        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                id("dev.kensa.gradle-plugin")
                id("org.jetbrains.kotlin.jvm") version "$kotlinVersion"
            }

            repositories {
                maven { url = uri("${repo.toUri()}") }
            }

            kensa {
                site = true
                sourceSets = setOf("uiTest", "test")
            }

            tasks.register<Test>("uiTest") {
                useJUnitPlatform()
            }
            """.trimIndent()
        )
    }

    private fun defaultRepo(projectDir: Path): Path = projectDir.resolve("test-repo")

    private fun prePopulateSource(projectDir: Path, sourceId: String, titleText: String) {
        val dir = projectDir.resolve("build/kensa-site/sources/$sourceId")
        Files.createDirectories(dir)
        Files.writeString(
            dir.resolve("configuration.json"),
            """{"titleText":"$titleText","kensaVersion":"$kensaCoreVersion"}"""
        )
        Files.writeString(
            dir.resolve("indices.json"),
            """{"indices":[]}"""
        )
        Files.createDirectories(dir.resolve("results"))
    }

    /**
     * Publishes a synthetic `dev.kensa:kensa-core:0.8.0` artifact into [repoRoot]
     * (Maven layout). The jar contains only `kensa.js` and `logo.svg` — sufficient for the
     * site-assembly task. Calling this twice with different bytes simulates a kensa UI
     * update being republished to maven local.
     */
    private fun publishFakeKensaCore(
        repoRoot: Path,
        kensaJsBytes: ByteArray = "// shell\n".toByteArray(),
        logoSvgBytes: ByteArray = "<svg/>".toByteArray(),
    ) {
        val artifactDir = repoRoot.resolve("dev/kensa/kensa-core/$kensaCoreVersion")
        Files.createDirectories(artifactDir)

        val jarPath = artifactDir.resolve("kensa-core-$kensaCoreVersion.jar")
        JarOutputStream(Files.newOutputStream(jarPath)).use { jos ->
            jos.putNextEntry(JarEntry("kensa.js"))
            jos.write(kensaJsBytes)
            jos.closeEntry()
            jos.putNextEntry(JarEntry("logo.svg"))
            jos.write(logoSvgBytes)
            jos.closeEntry()
        }

        Files.writeString(
            artifactDir.resolve("kensa-core-$kensaCoreVersion.pom"),
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>dev.kensa</groupId>
              <artifactId>kensa-core</artifactId>
              <version>$kensaCoreVersion</version>
              <packaging>jar</packaging>
            </project>
            """.trimIndent()
        )
    }
}
