package dev.kensa.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.process.CommandLineArgumentProvider

/**
 * Carries `kensa.output.root` / `kensa.source.id` to a Test task's JVM via Gradle's argument-provider
 * mechanism instead of `testTask.systemProperty(...)`. Two payoffs:
 *
 * - `sourceBundleDir` is declared as the Test task's `@OutputDirectory`, so Gradle tracks the per-source
 *   bundle that the test runtime writes into. UP-TO-DATE checks become accurate, and removing the dir
 *   externally correctly invalidates the task.
 * - `siteRoot` is `@Internal` — the absolute path doesn't enter the cache key, so the same Test task
 *   stays cache-compatible across machines / shared Gradle build cache. The bundle's contents are still
 *   tracked via `@OutputDirectory`, which is path-normalized.
 *
 * Plain `systemProperty("kensa.output.root", absPath)` made the absolute path part of the cache key,
 * which was fine locally but fatal for remote / shared caches.
 */
abstract class KensaSourceArgsProvider : CommandLineArgumentProvider {

    @get:Internal
    abstract val siteRoot: DirectoryProperty

    @get:OutputDirectory
    abstract val sourceBundleDir: DirectoryProperty

    @get:Input
    abstract val sourceId: Property<String>

    /**
     * When the consuming build has already set `kensa.source.id` via `testTask.systemProperty(...)`,
     * skip emitting our own `-D` so we don't fight an explicit override.
     */
    @get:Input
    abstract val emitSourceIdArg: Property<Boolean>

    override fun asArguments(): Iterable<String> {
        val args = mutableListOf("-Dkensa.output.root=${siteRoot.get().asFile.absolutePath}")
        if (emitSourceIdArg.get()) {
            args.add("-Dkensa.source.id=${sourceId.get()}")
        }
        return args
    }
}
