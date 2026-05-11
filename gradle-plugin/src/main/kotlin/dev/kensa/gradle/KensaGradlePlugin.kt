package dev.kensa.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class KensaGradlePlugin : KotlinCompilerPluginSupportPlugin {

    // Captured in `apply` so `getPluginArtifact` (called by the Kotlin Gradle plugin without
    // a Project parameter) can read the per-project KensaExtension to honor an override.
    private lateinit var capturedProject: Project

    override fun apply(target: Project) {
        capturedProject = target

        target.plugins.withType(KotlinBasePlugin::class.java) { kotlinPlugin ->
            val applied = kotlinPlugin.pluginVersion
            if (GradleVersion.version(applied) < GradleVersion.version(MIN_KOTLIN_VERSION)) {
                throw GradleException(
                    "dev.kensa.gradle-plugin requires Kotlin >= $MIN_KOTLIN_VERSION but the project applies Kotlin $applied. " +
                            "Update the Kotlin plugin version."
                )
            }
        }

        target.extensions.create("kensa", KensaExtension::class.java)

        target.afterEvaluate { project ->
            val extension = project.extensions.getByType(KensaExtension::class.java)
            checkKensaCoreCompat(extension.kensaCoreVersion.get())
            if (!extension.enabled.get() || !extension.site.get()) return@afterEvaluate

            val expectedSourceIds = extension.sourceSets.get().toMutableSet()
            val seenIds = mutableSetOf<String>()
            val configuredTestTasks = mutableListOf<org.gradle.api.tasks.testing.Test>()

            for (sourceSetName in extension.sourceSets.get()) {
                val testTask = project.tasks.findByName(sourceSetName)
                if (testTask == null || testTask !is org.gradle.api.tasks.testing.Test) continue

                val existingId = testTask.systemProperties["kensa.source.id"] as? String
                val resolvedId = existingId ?: sourceSetName
                if (!seenIds.add(resolvedId)) {
                    throw org.gradle.api.GradleException(
                        "Kensa site mode: source id collision on '$resolvedId' (multiple sourcesets / test tasks resolve to the same kensa.source.id). Override one explicitly: tasks.named<Test>(\"<name>\") { systemProperty(\"kensa.source.id\", \"<unique>\") }"
                    )
                }
                if (existingId != null && existingId != sourceSetName) {
                    expectedSourceIds.remove(sourceSetName)
                    expectedSourceIds.add(existingId)
                }

                val argsProvider = project.objects.newInstance(KensaSourceArgsProvider::class.java).apply {
                    siteRoot.set(extension.siteRoot)
                    sourceBundleDir.set(extension.siteRoot.dir("sources/$resolvedId"))
                    sourceId.set(resolvedId)
                    emitSourceIdArg.set(existingId == null)
                }
                testTask.jvmArgumentProviders.add(argsProvider)
                configuredTestTasks.add(testTask)
            }

            // Resolved at task-execution time, NOT at plugin-build time. The shell resources (kensa.js,
            // logo.svg) ride along with kensa-core, so updating the kensa UI just requires republishing
            // kensa-core; this configuration re-resolves and the task's @Classpath input picks up the
            // new jar's content fingerprint.
            val shellConfig = project.configurations.maybeCreate("kensaShellResources").apply {
                isCanBeConsumed = false
                isCanBeResolved = true
                isTransitive = false
                description = "Source jars for the Kensa multi-source site shell (kensa.js, logo.svg)."
            }
            val resolvedKensaCoreVersion = extension.kensaCoreVersion.get()
            project.dependencies.add(shellConfig.name, "dev.kensa:kensa-core:$resolvedKensaCoreVersion")

            val assembleTaskProvider = project.tasks.register(
                "assembleKensaSite",
                AssembleKensaSiteTask::class.java,
            ) { task ->
                task.group = "verification"
                task.description = "Assembles the Kensa multi-source site (shell + manifest) from per-sourceset bundles."
                task.siteRoot.set(extension.siteRoot)
                task.expectedSourceIds.set(expectedSourceIds)
                task.kensaVersion.set(resolvedKensaCoreVersion)
                task.sourceTitles.set(extension.sourceTitles)
                task.shellSource.from(shellConfig)
                task.sourceConfigurations.from(
                    project.fileTree(extension.siteRoot) {
                        it.include("sources/*/configuration.json")
                    }
                )
                task.manifestJsonFile.set(extension.siteRoot.file("manifest.json"))
                task.indexHtmlFile.set(extension.siteRoot.file("index.html"))
                task.kensaJsFile.set(extension.siteRoot.file("kensa.js"))
                task.logoSvgFile.set(extension.siteRoot.file("logo.svg"))
                // mustRunAfter (not dependsOn): standalone `gradle assembleKensaSite` aggregates from
                // disk without forcing Test tasks to re-run. Order is still enforced if both are invoked.
                task.mustRunAfter(configuredTestTasks)
            }

            // Auto-wire: `gradle test` (or any configured Test task) triggers the assemble as a
            // finalizer, so users don't have to remember `gradle test assembleKensaSite`. Finalizer
            // runs once after all configured Test tasks complete, regardless of pass/fail.
            for (testTask in configuredTestTasks) {
                testTask.finalizedBy(assembleTaskProvider)
            }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        val extension = kotlinCompilation.project.kensaExtension

        if (!extension.enabled.get()) {
            return false
        }

        val compilationName = kotlinCompilation.name
        return extension.sourceSets.get().contains(compilationName)
    }

    override fun getCompilerPluginId(): String = "dev.kensa.compiler-plugin"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "dev.kensa",
        artifactId = "kensa-compiler-plugin",
        version = capturedProject.kensaExtension.kensaCoreVersion.get()
    )

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.project
        val extension = project.kensaExtension

        if (extension.enabled.get()) {
            val resolvedKensaCoreVersion = extension.kensaCoreVersion.get()
            kotlinCompilation.defaultSourceSet.dependencies {
                implementation("dev.kensa:kensa-core:$resolvedKensaCoreVersion") {
                    capabilities {
                        it.requireCapability("dev.kensa:core-hooks")
                    }
                }
            }
        }

        return project.provider {
            val options = mutableListOf<SubpluginOption>()

            if (extension.enabled.get()) {
                options.add(SubpluginOption("enabled", "true"))
            }

            if(extension.debug.get()) {
                options.add(SubpluginOption("debug", "true"))
            }
            options
        }
    }

    private fun checkKensaCoreCompat(requested: String) {
        if (GradleVersion.version(requested) < GradleVersion.version(MIN_KENSA_CORE_VERSION)) {
            throw GradleException(
                "dev.kensa.gradle-plugin (this is plugin $KENSA_VERSION) requires kensa-core >= $MIN_KENSA_CORE_VERSION " +
                        "but the project requested kensa-core $requested. " +
                        "Update or remove the `kensa { kensaCoreVersion.set(...) }` override."
            )
        }
    }

    private val Project.kensaExtension get() = extensions.getByType(KensaExtension::class.java)
}
