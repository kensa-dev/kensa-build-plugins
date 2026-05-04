package dev.kensa.site

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SiteAssemblerTest {

    private class CapturingLogger : SiteAssemblerLogger {
        val lifecycle = mutableListOf<String>()
        val warn = mutableListOf<String>()
        override fun lifecycle(message: String) { lifecycle += message }
        override fun warn(message: String) { warn += message }
    }

    @Test
    fun `assembles manifest from intersect of expected and present sources`(@TempDir tempDir: Path) {
        prePopulate(tempDir, "uiTest", "UI Tests")
        prePopulate(tempDir, "test", "Acceptance Tests")
        val log = CapturingLogger()

        SiteAssembler(
            siteRoot = tempDir,
            expectedSourceIds = setOf("uiTest", "test"),
            kensaVersion = "0.8.0",
            logger = log,
        ).assembleManifest()

        val manifest = tempDir.resolve("manifest.json").toFile().readText()
        manifest shouldContain "\"id\": \"uiTest\""
        manifest shouldContain "\"id\": \"test\""
        log.warn.shouldContainExactly()
    }

    @Test
    fun `omits missing expected sources from manifest and logs a warning`(@TempDir tempDir: Path) {
        prePopulate(tempDir, "uiTest", "UI Tests")
        val log = CapturingLogger()

        SiteAssembler(
            siteRoot = tempDir,
            expectedSourceIds = setOf("uiTest", "test"),
            kensaVersion = "0.8.0",
            logger = log,
        ).assembleManifest()

        val manifest = tempDir.resolve("manifest.json").toFile().readText()
        manifest shouldContain "\"id\": \"uiTest\""
        manifest.contains("\"id\": \"test\"") shouldBe false
        log.warn.size shouldBe 1
        log.warn[0] shouldContain "test"
    }

    @Test
    fun `deletes stale source dirs not in expected set`(@TempDir tempDir: Path) {
        prePopulate(tempDir, "uiTest", "UI Tests")
        prePopulate(tempDir, "removed", "Old")
        val log = CapturingLogger()

        SiteAssembler(
            siteRoot = tempDir,
            expectedSourceIds = setOf("uiTest"),
            kensaVersion = "0.8.0",
            logger = log,
        ).assembleManifest()

        Files.exists(tempDir.resolve("sources/removed")) shouldBe false
        Files.exists(tempDir.resolve("sources/uiTest")) shouldBe true
    }

    @Test
    fun `extracts titleText from configuration json into manifest title`(@TempDir tempDir: Path) {
        prePopulate(tempDir, "uiTest", "Friendly UI Title")
        val log = CapturingLogger()

        SiteAssembler(
            siteRoot = tempDir,
            expectedSourceIds = setOf("uiTest"),
            kensaVersion = "0.8.0",
            logger = log,
        ).assembleManifest()

        tempDir.resolve("manifest.json").toFile().readText() shouldContain "\"title\": \"Friendly UI Title\""
    }

    @Test
    fun `falls back to source id when configuration json has no titleText`(@TempDir tempDir: Path) {
        val dir = tempDir.resolve("sources/uiTest")
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("configuration.json"), """{"kensaVersion":"0.8.0"}""")
        val log = CapturingLogger()

        SiteAssembler(
            siteRoot = tempDir,
            expectedSourceIds = setOf("uiTest"),
            kensaVersion = "0.8.0",
            logger = log,
        ).assembleManifest()

        tempDir.resolve("manifest.json").toFile().readText() shouldContain "\"title\": \"uiTest\""
    }

    private fun prePopulate(siteRoot: Path, sourceId: String, titleText: String) {
        val dir = siteRoot.resolve("sources/$sourceId")
        Files.createDirectories(dir)
        Files.writeString(
            dir.resolve("configuration.json"),
            """{"titleText":"$titleText","kensaVersion":"0.8.0"}"""
        )
    }
}
