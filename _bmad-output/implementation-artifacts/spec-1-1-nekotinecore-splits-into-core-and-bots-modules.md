---
title: 'Story 1.1: NekotineCore splits into core and bots modules, and Vi6 shades both'
type: 'refactor'
created: '2026-11-06'
status: 'done'
route: 'dispatch'
baseline_commit: 'ba7ed8c9461399617f3b23d26e9fa9c3e29d370b'
baseline_commit_nekotinecore: 'd18d03159e1c89cec2b74cc9bcd03a94eb527730'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/NekotineCore/AGENTS.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** NekotineCore is one Paper-API-only project that shades and relocates CommandAPI itself, so it has no place for NMS bot code. Adding NMS to it would pin every Core consumer to one Minecraft version.

**Approach:** Turn NekotineCore into a multi-module build: `core` (all of today's code, Paper API only, unpinned) and `bots` (paperweight userdev on Vi6's dev bundle, depends on `core`, placeholder class only). Both modules publish plain jars. Vi6 consumes both through the existing `includeBuild`, and takes over shading and relocating CommandAPI. Nothing changes for players.

## Boundaries & Constraints

**Always:**
- `core` never depends on `bots`, and neither module applies shadow (AD-1, AD-2).
- Vi6's jar holds one unrelocated `fr.nekotine.core`, including `fr.nekotine.core.bot`, and CommandAPI stays under `fr.nekotine.core.commandapi`.
- Move Core sources with `git mv` so history follows them.
- Lockfiles change only for what changed: `--update-locks <group>:*` in Vi6, and a lockfile written for the new `bots` module only. Review every lockfile diff.
- Spotless (eclipse 4.39, `removeUnusedImports`, `forbidWildcardImports`, ktlint) runs on every NekotineCore module.
- The NekotineCore branch targets `dev`. The Vi6Clean PR targets `main` and commits the NekotineCore gitlink.
- **Decision (user, 2026-11-06): CommandAPI runtime stays 11.2.0.** Vi6's runtime classpath is pinned strictly to `dev.jorel:commandapi-paper-shade:11.2.0`, which is what the server runs today. The compile classpath keeps its locked 11.2.1-SNAPSHOT, so compile and runtime stay mismatched, as they are now.
- **Decision (user, 2026-11-06):** the spec stays whole at about 2,000 tokens.

**Never:**
- No bot logic, no `IPluginModule` and no NMS use in `bots` yet (Story 1.3).
- Don't move paper-junit-server and don't create `bots-test-host` (Story 1.2).
- No full `--write-locks` in Vi6. No Paper or dev-bundle upgrade.
- Don't touch Vi6 game code or `test-bundle/`.

</frozen-after-approval>

## Code Map

- `NekotineCore/settings.gradle` -- Groovy, one line. Replace it with `settings.gradle.kts`: `rootProject.name = "NekotineCore"`, `include("core", "bots")`.
- `NekotineCore/build.gradle.kts` -- today's single-project build: shadow, the CommandAPI relocate, and `apiElements`/`runtimeElements` rewired to the shadow jar with commandapi excluded. That rewiring and the shadow plugin go away. Keep the shared parts (group `fr.nekotine`, version, repositories, toolchain 25, UTF-8, Spotless, `lockAllConfigurations`) in a root `subprojects {}` block.
- `NekotineCore/gradle/libs.versions.toml` -- the shared catalog. Add the paperweight plugin `2.0.0-beta.21` and a pinned `paper-devbundle = "26.2.build.65-beta"`. Drop `shadow` once it is unused.
- `NekotineCore/src/**` -- moves to `core/src/**` with `git mv`, 13 files of which use CommandAPI. `src/test` holds 5 fully commented-out JUnit files; move them as they are.
- `NekotineCore/gradle.lockfile` -- moves to `core/gradle.lockfile`. Its content stays the same apart from removed `shadow` entries.
- `NekotineCore/AGENTS.md` -- stale: it says Vi6 relocates Core to `fr.nekotine.vi6clean.nekotinecore`. Update it for the modules and plain jars.
- `build.gradle.kts` (Vi6) -- `implementation(project(":NekotineCore"))` and `compileOnly(libs.commandapi)`. The relocate and `verifyShadowJarLayout` stay; extend the latter.
- `settings.gradle.kts` (Vi6) -- `includeBuild("NekotineCore")` is unchanged. Substitution maps `fr.nekotine:core` and `fr.nekotine:bots` to the included projects.
- `gradle.lockfile` (Vi6) -- dev bundle `26.2.build.65-beta`; CommandAPI `11.2.1-SNAPSHOT` on `compileClasspath` only.
- `AGENTS.md` (Vi6) -- the stack line about how Core is shaded needs updating.

## Tasks & Acceptance

**Execution:**
- [x] `NekotineCore/settings.gradle.kts` -- create it with both modules, and delete `settings.gradle` -- multi-module root.
- [x] `NekotineCore/build.gradle.kts` -- reduce it to shared `subprojects {}` config with no shadow -- one place for conventions.
- [x] `NekotineCore/core/build.gradle.kts` -- `java-library`; `compileOnly` paper-api, protocollib and fawe; `implementation(libs.commandapi)`; JUnit setup carried over -- plain jar, CommandAPI reaches the host transitively.
- [x] `NekotineCore/core/src/**`, `core/gradle.lockfile` -- `git mv` from the root.
- [x] `NekotineCore/bots/build.gradle.kts` -- `java-library`, paperweight userdev with `paperDevBundle(<pinned>)`, `api(project(":core"))` -- NMS module.
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/NekotineBots.java` -- final placeholder class with a private constructor and Javadoc -- gives the module a class without registering a module.
- [x] `NekotineCore/bots/gradle.lockfile` -- `gradlew :bots:dependencies --write-locks` -- lock the new module only.
- [x] `NekotineCore/gradle/libs.versions.toml`, `AGENTS.md` -- the catalog entries and docs above.
- [x] `build.gradle.kts` (Vi6) -- `implementation("fr.nekotine:core")`, `implementation("fr.nekotine:bots")`. Keep `compileOnly(libs.commandapi)` and add a `runtimeOnly` CommandAPI dependency that is `strictly("11.2.0")`. Extend `verifyShadowJarLayout` to require `fr/nekotine/core/bot/NekotineBots.class` and one Core class, and to forbid unrelocated `dev/jorel/` -- shading moves to the host.
- [x] `gradle.lockfile` (Vi6) -- `gradlew dependencies --update-locks dev.jorel:*` -- the runtime classpath gains 11.2.0, and the compile classpath must stay 11.2.1-SNAPSHOT.
- [x] `AGENTS.md` (Vi6) -- describe `core`/`bots` and Vi6-side CommandAPI shading.
- [x] Gitlink -- commit the NekotineCore pointer in the Vi6Clean PR.

**Acceptance Criteria:**
- Given the migrated NekotineCore, when `gradlew build` runs in `NekotineCore/`, then `core` and `bots` each produce a plain jar, no shadow task exists, and Spotless checks both.
- Given `NekotineCore/core`, when its dependencies are listed, then it has no paperweight and no `bots` dependency.
- Given Vi6Clean, when `gradlew dependencies --configuration runtimeClasspath` runs, then `fr.nekotine:core` appears once (as a project substitution) and no dev-bundle or Minecraft server artifact is on it.
- Given Vi6Clean, when bare `gradlew`, then `gradlew build`, then `gradlew integrationTest` run, then all three pass, including `verifyShadowJarLayout`, `Vi6SmokeIT`, `Vi6MapIT` and `Vi6ConfigIT`.
- Given the new jar on the dev server, when a human plays, then commands and gameplay behave as before (manual, by the user).

## Design Notes

Vi6 declares `core` directly and also gets it through `bots`'s `api(project(":core"))`. Substitution resolves both to the same included project, so the shadow jar holds one copy. The dev bundle is pinned exactly in Core's catalog rather than `26.2.build.+`, because each build in a composite resolves on its own and AD-1 needs `bots` to match Vi6's lock. Bumping one means bumping both.

## Verification

**Commands:**
- `NekotineCore/gradlew.bat build` (in `NekotineCore/`) -- expected: BUILD SUCCESSFUL
- `gradlew.bat` then `gradlew.bat build` then `gradlew.bat integrationTest` (in Vi6Clean) -- expected: all green
- `gradlew.bat dependencies --configuration runtimeClasspath` -- expected: one `fr.nekotine:core`, and `commandapi-paper-shade` at 11.2.0

**Manual checks:**
- Lockfile diffs in both repositories contain only CommandAPI (Vi6), the moved file (Core) and the new `bots` lock.

## Implementation Notes

- Vi6 lockfile: the literal `gradlew dependencies --update-locks dev.jorel:*` re-resolves `11.2.+` on `compileClasspath` too, and codemc no longer lists 11.2.1-SNAPSHOT, so compile dropped to 11.2.0 (and the stale `integrationTestAnnotationProcessor` entry appeared). Used `gradlew dependencies --configuration runtimeClasspath|testRuntimeClasspath --update-locks dev.jorel:*` instead: the diff is one `commandapi-paper-shade:11.2.0=runtimeClasspath,testRuntimeClasspath` line plus `runtimeClasspath` leaving `empty=`. Compile keeps 11.2.1-SNAPSHOT from the Gradle cache; a cache wipe would break a fresh compile resolution (pre-existing risk).
- NekotineCore root applies Spotless (ktlint on root `*.gradle.kts`) and needs `repositories { mavenCentral() }` for it. Root `out` = every module's `spotlessApply` + `jar`.
- `core/gradle.lockfile` was edited by hand (only `shadow` dropped from `empty=`) to avoid re-resolving `paper-api 26.2.build.+`.
- Jar parity: the new shadow jar was diffed against a HEAD build (temporary worktree with NekotineCore at `d18d031`): identical entries and bytes except the added `fr/nekotine/core/bot/NekotineBots.class`.
- Bare `gradlew` was run with `-PVi6CleanJarOutputPath=build/dist` so it did not drop a jar into the dev server's `plugins/update/`.

## Spec Change Log

## Review Triage Log

Review pass 1 (2026-11-06). Layers: Blind Hunter (B), Edge Case Hunter (E), Verification Gap (G).

| # | Finding | Verdict | Evidence | Route |
|---|---------|---------|----------|-------|
| B1 / E1 / G1 | No automated check that the `bots` dev bundle (pinned to 65-beta) matches Vi6's locked `26.2.build.+` | low | Real: a full `--write-locks` in Vi6 diverges silently. `bots` holds only a placeholder with no NMS, so there is no runtime effect until Story 1.3 | defer |
| B2 | paperweight plugin version written in two places (Core catalog, Vi6 `plugins {}`) | low | Real duplication. The `AGENTS.md` bump note covers only the dev bundle; the fix is a doc line or moving it to the catalog | defer |
| B3 | `verifyShadowJarLayout` doesn't forbid `net/minecraft/` and similar | false | `lockAllConfigurations` fails resolution on any unlocked module, and the runtime locks for `bots` and Vi6 hold only `commandapi-paper-shade:11.2.0`, so a leak can't happen silently | reject |
| B4 | `out` (deploy copy) doesn't run `verifyShadowJarLayout` | low | Pre-existing: before this change the check was also only on `check` | defer |
| B5 / E3 | Runtime CommandAPI pin `11.2.0` is a literal outside the catalog | low | Real: bumping `libs.commandapi` leaves the runtime pin unseen in `build.gradle.kts`. The fix moves the literal into the catalog | patch |
| B6 | Fresh-machine failure (11.2.1-SNAPSHOT only in the local cache) not in `AGENTS.md` pitfalls | medium | Pre-existing (the compile lock predates this change); the fix edits `AGENTS.md` | defer |
| E2 | Compile 11.2.1-SNAPSHOT vs runtime 11.2.0 mismatch | false | This is the user's frozen decision (compile and runtime stay mismatched, as before) | reject |
| B7 | NekotineCore `AGENTS.md` says modules "publish plain jars" and "Nothing is published" | low | Real wording clash introduced here; the fix edits an agent-context file | defer |
| B8 | NekotineCore README not updated for the module layout | low | The README is the `IPluginModule` inventory, which is unchanged; adding a layout section is more than a correction, and no reader is misled | reject |
| B9 | Vi6 `AGENTS.md` says "locked `26.2.build.+`" instead of the concrete version | low | Real imprecision; the fix edits `AGENTS.md` | defer |
| B10 | `useJUnitPlatform()` only in `core`, so future `bots` unit tests would be silently skipped | low | Real: a Gradle `Test` task without JUnit Platform finds no Jupiter tests. The fix moves one block into `subprojects {}` | patch |
| B11 | `.$MapModule.drawio.bkp` editor backup carried into `core/src` | low | Pre-existing junk file, moved by the rename | defer |
| B12 | The jar check requires the placeholder `NekotineBots.class`, which may be renamed later | false | A rename fails the check loudly at build time, which is correct behaviour, not a defect | reject |
| B13 | `bots` lacks ProtocolLib on its compile classpath | false | Nothing in this story needs it; Story 1.3 adds what it uses | reject |
| B14 | No runtime test that `fr.nekotine.core.bot` loads in the plugin | low | Presence is verified by `verifyShadowJarLayout`; a runtime load check adds a test with no current behaviour to protect | reject |
| B15 | `settings-gradle.lockfile` not regenerated after `settings.gradle` became `.kts` | false | The Core build passes with locking on, and the settings lock content (`empty=incomingCatalogForLibs0`) doesn't depend on the script language | reject |
| E4 | Non-Vi6 consumers of `core` no longer get shaded CommandAPI | false | Intended by AD-2 and documented in NekotineCore `AGENTS.md` line 11 (the host shades and relocates CommandAPI) | reject |
