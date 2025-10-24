package dev.kensa.gradle

import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

abstract class KensaExtension {
    abstract val enabled: Property<Boolean>
    abstract val debug: Property<Boolean>
    abstract val sourceSets: SetProperty<String>

    init {
        enabled.convention(true)
        debug.convention(false)
        sourceSets.convention(setOf("test"))
    }
}