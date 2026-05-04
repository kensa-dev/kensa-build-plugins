package dev.kensa.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

abstract class KensaExtension @Inject constructor(layout: ProjectLayout) {
    abstract val enabled: Property<Boolean>
    abstract val debug: Property<Boolean>
    abstract val sourceSets: SetProperty<String>
    abstract val site: Property<Boolean>
    abstract val siteRoot: DirectoryProperty

    init {
        enabled.convention(true)
        debug.convention(false)
        sourceSets.convention(setOf("test"))
        site.convention(false)
        siteRoot.convention(layout.buildDirectory.dir("kensa-site"))
    }
}
