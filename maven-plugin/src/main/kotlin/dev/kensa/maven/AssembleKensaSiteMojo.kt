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
        ).assembleManifest()

        val resolvedVersion = kensaCoreVersion?.takeIf { it.isNotBlank() } ?: defaultKensaCoreVersion()
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

    private fun defaultKensaCoreVersion(): String {
        val resourceName = "META-INF/dev/kensa/kensa-core-version.txt"
        return AssembleKensaSiteMojo::class.java.classLoader
            .getResourceAsStream(resourceName)
            ?.bufferedReader()
            ?.use { it.readText().trim() }
            ?.takeIf { it.isNotBlank() }
            ?: throw MojoExecutionException(
                "kensa-maven-plugin: default kensa-core version resource missing ($resourceName). " +
                    "This is a packaging bug — file an issue, or set <kensaCoreVersion> explicitly."
            )
    }
}
