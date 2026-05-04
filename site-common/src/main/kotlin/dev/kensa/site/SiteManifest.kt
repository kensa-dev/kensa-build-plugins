package dev.kensa.site

data class SourceEntry(
    val id: String,
    val title: String,
    val url: String,
)

data class SiteManifest(
    val schemaVersion: Int,
    val kensaVersion: String,
    val sources: List<SourceEntry>,
) {
    companion object {
        // Uses trimMargin (not trimIndent) because sourcesJson is multi-line: trimIndent's
        // global min-indent calculation would factor the interpolated content's leading spaces
        // into the trim and wreck the surrounding layout.
        fun toJson(manifest: SiteManifest): String = """
            |{
            |  "schemaVersion": ${manifest.schemaVersion},
            |  "kensaVersion": "${escape(manifest.kensaVersion)}",
            |  "sources": ${sourcesJson(manifest.sources)}
            |}""".trimMargin()

        private fun sourcesJson(sources: List<SourceEntry>): String =
            if (sources.isEmpty()) "[]"
            else sources.joinToString(prefix = "[\n", separator = ",\n", postfix = "\n  ]") { sourceJson(it) }

        private fun sourceJson(source: SourceEntry): String =
            """    { "id": "${escape(source.id)}", "title": "${escape(source.title)}", "url": "${escape(source.url)}" }"""

        private fun escape(s: String): String = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
