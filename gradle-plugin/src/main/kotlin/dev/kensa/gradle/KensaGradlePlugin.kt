package dev.kensa.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class KensaGradlePlugin : KotlinCompilerPluginSupportPlugin {

    override fun apply(target: Project) {
        target.extensions.create("kensa", KensaExtension::class.java)
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
        version = KENSA_VERSION
    )

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.project
        val extension = project.kensaExtension

        if (extension.enabled.get()) {
            kotlinCompilation.dependencies {
                implementation("dev.kensa:kensa-core:$KENSA_VERSION") {
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