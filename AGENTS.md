<!-- bmad:context -->
<!-- Verified 2026-10-28 against 7965eab. Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

## Vi6Clean

PaperMC plugin for "Voleur Industriel 6" — thieves infiltrate a factory to steal artefacts (processors, solar panels) while defenders stop them. Java 25, Gradle 9.4.1 Kotlin DSL, Paper 26.2 via paperweight userdev, NekotineCore's `core` and `bots` modules (`fr.nekotine:core`, `fr.nekotine:bots`, plain jars) shaded into the jar unrelocated under `fr.nekotine.core`; Vi6 shades CommandAPI itself and relocates it to `fr.nekotine.core.commandapi` (runtime pinned strictly to 11.2.0, compile uses the locked `libs.commandapi`). BMad planning output (specs, refactoring notes) lives in `_bmad-output/` — read it for context; `docs/`, which `_bmad/config.toml` names as project knowledge, does not exist.

## Policy

- Never push to `main` — branch, push, open a PR. Agents may push their own branches and open PRs, but never merge one; a human reviews first.
- Build green with `gradlew` before opening a PR — no CI builds Java and branch protection only requires 1 approval, so the reviewer is the only gate.
- `_bmad-output/` is version-controlled, except `*.log` files — server logs hold player names and IPs; never `git add -f` them. Summarize what you need from a log in a Markdown file instead.
- Commit messages follow Conventional Commits: `type(scope): subject`, `!` before the colon for breaking changes.

## Where things are

- Entry: `src/main/resources/paper-plugin.yml` → `fr.nekotine.vi6clean.Vi6Main` / `Vi6Bootstrapper`; ProtocolLib and WorldEdit are required server plugins.
- Tool tuning: `src/main/resources/tools/<code>.yml`, loaded by `ToolHandler` via its `@ToolCode`. KoTH tuning: `src/main/resources/koths/<code>.yml`, loaded by `AbstractKothEffect`.
- Game-wide options: Configurate records in `src/main/java/fr/nekotine/vi6clean/configuration/` (`Vi6Config` → server `config.yml`, keys in kebab-case).
- `NekotineCore/` is a git submodule and its own repository — read `NekotineCore/AGENTS.md` before editing it.
- `paper-junit-server/` is a second git submodule and its own repository (in-process Paper test harness, Gradle plugin `fr.xxgoldenbluexx.paper-junit-server`) — read `paper-junit-server/AGENTS.md` before editing it.
- `bots` is pinned to the same dev bundle as Vi6 (`paper-devbundle` in `NekotineCore/gradle/libs.versions.toml` vs Vi6's locked `26.2.build.+`) — bump both together.
- `settings.gradle.kts` uses `includeBuild("NekotineCore")` and `includeBuild("paper-junit-server")` (the latter also under `pluginManagement`), so the build compiles the submodules' working trees, not the recorded pins — commit the gitlink (`git add NekotineCore` / `git add paper-junit-server`) in the same PR as any change to either.
- Tests: the plain JUnit suite (`src/test/java`, pure logic, no server; runs under `build`) is wired but empty — the folder doesn't exist yet and `gradlew test` reports NO-SOURCE until a test is added. `src/integrationTest/java` holds tests that run inside a real Paper server and use Vi6/Core types directly (`Ioc.resolve(...)`, `PaperServer.onMain(...)`).
- `test-bundle/` is the integration-test server folder (configs, `worlds/`, ProtocolLib and FAWE jars and their config folders, `plugins/Vi6Clean/`). Its contents are gitignored except `README.md`, which lists what goes in; fill it locally from the dev server and keep it in line with production.
- `datapacks/` is bundled into the jar by `shadowJar`; `resourcepacks/` is published separately (below).

## Running and verifying

- Requires JDK 25 installed locally — the toolchain is pinned to 25 with no resolver, so Gradle cannot download it. Use the wrapper (`gradlew.bat` on Windows).
- Bare `gradlew` runs the default `out` task: `spotlessApply` + `shadowJar`, then copies the jar to `Vi6CleanJarOutputPath` from `~/.gradle/gradle.properties`, or `build/dist/` without it. Dropping it into a server's `plugins/update/` restarts that server: `Vi6Main` starts NekotineCore's `UpdateFolderRestart`, which watches the update folder and calls `Bukkit.restart()` (needs `restart-script` set in `spigot.yml`, or the server just stops).
- `gradlew build` runs `spotlessCheck` and fails on formatting drift or wildcard imports — run bare `gradlew` first so Spotless fixes them.
- `gradlew test` (also run by `build`) runs the plain suite. `gradlew integrationTest` stages `test-bundle/` plus the fresh shadow jar into `build/it-server/` and boots one in-process server for the run; `build` and `check` never run it, and it fails if `test-bundle/` is missing or Vi6Clean is not loaded or disabled. `Vi6Main.onEnable` only logs its exceptions, so a half-failed enable boots fine; `Vi6SmokeIT` checks that enable completed. Gameplay not covered by tests still needs the dev server.
- Dependency locking is on: lock state is required after changing dependencies, or resolution fails. A full `gradlew --write-locks` re-resolves `26.2.build.+` and `11.2.+` and moves the dev bundle and CommandAPI; prefer `gradlew dependencies --configuration <name> --update-locks <group>:*` for just what changed, and review the lockfile diff.
- `.github/workflows/gradle.yml` is a disabled legacy pipeline kept for reference (it triggers on a nonexistent `master` branch) — don't fix or re-enable it. `ressourcepacks.yml` is live: a push to `main` touching `resourcepacks/**` republishes the zips to the `dev` release.

## Conventions that differ from defaults

- French and English are used interchangeably in code, logs and text — either is fine; don't translate existing text.

## Known pitfalls

- `docker/` is a stale, tracked server snapshot (temurin 21, Paper 1.20.4, expects a `-coreShaded` jar Gradle no longer produces) — don't use `docker build`, and don't treat `docker/plugins/Vi6Clean/` as source; edit `src/main/resources/` instead.
- `override-config: true` overwrites server copies of `config.yml`, `tools/*.yml` and `koths/*.yml` with the jar defaults when they load (boot for config and tools, each game for KoTHs), so hand-edits on the server are lost — tune in `src/main/resources/`; with `override-config: false` server copies only gain missing keys and keep their old values, so delete a copy to pick up a changed default.

<!-- /bmad:context -->
