package dev.kensa.maven

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.apache.maven.shared.invoker.DefaultInvocationRequest
import org.apache.maven.shared.invoker.DefaultInvoker
import org.apache.maven.shared.invoker.InvocationResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.UUID
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class InvokerIntegrationTest {

    companion object {
        private val pluginVersion: String = System.getProperty("plugin.version")
            ?: error("System property 'plugin.version' must be set when running this test")
        private val mavenLocalRepo: File = File(System.getProperty("user.home"), ".m2/repository")
    }

    @Test
    fun `single-execution fixture builds and produces a single-source manifest`(@TempDir tempDir: Path) {
        val testRepo = tempDir.resolve("test-repo")
        val kensaCoreVersion = "0.8.0-test-${UUID.randomUUID()}"
        publishFakeKensaCore(testRepo, kensaCoreVersion, kensaJsBytes = "// shell\n".toByteArray())

        copyFixture("single-execution", tempDir)
        prePopulateSource(tempDir.resolve("target/kensa-site/sources/test"), "Acceptance Tests")

        val result = runMaven(tempDir.toFile(), goals = listOf("verify"), testRepo = testRepo, kensaCoreVersion = kensaCoreVersion)

        result.exitCode shouldBe 0
        Files.exists(tempDir.resolve("target/kensa-site/manifest.json")) shouldBe true
        Files.exists(tempDir.resolve("target/kensa-site/index.html")) shouldBe true
        Files.exists(tempDir.resolve("target/kensa-site/kensa.js")) shouldBe true
        Files.exists(tempDir.resolve("target/kensa-site/logo.svg")) shouldBe true

        val manifest = tempDir.resolve("target/kensa-site/manifest.json").toFile().readText()
        manifest shouldContain "\"id\": \"test\""
        manifest shouldContain "\"title\": \"Acceptance Tests\""

        Files.readString(tempDir.resolve("target/kensa-site/kensa.js")) shouldBe "// shell\n"
    }

    @Test
    fun `two-executions fixture aggregates both source bundles`(@TempDir tempDir: Path) {
        val testRepo = tempDir.resolve("test-repo")
        val kensaCoreVersion = "0.8.0-test-${UUID.randomUUID()}"
        publishFakeKensaCore(testRepo, kensaCoreVersion)

        copyFixture("two-executions", tempDir)
        prePopulateSource(tempDir.resolve("target/kensa-site/sources/uiTest"), "UI Tests")
        prePopulateSource(tempDir.resolve("target/kensa-site/sources/scenarioTest"), "Scenario Tests")

        val result = runMaven(tempDir.toFile(), goals = listOf("verify"), testRepo = testRepo, kensaCoreVersion = kensaCoreVersion)

        result.exitCode shouldBe 0
        val manifest = tempDir.resolve("target/kensa-site/manifest.json").toFile().readText()
        manifest shouldContain "\"id\": \"uiTest\""
        manifest shouldContain "\"id\": \"scenarioTest\""
    }

    @Test
    fun `partial run — one source missing — completes with warning and partial manifest`(@TempDir tempDir: Path) {
        val testRepo = tempDir.resolve("test-repo")
        val kensaCoreVersion = "0.8.0-test-${UUID.randomUUID()}"
        publishFakeKensaCore(testRepo, kensaCoreVersion)

        copyFixture("two-executions", tempDir)
        prePopulateSource(tempDir.resolve("target/kensa-site/sources/uiTest"), "UI Tests")
        // scenarioTest intentionally absent

        val result = runMaven(tempDir.toFile(), goals = listOf("verify"), testRepo = testRepo, kensaCoreVersion = kensaCoreVersion)

        result.exitCode shouldBe 0
        val manifest = tempDir.resolve("target/kensa-site/manifest.json").toFile().readText()
        manifest shouldContain "\"id\": \"uiTest\""
        assert(!manifest.contains("\"id\": \"scenarioTest\"")) { "scenarioTest should not be in manifest: $manifest" }
    }

    @Test
    fun `sourceTitles mojo parameter overrides per-source titleText in manifest and configuration json`(@TempDir tempDir: Path) {
        val testRepo = tempDir.resolve("test-repo")
        val kensaCoreVersion = "0.8.0-test-${UUID.randomUUID()}"
        publishFakeKensaCore(testRepo, kensaCoreVersion)

        copyFixture("with-source-titles", tempDir)
        prePopulateSource(tempDir.resolve("target/kensa-site/sources/uiTest"), "Code-side UI")
        prePopulateSource(tempDir.resolve("target/kensa-site/sources/scenarioTest"), "Code-side Scenario")

        val result = runMaven(tempDir.toFile(), goals = listOf("verify"), testRepo = testRepo, kensaCoreVersion = kensaCoreVersion)

        result.exitCode shouldBe 0
        val manifest = tempDir.resolve("target/kensa-site/manifest.json").toFile().readText()
        manifest shouldContain "\"title\": \"Build-declared UI Tests\""
        manifest shouldContain "\"title\": \"Build-declared Scenario Tests\""

        val uiConfig = tempDir.resolve("target/kensa-site/sources/uiTest/configuration.json").toFile().readText()
        uiConfig shouldContain "\"titleText\": \"Build-declared UI Tests\""
    }

    @Test
    fun `re-running with a new kensa-core jar picks up the new shell content without a plugin republish`(@TempDir tempDir: Path) {
        val testRepo = tempDir.resolve("test-repo")
        val firstVersion = "0.8.0-test-${UUID.randomUUID()}"
        val secondVersion = "0.8.0-test-${UUID.randomUUID()}"
        publishFakeKensaCore(testRepo, firstVersion, kensaJsBytes = "// shell v1\n".toByteArray())
        publishFakeKensaCore(testRepo, secondVersion, kensaJsBytes = "// shell v2\n".toByteArray())

        copyFixture("single-execution", tempDir)
        prePopulateSource(tempDir.resolve("target/kensa-site/sources/test"), "Acceptance Tests")

        val first = runMaven(tempDir.toFile(), goals = listOf("verify"), testRepo = testRepo, kensaCoreVersion = firstVersion)
        first.exitCode shouldBe 0
        Files.readString(tempDir.resolve("target/kensa-site/kensa.js")) shouldBe "// shell v1\n"

        val second = runMaven(tempDir.toFile(), goals = listOf("verify"), testRepo = testRepo, kensaCoreVersion = secondVersion)
        second.exitCode shouldBe 0
        Files.readString(tempDir.resolve("target/kensa-site/kensa.js")) shouldBe "// shell v2\n"
    }

    private fun runMaven(
        workingDir: File,
        goals: List<String>,
        testRepo: Path,
        kensaCoreVersion: String,
    ): InvocationResult {
        val request = DefaultInvocationRequest()
            .setBaseDirectory(workingDir)
            .setGoals(goals)
            .setProperties(Properties().apply {
                setProperty("kensa.plugin.version", pluginVersion)
                setProperty("kensa.test.repo", testRepo.toUri().toString())
                setProperty("kensa.test.core.version", kensaCoreVersion)
            })
            .setBatchMode(true)
        val invoker = DefaultInvoker().setLocalRepositoryDirectory(mavenLocalRepo)
        System.getProperty("maven.home")?.takeIf { it.isNotBlank() }?.let { invoker.setMavenHome(File(it)) }
        return invoker.execute(request)
    }

    private fun copyFixture(name: String, dest: Path) {
        val src = File("src/it/$name")
        require(src.exists()) { "fixture not found: ${src.absolutePath}" }
        src.walkTopDown().forEach { source ->
            val rel = source.relativeTo(src).path
            val target = dest.resolve(rel)
            if (source.isDirectory) Files.createDirectories(target)
            else {
                Files.createDirectories(target.parent)
                Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun prePopulateSource(dir: Path, titleText: String) {
        Files.createDirectories(dir)
        Files.writeString(
            dir.resolve("configuration.json"),
            """{"titleText":"$titleText","kensaVersion":"0.8.0"}"""
        )
    }

    /**
     * Publishes a synthetic `dev.kensa:kensa-core` artifact at [version] into [repoRoot] (Maven layout).
     * The jar contains only `kensa.js` and `logo.svg` — sufficient for the site-assembly mojo. Calling
     * with a different [version] simulates a kensa UI republish without a plugin republish.
     */
    private fun publishFakeKensaCore(
        repoRoot: Path,
        version: String,
        kensaJsBytes: ByteArray = "// shell\n".toByteArray(),
        logoSvgBytes: ByteArray = "<svg/>".toByteArray(),
    ) {
        val artifactDir = repoRoot.resolve("dev/kensa/kensa-core/$version")
        Files.createDirectories(artifactDir)

        val jarPath = artifactDir.resolve("kensa-core-$version.jar")
        JarOutputStream(Files.newOutputStream(jarPath)).use { jos ->
            jos.putNextEntry(JarEntry("kensa.js"))
            jos.write(kensaJsBytes)
            jos.closeEntry()
            jos.putNextEntry(JarEntry("logo.svg"))
            jos.write(logoSvgBytes)
            jos.closeEntry()
        }

        Files.writeString(
            artifactDir.resolve("kensa-core-$version.pom"),
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>dev.kensa</groupId>
              <artifactId>kensa-core</artifactId>
              <version>$version</version>
              <packaging>jar</packaging>
            </project>
            """.trimIndent()
        )
    }
}
