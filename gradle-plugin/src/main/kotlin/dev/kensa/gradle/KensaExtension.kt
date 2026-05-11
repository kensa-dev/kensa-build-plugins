package dev.kensa.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

abstract class KensaExtension @Inject constructor(layout: ProjectLayout) {
    abstract val enabled: Property<Boolean>
    abstract val debug: Property<Boolean>
    abstract val sourceSets: SetProperty<String>
    abstract val site: Property<Boolean>
    abstract val siteRoot: DirectoryProperty

    /**
     * Version of dev.kensa:kensa-core (and dev.kensa:kensa-compiler-plugin) to resolve at
     * compile + task time. Defaults to the version this plugin release was tested against
     * (`KENSA_CORE_VERSION`). Override to pin a different kensa-core within the supported
     * range — see the compatibility matrix in the plugin README.
     */
    abstract val kensaCoreVersion: Property<String>

    /**
     * Per-source display labels for site mode, keyed by source id. Entries set here override
     * whatever the test runtime wrote to that source's `configuration.json` (including a value
     * `Kensa.konfigure { titleText = ... }` may have set in code). When a source id has no entry,
     * the per-source `titleText` from the test runtime is used unchanged.
     *
     * ```kotlin
     * kensa {
     *     sourceTitles.put("uiTest", "UI Tests")
     *     sourceTitles.put("acceptanceTest", "Acceptance Tests")
     * }
     * ```
     */
    abstract val sourceTitles: MapProperty<String, String>

    init {
        enabled.convention(true)
        debug.convention(false)
        sourceSets.convention(setOf("test"))
        site.convention(false)
        siteRoot.convention(layout.buildDirectory.dir("kensa-site"))
        kensaCoreVersion.convention(KENSA_CORE_VERSION)
        sourceTitles.convention(emptyMap())
    }

    /**
     * Member-extension that lets users write `sourceTitles["uiTest"] = "UI Tests"` inside the
     * `kensa { … }` block — map-literal feel without losing `MapProperty`'s lazy-Provider
     * semantics. Defined as a member (not a top-level extension) so it's resolved through the
     * `KensaExtension` implicit receiver without users needing an explicit import in their
     * build script.
     */
    operator fun MapProperty<String, String>.set(key: String, value: String) {
        put(key, value)
    }
}
