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
            val configJson = sourcesDir.resolve(id).resolve("configuration.json").readText()
            val title = extractTitle(configJson) ?: id
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

    private fun unescape(s: String): String = s
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
}
