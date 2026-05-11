package dev.kensa.maven

import dev.kensa.site.ShellResources
import dev.kensa.site.SiteAssembler
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactRequest
import org.eclipse.aether.resolution.ArtifactResolutionException
import java.io.File
import java.nio.file.Files

@Mojo(
    name = "assemble-site",
    defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST,
    threadSafe = true,
    requiresProject = true,
)
class AssembleKensaSiteMojo : AbstractMojo() {

    /** Site root directory. Defaults to ${project.build.directory}/kensa-site. */
    @Parameter(defaultValue = "\${project.build.directory}/kensa-site", required = true)
    lateinit var siteRoot: File

    /** Source ids expected to be present. Each maps to a sub-dir under sources/. */
    @Parameter(required = true)
    var expectedSourceIds: List<String> = emptyList()

    /** Kensa version recorded in manifest.json. Defaults to the plugin's own version. */
    @Parameter(defaultValue = "\${plugin.version}", required = true)
    lateinit var kensaVersion: String

    /**
     * Version of dev.kensa:kensa-core to resolve and extract the site shell from. When unset, a
     * default paired with this plugin release is used (read from a bundled resource at build time).
     * Set explicitly to pin against a specific kensa-core release/SNAPSHOT independent of the plugin.
     */
    @Parameter(property = "kensa.core.version")
    var kensaCoreVersion: String? = null

    /**
     * Per-source display labels for site mode, keyed by source id. Entries set here override
     * whatever the test runtime wrote to that source's `configuration.json`. When a source id
     * has no entry, the per-source `titleText` from the test runtime is used unchanged.
     *
     * ```xml
     * <sourceTitles>
     *   <uiTest>UI Tests</uiTest>
     *   <acceptanceTest>Acceptance Tests</acceptanceTest>
     * </sourceTitles>
     * ```
     */
    @Parameter
    var sourceTitles: Map<String, String> = emptyMap()

    @Component
    private lateinit var repositorySystem: RepositorySystem

    @Parameter(defaultValue = "\${repositorySystemSession}", readonly = true, required = true)
    private lateinit var repositorySession: RepositorySystemSession

    @Parameter(defaultValue = "\${project.remoteProjectRepositories}", readonly = true, required = true)
    private lateinit var remoteRepositories: List<RemoteRepository>

    override fun execute() {
        if (expectedSourceIds.isEmpty()) {
            throw MojoExecutionException("kensa-maven-plugin: <expectedSourceIds> must list at least one source id")
        }

        val rootPath = siteRoot.toPath()
        Files.createDirectories(rootPath)

        SiteAssembler(
            siteRoot = rootPath,
            expectedSourceIds = expectedSourceIds.toSet(),
            kensaVersion = kensaVersion,
            logger = MavenLogAdapter(log),
            sourceTitles = sourceTitles,
        ).assembleManifest()

        val resolvedVersion = kensaCoreVersion?.takeIf { it.isNotBlank() } ?: defaultKensaCoreVersion()
        checkKensaCoreCompat(resolvedVersion)
        val kensaCoreJar = resolveKensaCore(resolvedVersion)

        try {
            ShellResources.writeTo(rootPath, listOf(kensaCoreJar.toPath()))
        } catch (e: IllegalStateException) {
            throw MojoExecutionException(e.message ?: "Failed to extract Kensa shell resources", e)
        }
    }

    private fun resolveKensaCore(version: String): File {
        val artifact = DefaultArtifact("dev.kensa", "kensa-core", "jar", version)
        val request = ArtifactRequest(artifact, remoteRepositories, null)
        try {
            val result = repositorySystem.resolveArtifact(repositorySession, request)
            return result.artifact.file
        } catch (e: ArtifactResolutionException) {
            throw MojoExecutionException(
                "kensa-maven-plugin: failed to resolve dev.kensa:kensa-core:$version. " +
                    "Ensure your project declares a repository (mavenLocal, mavenCentral, or a snapshot repo) " +
                    "where this artifact is published, or override <kensaCoreVersion>.",
                e,
            )
        }
    }

    private fun defaultKensaCoreVersion(): String =
        readBundledVersion("META-INF/dev/kensa/kensa-core-version.txt")

    private fun minKensaCoreVersion(): String =
        readBundledVersion("META-INF/dev/kensa/kensa-core-min-version.txt")

    private fun readBundledVersion(resourceName: String): String =
        AssembleKensaSiteMojo::class.java.classLoader
            .getResourceAsStream(resourceName)
            ?.bufferedReader()
            ?.use { it.readText().trim() }
            ?.takeIf { it.isNotBlank() }
            ?: throw MojoExecutionException(
                "kensa-maven-plugin: bundled version resource missing ($resourceName). " +
                    "This is a packaging bug — file an issue, or set <kensaCoreVersion> explicitly."
            )

    private fun checkKensaCoreCompat(requested: String) {
        val min = minKensaCoreVersion()
        if (compareSemver(requested, min) < 0) {
            throw MojoExecutionException(
                "kensa-maven-plugin requires kensa-core >= $min but the project requested kensa-core $requested. " +
                    "Update or remove the <kensaCoreVersion> override."
            )
        }
    }

    // Lightweight semver comparison: splits on '.' / '-', compares numeric segments numerically
    // and falls back to lexicographic for anything else (e.g. "0.8.0" < "0.8.1", "0.8.0-SNAPSHOT" < "0.8.0").
    private fun compareSemver(a: String, b: String): Int {
        val left = a.split('.', '-')
        val right = b.split('.', '-')
        val limit = maxOf(left.size, right.size)
        for (i in 0 until limit) {
            val l = left.getOrNull(i) ?: return -1
            val r = right.getOrNull(i) ?: return 1
            val li = l.toIntOrNull()
            val ri = r.toIntOrNull()
            val cmp = if (li != null && ri != null) li.compareTo(ri) else l.compareTo(r)
            if (cmp != 0) return cmp
        }
        return 0
    }
}
