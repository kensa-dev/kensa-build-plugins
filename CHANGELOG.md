<h2 class="github">Changelog</h2>

### v0.9.1

**Site-mode ergonomics.** Four changes that make `gradle test` / `mvn verify` produce an up-to-date aggregated report without re-running tests, and move per-source titles off system properties.

New / changed:
- **`gradle test` auto-builds the site.** Each configured Test task is now `finalizedBy(assembleKensaSite)` — running any of them automatically refreshes the aggregated site as a finalizer. Finalizer runs once after all participating Test tasks complete, regardless of pass/fail (a partial site is helpful when triaging failures). Maven side already does this via `post-integration-test` lifecycle binding.
- **`gradle assembleKensaSite` no longer re-runs tests.** Switched from `dependsOn` to `mustRunAfter` — standalone invocation aggregates whatever bundles are on disk and emits the existing "expected but not present" warning for missing sources. Order is still enforced if you invoke both explicitly.
- **`kensa { sourceTitles["id"] = title }` (Gradle) / `<sourceTitles>` (Maven) for per-source labels.** Build-declared titles overwrite the per-source `configuration.json` so the standalone HTML `<title>` matches the manifest sidebar label. Replaces the previous `tasks.named<Test>("...") { systemProperty("kensa.source.title", ...) }` pattern. The Gradle DSL also accepts `sourceTitles = mapOf("id" to title, …)` and `sourceTitles.put("id", title)` for bulk-set / explicit-method styles.

Precedence when multiple paths set a source's title:
1. Build DSL — `kensa { sourceTitles.put(id, ...) }` / `<sourceTitles>`
2. Code via `Kensa.konfigure { titleText = ... }` (e.g. a per-sourceset base class — works in site mode because each Gradle Test task forks its own JVM)
3. `kensa.source.title` system property (legacy; soft-deprecated)
4. Default `"Index"` / source id fallback

Internal:
- **Test task input hygiene.** The plugin now passes `kensa.output.root` / `kensa.source.id` via `CommandLineArgumentProvider` with `@OutputDirectory` on the per-source bundle dir and `@Internal` on the site root path. The per-source bundle dir is a tracked Test output, so UP-TO-DATE checks become accurate. Absolute paths no longer enter the Test task cache key — friendly to shared / remote Gradle build caches.

Migration:
- Drop `systemProperty("kensa.source.title", ...)` calls from your `Test` task wiring in favour of `kensa { sourceTitles.put(id, "...") }`. Code-side `Kensa.konfigure { titleText = ... }` users keep working unchanged.

Default kensa-core paired with this release is **0.8.1** (was 0.8.0). 0.8.1 carries the site-mode fix that surfaces per-source aggregate component diagrams correctly in the HTML UI. Override the default via `kensa { kensaCoreVersion.set(...) }` if you want to pin elsewhere.

### v0.9.0

**Plugin versioning is now independent of kensa-core.** Previously the Gradle and Maven plugins were released in lockstep with kensa-core, sharing a version number. Plugin-only fixes no longer require a kensa-core release; kensa-core releases no longer require a plugin release.

- New `kensa { kensaCoreVersion.set("X.Y.Z") }` extension property (Gradle) and `<kensaCoreVersion>` mojo parameter (Maven). Defaults to the version this plugin release was tested against (read from the bundled `kensa-core-version.txt`). Override to pin a different kensa-core within the supported range.
- Apply-time minimum-version check: a `kensa-core` below `MIN_KENSA_CORE_VERSION` (currently 0.8.0) is rejected with an actionable error. No upper bound — newer kensa-cores are assumed compatible until proven otherwise.
- Compatibility matrix lives in the plugin READMEs and at [kensa.dev](https://kensa.dev/docs/build-plugins). Same-version pairing (`plugin X.Y.Z` ↔ `kensa-core X.Y.Z`) is no longer implied; consult the matrix.
- CI: a kensa release no longer auto-bumps `version.txt` or stages a draft plugin release. It only bumps `kensa-core-version.txt` (the default the plugin pairs with) and runs verification.

Fixed:
- **site-common is now bundled into both plugin jars.** v0.8.0 declared `dev.kensa:site-common` as a runtime POM dep but never published it, breaking real consumers. New publish smoke test in CI catches this class of bug before tagging.

### v0.8.0 — withdrawn
> Pulled from the Gradle Plugin Portal. The published POM declared `dev.kensa:site-common` as a runtime dep but that artifact was never published to a public repo, so the plugin failed to resolve for real consumers. Use v0.9.0 or later.

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
