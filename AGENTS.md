<!-- bmad:context -->
<!-- Verified 2026-09-25 against d82b73b. Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

## Vi6Clean

PaperMC plugin for "Voleur Industriel 6" — thieves infiltrate a factory to steal artefacts (processors, solar panels) while defenders stop them. Java 25, Gradle 9.4.1 Kotlin DSL, Paper 26.2 via paperweight, shaded with NekotineCore. Planning artifacts live in `_bmad-output/`; `docs/` (named by `_bmad/config.yaml`) does not exist.

## Policy

- Never push to `main` directly — branch, push, open a PR. Agents may push their own branches and open PRs, but never merge one; a human reviews first.
- Every PR must build green, and nothing enforces that automatically — branch protection only requires 1 approval, so the reviewing human is the gate; build with `gradlew` before opening the PR.
- Commit messages follow the [Conventional Commits](https://www.conventionalcommits.org/) specification: `type(scope): short subject`, `!` before the colon for breaking changes.

## Where things are

- Entry: `src/main/resources/paper-plugin.yml` → `Vi6Main` / `Vi6Bootstrapper`.
- Tuning tools and KoTHs: edit `src/main/resources/tools/*.yml` and `koths/*.yml`; the copies under `docker/plugins/Vi6Clean/` are live server data and get overwritten at boot (`override_config` defaults true). Game-wide options are Configurate records under `src/main/java/fr/nekotine/vi6clean/configuration/`.
- `NekotineCore/` is a submodule and its own repository — read `NekotineCore/AGENTS.md` before editing anything in there.
- Core changes only reach other clones after a gitlink bump: Vi6Clean builds the submodule working tree, not the recorded pin, so commit `git add NekotineCore` in the same PR.
- CI: `.github/workflows/gradle.yml` is a disabled legacy pipeline kept for reference — don't fix or re-enable it. `ressourcepacks.yml` is live: a push touching `resourcepacks/**` publishes zips to the `dev` release.

## Running and verifying

- Build requires JDK 25 — toolchain is fixed at 25 and no toolchain resolver is configured, so Gradle cannot download it; use the wrapper (`./gradlew`, `gradlew.bat` on Windows).
- Bare `gradlew` runs `out`: `spotlessApply` + `shadowJar`, then drops the jar into your running dev server (`Vi6CleanJarOutputPath` in user `~/.gradle/gradle.properties`) which restarts to load it; without that property the jar lands in `build/dist/`.
- `gradlew build` runs `check`, which includes `spotlessCheck` — formatting drift and wildcard imports fail it, so run bare `gradlew` (or `spotlessApply`) first to fix instead of fail.
- No tests exist — `src/test` is empty with no test dependencies, so verification means compiling and playing it on the server.
- Dependency locking is on: run `gradlew --write-locks` after adding or changing dependencies, plain resolution fails otherwise.

## Conventions that differ from defaults

- French and English are used interchangeably — either is fine; don't translate existing text.

## Known pitfalls

- `docker/` holds a full dev server (world, plugin jars), but its Dockerfile is stale (temurin 21, Paper 1.20.4, expects a `-coreShaded` jar name Gradle no longer produces) — `docker build` is not the way to run a change; `gradlew` is.

<!-- /bmad:context -->
