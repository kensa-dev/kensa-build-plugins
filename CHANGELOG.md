<h2 class="github">Changelog</h2>

### v0.8.0
New features:
  - **Site mode** — aggregate per-sourceset (Gradle) or per-execution (Maven) test bundles into a single multi-source HTML site.
    - **Gradle**: new `site` and `siteRoot` properties on the `kensa { }` extension; `assembleKensaSite` task auto-registered when `site = true`. Wires `kensa.output.root` and `kensa.source.id` system properties onto the configured `Test` tasks. [Docs](https://kensa.dev/docs/build-plugins/gradle-plugin).
    - **Maven**: new `assemble-site` mojo (defaults to `post-integration-test`). Per-execution bundles are driven by Surefire/Failsafe `systemPropertyVariables`. [Docs](https://kensa.dev/docs/build-plugins/maven-plugin).
    - Site shell (`kensa.js`, `logo.svg`) is resolved from the `dev.kensa:kensa-core` jar at task/mojo execution time — UI changes ride along on a kensa-core republish, no plugin republish required. [Docs](https://kensa.dev/docs/build-plugins/site-mode).
  - Pairs against `kensa-core` 0.8.0 — see the kensa CHANGELOG for the new Field Assertion DSL, hamkrest variants, experimental UI test framework, component diagrams, and other runtime additions.

Changed:
  - **Kotlin stdlib no longer pinned in the plugin POMs.** The Gradle plugin now enforces a minimum Kotlin version at `apply` time and otherwise lets the consuming project's Kotlin version flow through. Reduces classpath conflicts in projects on a different Kotlin minor.
  - The `kensa-core` version this release pairs with is read from a sibling `kensa-core-version.txt` file, decoupling plugin versioning from kensa-core.

### v0.5.30
- Initial release - tracking Kensa version
