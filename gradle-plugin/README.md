# Kensa Gradle Plugin

Gradle plugin for the Kensa BDD testing framework — wires the Kotlin compiler plugin into your build and (in v0.8+) supports site mode for aggregating multiple test sourcesets into one browsable report.

## Documentation

- **[Quickstart](https://kensa.dev/docs/intro)**
- **[Gradle plugin reference](https://kensa.dev/docs/build-plugins/gradle-plugin)**
- **[Site mode](https://kensa.dev/docs/build-plugins/site-mode)** — multi-sourceset aggregated reports
- **[Configuration reference](https://kensa.dev/docs/api/configuration)**

## Coordinates

```kotlin
plugins {
    id("dev.kensa.gradle-plugin") version "<version>"
}
```

See the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/dev.kensa.gradle-plugin) for the latest version.

## kensa-core compatibility

The plugin and `dev.kensa:kensa-core` version independently (since plugin v0.9.0). Each plugin release ships a default kensa-core it was tested against; override to pin a different one within the supported range.

| Plugin     | Default kensa-core | Min kensa-core | Notes                                |
| ---------- | ------------------ | -------------- | ------------------------------------ |
| 0.9.x      | 0.8.0              | 0.8.0          | Plugin and kensa-core versioned independently; site-mode ergonomics (sourceTitles DSL, auto-assemble) added in 0.9.1 |
| 0.7.x      | 0.7.x              | —              | Same-version pairing (no override)   |

> v0.8.0 was withdrawn from the Gradle Plugin Portal — its POM declared an unpublished `dev.kensa:site-common` dep. Use 0.9.0 or later.

Override the kensa-core version:

```kotlin
kensa {
    kensaCoreVersion.set("0.8.1")
}
```

No upper bound — newer kensa-cores are assumed compatible until proven otherwise. A version below the minimum fails fast at apply time.

## Site-mode source titles

Set per-source labels for the aggregated site sidebar via the `sourceTitles` map. Entries here override the title the test runtime wrote into each source's `configuration.json` (and also rewrite that file so the standalone per-source HTML page title matches).

```kotlin
kensa {
    site = true
    sourceSets = setOf("test", "uiTest")
    sourceTitles["uiTest"] = "UI Tests"
    sourceTitles["test"] = "Unit Tests"
}
```

Bulk-set the whole map via a literal when that reads better:

```kotlin
kensa {
    site = true
    sourceSets = setOf("test", "uiTest")
    sourceTitles = mapOf(
        "uiTest" to "UI Tests",
        "test"   to "Unit Tests",
    )
}
```

Sources without an entry keep whatever `Kensa.konfigure { titleText = "..." }` set in code (each Gradle `Test` task forks its own JVM, so per-sourceset base classes with their own `konfigure` work cleanly in site mode without any build DSL).

`gradle test` (or any other configured `Test` task) automatically triggers `assembleKensaSite` as a finalizer — you don't need to chain `assembleKensaSite` on the command line. Standalone `gradle assembleKensaSite` aggregates whatever bundles already exist without re-running tests.
