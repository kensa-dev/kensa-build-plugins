package dev.kensa.site

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

class SiteManifestTest {

    @Test
    fun `serialises a manifest with multiple sources to canonical JSON`() {
        val manifest = SiteManifest(
            schemaVersion = 1,
            kensaVersion = "0.8.0-SNAPSHOT",
            sources = listOf(
                SourceEntry("uiTest", "UI Tests", "sources/uiTest"),
                SourceEntry("test", "Acceptance Tests", "sources/test"),
            )
        )

        val json = SiteManifest.toJson(manifest)

        json shouldContain "\"schemaVersion\": 1"
        json shouldContain "\"kensaVersion\": \"0.8.0-SNAPSHOT\""
        json shouldContain "\"id\": \"uiTest\""
        json shouldContain "\"title\": \"UI Tests\""
        json shouldContain "\"url\": \"sources/uiTest\""
        json shouldContain "\"id\": \"test\""
    }

    @Test
    fun `does not emit generatedAt — assembler output must be deterministic for build-cache correctness`() {
        val manifest = SiteManifest(
            schemaVersion = 1,
            kensaVersion = "0.8.0",
            sources = listOf(SourceEntry("ui", "UI", "sources/ui")),
        )

        SiteManifest.toJson(manifest) shouldNotContain "generatedAt"
    }

    @Test
    fun `escapes JSON special characters in title and url`() {
        val manifest = SiteManifest(
            schemaVersion = 1,
            kensaVersion = "0.8.0",
            sources = listOf(
                SourceEntry("ui", "Tests with \"quotes\" and \\backslash", "sources/ui"),
            )
        )

        val json = SiteManifest.toJson(manifest)

        json shouldContain "Tests with \\\"quotes\\\" and \\\\backslash"
    }

    @Test
    fun `serialises an empty sources array`() {
        val manifest = SiteManifest(
            schemaVersion = 1,
            kensaVersion = "0.8.0",
            sources = emptyList()
        )

        val json = SiteManifest.toJson(manifest)

        json shouldContain "\"sources\": []"
    }
}
