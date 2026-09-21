<!-- bmad:context -->
<!-- Verified 2026-09-21. Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

## Vi6Clean

Minecraft plugin for the Vi6Clean game mode. Java 25, Gradle Kotlin DSL, Paper API. Planning lives in `_bmad-output/`.

## Policy

- Never modify `NekotineCore/` — it is a git submodule (external dependency).
- Use Java 25 for all compilation and toolchain configuration.
- Follow conventional commits specification for all commit messages.

## Where things are

- Main source: `src/main/java/fr/nekotine/vi6clean/`
- Resources: `src/main/resources/paper-plugin.yml`
- Datapacks: `datapacks/` (game data)
- Resource packs: `resourcepacks/` (client assets)
- Build config: `build.gradle.kts`, `gradle/libs.versions.toml`
- CI/CD: `.github/workflows/gradle.yml`

## Running and verifying

- Build: `./gradlew shadowJar` (includes Spotless formatting automatically)
- Output JAR: `build/dist/` or custom path via `Vi6CleanJarOutputPath` property
- Spotless formatting runs on build; do not bypass with `--no-spotless`
- CI builds Docker image on master branch pushes

## Conventions that differ from defaults

- Java toolchain version is 25, not 21 (CI may differ — always use 25 locally)
- Spotless enforces Eclipse 4.39 style with wildcard imports forbidden
- Shadow plugin relocates packages: `fr.nekotine.core` → `fr.nekotine.vi6clean.nekotinecore`

## Known pitfalls

- No test directory exists; do not attempt to run tests
- NekotineCore submodule must be initialized before build: `git submodule update --init`
- ProtocolLib repository is commented out in build.gradle.kts; use mavenLocal() if needed

<!-- /bmad:context -->
