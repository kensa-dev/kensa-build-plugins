package dev.kensa.gradle

import dev.kensa.site.ShellResources
import dev.kensa.site.SiteAssembler
import dev.kensa.site.SiteAssemblerLogger
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files

@CacheableTask
abstract class AssembleKensaSiteTask : DefaultTask() {

    @get:Internal
    abstract val siteRoot: DirectoryProperty

    @get:Input
    abstract val expectedSourceIds: SetProperty<String>

    @get:Input
    abstract val kensaVersion: Property<String>

    /**
     * Per-source title overrides keyed by source id. Mirrors `kensa.sourceTitles` on the extension.
     * Entries here win over whatever the test runtime wrote to `configuration.json`.
     */
    @get:Input
    abstract val sourceTitles: MapProperty<String, String>

    /**
     * Per-source `configuration.json` files. Their content (titleText) feeds the manifest, and their presence
     * determines which sources are included. Tracked so a Test task that updates titleText invalidates the cache.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceConfigurations: ConfigurableFileCollection

    /**
     * Resolved kensa-core jar(s) — `kensa.js` and `logo.svg` are extracted from these at execution time.
     * @Classpath fingerprints jar contents, so when a new kensa-core SNAPSHOT lands in `~/.m2` the cache
     * key changes and the task re-runs. No plugin republish required.
     */
    @get:Classpath
    abstract val shellSource: ConfigurableFileCollection

    @get:OutputFile
    abstract val manifestJsonFile: RegularFileProperty

    @get:OutputFile
    abstract val indexHtmlFile: RegularFileProperty

    @get:OutputFile
    abstract val kensaJsFile: RegularFileProperty

    @get:OutputFile
    abstract val logoSvgFile: RegularFileProperty

    @TaskAction
    fun assemble() {
        val root = siteRoot.get().asFile.toPath()
        Files.createDirectories(root)

        val gradleLogger = object : SiteAssemblerLogger {
            override fun lifecycle(message: String) = logger.lifecycle(message)
            override fun warn(message: String) = logger.warn(message)
        }

        SiteAssembler(
            siteRoot = root,
            expectedSourceIds = expectedSourceIds.get(),
            kensaVersion = kensaVersion.get(),
            logger = gradleLogger,
            sourceTitles = sourceTitles.get(),
        ).assembleManifest()

        val jars = shellSource.files.map { it.toPath() }
        if (jars.isEmpty()) {
            throw GradleException(
                "Kensa shell source is empty. Ensure the project declares a repository (e.g. mavenLocal() / mavenCentral()) " +
                    "that publishes dev.kensa:kensa-core:${kensaVersion.get()}."
            )
        }

        try {
            ShellResources.writeTo(root, jars)
        } catch (e: IllegalStateException) {
            throw GradleException(e.message ?: "Failed to extract Kensa shell resources", e)
        }
    }
}
