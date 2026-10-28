<!-- bmad:context -->
<!-- Verified 2026-10-28 against 7965eab. Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

## Vi6Clean

PaperMC plugin for "Voleur Industriel 6" — thieves infiltrate a factory to steal artefacts (processors, solar panels) while defenders stop them. Java 25, Gradle 9.4.1 Kotlin DSL, Paper 26.2 via paperweight userdev, NekotineCore shaded and relocated into the jar. BMad planning output (specs, refactoring notes) lives in `_bmad-output/` — read it for context; `docs/`, which `_bmad/config.toml` names as project knowledge, does not exist.

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
- `settings.gradle.kts` uses `includeBuild("NekotineCore")`, so the build compiles the submodule working tree, not the recorded pin — commit the gitlink (`git add NekotineCore`) in the same PR as any Core change.
- `datapacks/` is bundled into the jar by `shadowJar`; `resourcepacks/` is published separately (below).

## Running and verifying

- Requires JDK 25 installed locally — the toolchain is pinned to 25 with no resolver, so Gradle cannot download it. Use the wrapper (`gradlew.bat` on Windows).
- Bare `gradlew` runs the default `out` task: `spotlessApply` + `shadowJar`, then copies the jar to `Vi6CleanJarOutputPath` from `~/.gradle/gradle.properties`, or `build/dist/` without it. Dropping it into a server's `plugins/update/` restarts that server: `Vi6Main` starts NekotineCore's `UpdateFolderRestart`, which watches the update folder and calls `Bukkit.restart()` (needs `restart-script` set in `spigot.yml`, or the server just stops).
- `gradlew build` runs `spotlessCheck` and fails on formatting drift or wildcard imports — run bare `gradlew` first so Spotless fixes them.
- No tests exist (`src/test` is absent) — verify by compiling and playing on the dev server.
- Dependency locking is on: run `gradlew --write-locks` after changing dependencies, or resolution fails.
- `.github/workflows/gradle.yml` is a disabled legacy pipeline kept for reference (it triggers on a nonexistent `master` branch) — don't fix or re-enable it. `ressourcepacks.yml` is live: a push to `main` touching `resourcepacks/**` republishes the zips to the `dev` release.

## Conventions that differ from defaults

- French and English are used interchangeably in code, logs and text — either is fine; don't translate existing text.

## Known pitfalls

- `docker/` is a stale, tracked server snapshot (temurin 21, Paper 1.20.4, expects a `-coreShaded` jar Gradle no longer produces) — don't use `docker build`, and don't treat `docker/plugins/Vi6Clean/` as source; edit `src/main/resources/` instead.
- `override-config: true` overwrites server copies of `config.yml` and `tools/*.yml` with the jar defaults at boot, so hand-edits on the server are lost — tune in `src/main/resources/`.
- KoTH configs do not follow that flag: `AbstractKothEffect` reads `override_config` (underscore) from Bukkit's config with default `false`, so server `koths/*.yml` only gain missing keys and keep their old values. Delete the server copy to pick up a changed default.

<!-- /bmad:context -->
