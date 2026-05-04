package dev.kensa.site

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

object ShellResources {
    /** Resources copied verbatim from one of the supplied jars. */
    val CLASSPATH_NAMES = listOf("kensa.js", "logo.svg")

    /**
     * Writes the Kensa site shell into [siteRoot]:
     *   - `index.html` — generated inline (template; mirrors core's ResultWriter.writeHtml)
     *   - `kensa.js`, `logo.svg` — extracted from the first [sourceJars] entry that contains them.
     *
     * [sourceJars] is typically a single resolved kensa-core jar. Resolving at task/mojo execution
     * time (rather than bundling at plugin-build time) means the latest published kensa-core's UI
     * bundle is used without republishing the build plugin.
     *
     * Throws IllegalStateException if any required resource is not found in the supplied jars.
     */
    fun writeTo(siteRoot: Path, sourceJars: Collection<Path>, titleText: String = "Kensa Tests") {
        Files.writeString(siteRoot.resolve("index.html"), indexHtml(titleText))

        val needed = CLASSPATH_NAMES.toMutableSet()
        for (jarPath in sourceJars) {
            if (needed.isEmpty()) break
            ZipFile(jarPath.toFile()).use { zip ->
                for (name in needed.toList()) {
                    val entry = zip.getEntry(name) ?: continue
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(siteRoot.resolve(name)).use { output ->
                            input.copyTo(output)
                        }
                    }
                    needed.remove(name)
                }
            }
        }

        if (needed.isNotEmpty()) {
            error(
                "Kensa shell resources not found in any provided jar: $needed. " +
                    "Searched: ${sourceJars.joinToString { it.fileName.toString() }}"
            )
        }
    }

    private fun indexHtml(titleText: String): String = """
        <!doctype html>
        <html lang="en">
        <head>
            <title>$titleText</title>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <link rel="icon" type="image/svg+xml" href="logo.svg" />
        </head>
        <body>
            <div id="root"></div>
            <script src="kensa.js"></script>
        </body>
        </html>
    """.trimIndent()
}
