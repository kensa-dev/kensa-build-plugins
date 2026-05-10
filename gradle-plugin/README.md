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
| 0.9.x      | 0.8.0              | 0.8.0          | First decoupled release              |
| 0.7.x      | 0.7.x              | —              | Same-version pairing (no override)   |

> v0.8.0 was withdrawn from the Gradle Plugin Portal — its POM declared an unpublished `dev.kensa:site-common` dep. Use 0.9.0 or later.

Override the kensa-core version:

```kotlin
kensa {
    kensaCoreVersion.set("0.8.1")
}
```

No upper bound — newer kensa-cores are assumed compatible until proven otherwise. A version below the minimum fails fast at apply time.
