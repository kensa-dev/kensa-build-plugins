package dev.kensa.site

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

class SiteAssembler(
    private val siteRoot: Path,
    private val expectedSourceIds: Set<String>,
    private val kensaVersion: String,
    private val logger: SiteAssemblerLogger,
    /**
     * Per-source title overrides supplied by the build tool (Gradle `kensa { sourceTitles = ... }`
     * or Maven `<sourceTitles>`). When a source id has an entry, its `titleText` in that source's
     * `configuration.json` is rewritten before manifest assembly so the per-source HTML page
     * `<title>` reflects the build-declared label too. Empty by default — code-side
     * `Kensa.konfigure { titleText = ... }` (and the legacy `kensa.source.title` system property)
     * remain valid for projects that prefer to keep title declaration alongside the tests.
     */
    private val sourceTitles: Map<String, String> = emptyMap(),
) {
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun assembleManifest() {
        val sourcesDir = siteRoot.resolve("sources")
        Files.createDirectories(sourcesDir)

        val present = sourcesDir.listDirectoryEntries()
            .filter { it.isDirectory() && it.resolve("configuration.json").exists() }
            .map { it.name }
            .toSet()

        val stale = present - expectedSourceIds
        for (id in stale) {
            logger.lifecycle("Removing stale Kensa source bundle: $id")
            sourcesDir.resolve(id).deleteRecursively()
        }

        val missing = expectedSourceIds - present
        for (id in missing) {
            logger.warn(
                "Kensa source '$id' was expected but no bundle is present at ${sourcesDir.resolve(id)}; " +
                "omitting from manifest. Run its test task/execution to include it."
            )
        }

        val included = (expectedSourceIds intersect present).sorted()
        val sources = included.map { id ->
            val configPath = sourcesDir.resolve(id).resolve("configuration.json")
            val override = sourceTitles[id]
            val title = if (override != null) {
                applyTitleOverride(configPath, override)
                override
            } else {
                extractTitle(configPath.readText()) ?: id
            }
            SourceEntry(id = id, title = title, url = "sources/$id")
        }

        val manifest = SiteManifest(
            schemaVersion = 1,
            kensaVersion = kensaVersion,
            sources = sources,
        )

        siteRoot.resolve("manifest.json").writeText(SiteManifest.toJson(manifest))
        logger.lifecycle("Kensa site manifest: ${siteRoot.resolve("manifest.json")} (${sources.size} source(s))")
    }

    private fun extractTitle(configJson: String): String? {
        val regex = Regex("\"titleText\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        return regex.find(configJson)?.groupValues?.get(1)?.let { unescape(it) }
    }

    /**
     * Rewrite the `titleText` field in a source's `configuration.json` to [override]. The per-source
     * bundle's HTML pages read `titleText` for their `<title>` tags, so propagating the build-declared
     * label here keeps the standalone page title in sync with the aggregated manifest label.
     *
     * Relies on the invariant that kensa-core's `ResultWriter` always writes a `titleText` field. If
     * that invariant breaks, the replace is a no-op and the manifest will still show the build-declared
     * label (it's resolved separately in `assembleManifest`), but the per-source HTML title will fall
     * out of sync — which surfaces the underlying packaging bug rather than hiding it.
     */
    private fun applyTitleOverride(configPath: Path, override: String) {
        val original = configPath.readText()
        if (extractTitle(original) == override) return  // idempotent — no rewrite needed
        val rewritten = original.replaceFirst(
            Regex("\"titleText\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\""),
            "\"titleText\": \"${escape(override)}\"",
        )
        configPath.writeText(rewritten)
    }

    private fun unescape(s: String): String = s
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
