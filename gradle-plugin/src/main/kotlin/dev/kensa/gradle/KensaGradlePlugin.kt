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

    override fun apply(target: Project) {
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
            if (!extension.enabled.get() || !extension.site.get()) return@afterEvaluate

            val siteRootDir = extension.siteRoot.get().asFile.absolutePath
            val expectedSourceIds = extension.sourceSets.get().toMutableSet()
            val seenIds = mutableSetOf<String>()

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

                testTask.systemProperty("kensa.output.root", siteRootDir)
                if (existingId == null) {
                    testTask.systemProperty("kensa.source.id", sourceSetName)
                }
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
            project.dependencies.add(shellConfig.name, "dev.kensa:kensa-core:$KENSA_CORE_VERSION")

            project.tasks.register("assembleKensaSite", AssembleKensaSiteTask::class.java) { task ->
                task.group = "verification"
                task.description = "Assembles the Kensa multi-source site (shell + manifest) from per-sourceset bundles."
                task.siteRoot.set(extension.siteRoot)
                task.expectedSourceIds.set(expectedSourceIds)
                task.kensaVersion.set(KENSA_CORE_VERSION)
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
                for (sourceSetName in extension.sourceSets.get()) {
                    project.tasks.findByName(sourceSetName)?.let { task.dependsOn(it) }
                }
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
        version = KENSA_CORE_VERSION
    )

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.project
        val extension = project.kensaExtension

        if (extension.enabled.get()) {
            kotlinCompilation.dependencies {
                implementation("dev.kensa:kensa-core:$KENSA_CORE_VERSION") {
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

    private val Project.kensaExtension get() = extensions.getByType(KensaExtension::class.java)
}
