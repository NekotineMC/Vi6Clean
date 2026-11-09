---
title: 'Story 1.2: NekotineCore owns paper-junit-server and boots its own test host'
type: 'chore'
created: '2026-11-09'
status: 'done'
route: 'dispatch'
baseline_commit: 'c9e76cd6035b1ee7303d9e76f73c360c3f9ee100'
baseline_commit_nekotinecore: 'c9ccddf2ab73d0818782c7438306e63e33636fef'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/NekotineCore/AGENTS.md'
  - '{project-root}/NekotineCore/paper-junit-server/README.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The in-process Paper test harness is a submodule of Vi6Clean, so NekotineCore cannot test the `bots` library on a real server without depending on Vi6 (AD-1, AD-13).

**Approach:** Move paper-junit-server to a NekotineCore submodule that Vi6Clean reaches through the nested path. Add a `bots-test-host` module to NekotineCore: a tiny Paper plugin that shades `core` and `bots`, relocates CommandAPI itself, and is booted with ProtocolLib by NekotineCore's own `integrationTest` suite, where a smoke test asserts it is enabled.

## Boundaries & Constraints

**Always:**
- `bots-test-host` contains no NMS code; it applies paperweight userdev (the harness needs it) on the same pinned `paper-devbundle` as `bots`.
- `bots-test-host` shades exactly one `core` and one `bots`, unrelocated, and relocates CommandAPI to `fr.nekotine.core.commandapi` (AD-2). Its own code lives outside `fr.nekotine.core`, so Core's module scan never sees it.
- paper-junit-server stays at its current pin `1a8b85be5d10b584f6f9265a296092a6c247cdd8`, unchanged.
- Lockfiles: write a lockfile for the new module only; no other NekotineCore or Vi6 lockfile changes unless a diff proves it needed, and every diff is reviewed.
- Branches: NekotineCore from `origin/dev` with a PR to `dev`; Vi6Clean from `main` with a PR to `main` that commits the NekotineCore gitlink.
- Bare `gradlew` in Vi6Clean is always run with `-PVi6CleanJarOutputPath=build/dist`.
- **Decision (user, 2026-11-09): the spec stays whole at about 2,200 tokens.**
- **Decision (agent, 2026-11-09):** Vi6Clean has no README today; create a short root `README.md` that says what the repo is and that a clone needs `git submodule update --init --recursive` (or `git clone --recursive`).

**Never:**
- No bot logic, roster, `IPluginModule` or test beyond the host smoke test (Story 1.3+).
- No change inside paper-junit-server and no change to Vi6 game code, `test-bundle/` or Vi6's integration tests.
- No full `--write-locks` in either repository, no Paper or dev-bundle upgrade.
- No `join-classpath` between Nekotine hosts.

</frozen-after-approval>

## Code Map

- `.gitmodules`, `settings.gradle.kts` (Vi6) -- drop the `paper-junit-server` submodule (`git rm paper-junit-server`); both `includeBuild("paper-junit-server")` lines (one in `pluginManagement`) become `"NekotineCore/paper-junit-server"`.
- `build.gradle.kts` (Vi6) -- `paperJunitServer {}` block unchanged; reference for the host's block (`targetPlugin`, `pluginJar = shadowJar`, `bundle`).
- `paper-junit-server/README.md`, `PaperJunitServerPlugin.java` -- consumer needs `pluginManagement { includeBuild(...) }` plus a plain `includeBuild(...)`, paperweight userdev applied, `targetPlugin`, `pluginJar`, `bundle`. Tests go in `src/integrationTest/java` and use `PaperServer.awaitTicks/onMain/plugin()`.
- `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6SmokeIT.java` -- model for the host smoke test.
- `test-bundle/README.md`, `.gitignore` (`test-bundle/*`, `!test-bundle/README.md`) (Vi6) -- model for the host bundle and its ignore rules. Vi6's local `test-bundle/plugins/ProtocolLib.jar` is the source for filling the host bundle locally.
- `NekotineCore/settings.gradle.kts` -- add the two `includeBuild("paper-junit-server")` lines and `include("bots-test-host")`.
- `NekotineCore/build.gradle.kts` -- `subprojects {}` already applies java-library, Spotless, locking, toolchain 25 to every module; root `out` covers every module's `jar`.
- `NekotineCore/gradle/libs.versions.toml` -- has `paper-devbundle`, `paperweight`; add `shadow` (Vi6 uses `com.gradleup.shadow` `9.4.+`).
- `NekotineCore/core/.../NekotinePlugin.java` -- the host's main class extends it (Ioc, modules, CommandAPI load/enable). `NekotineCoreBootstrapper` is not needed.
- `NekotineCore/core/gradle.lockfile` -- CommandAPI locked at `11.2.0`; the host's runtime should resolve the same.
- `src/main/resources/paper-plugin.yml` (Vi6) -- model for the descriptor's ProtocolLib dependency (`load: BEFORE`, `required: true`, `join-classpath: true`).

## Tasks & Acceptance

**Execution:**
- [x] `NekotineCore/.gitmodules`, `NekotineCore/paper-junit-server` -- `git submodule add https://github.com/XxGoldenbluexX/paper-junit-server.git paper-junit-server`, checked out at the pin.
- [x] `NekotineCore/settings.gradle.kts` -- include the harness (plugin management and plain) and `bots-test-host`.
- [x] `NekotineCore/gradle/libs.versions.toml` -- add the shadow plugin entry.
- [x] `NekotineCore/bots-test-host/build.gradle.kts` -- paperweight userdev + pinned dev bundle, shadow, `fr.xxgoldenbluexx.paper-junit-server`; `implementation(project(":bots"))` (brings `core`); shadow relocates `dev.jorel.commandapi`; `paperJunitServer { targetPlugin = "BotsTestHost"; pluginJar = shadowJar; bundle = test-bundle }`; a `verifyShadowJarLayout`-style check on `check` (Core and bots classes present, no `dev/jorel/`, CommandAPI under `fr/nekotine/core/commandapi/`).
- [x] `NekotineCore/bots-test-host/src/main/java/fr/nekotine/botstesthost/BotsTestHost.java` -- extends `NekotinePlugin`, nothing else yet.
- [x] `NekotineCore/bots-test-host/src/main/resources/paper-plugin.yml` -- name `BotsTestHost`, api-version `26.2`, required ProtocolLib dependency.
- [x] `NekotineCore/bots-test-host/src/integrationTest/java/fr/nekotine/botstesthost/it/BotsTestHostSmokeIT.java` -- asserts the host and ProtocolLib are enabled and the test class loads through the host's classloader.
- [x] `NekotineCore/bots-test-host/test-bundle/README.md`, `NekotineCore/.gitignore` -- README lists contents (`server.properties` with `spawn-protection=0`, `online-mode=false`, `eula.txt`, `plugins/ProtocolLib.jar` matching the catalog version) and what never goes in; ignore everything else in the folder. Fill the folder locally.
- [x] `NekotineCore/bots-test-host/gradle.lockfile` -- `gradlew :bots-test-host:dependencies --write-locks`; review it.
- [x] `NekotineCore/AGENTS.md` -- describe the harness submodule, `bots-test-host`, its bundle and `gradlew integrationTest`; fix the "publish plain jars" wording (deferred item).
- [x] Vi6 `.gitmodules`, `settings.gradle.kts` -- remove the submodule, point at the nested path.
- [x] Vi6 `AGENTS.md` -- new submodule layout, recursive clone; deferred doc fixes in the same file: name the concrete locked dev bundle (`26.2.build.65-beta`) and record that CommandAPI 11.2.1-SNAPSHOT resolves only from the local Gradle cache.
- [x] Vi6 `README.md` -- create it (decision above).
- [x] Gitlink -- commit the new NekotineCore pointer in the Vi6Clean PR.

**Acceptance Criteria:**
- Given Vi6Clean after the move, when listing submodules, then `paper-junit-server` exists only at `NekotineCore/paper-junit-server` and `git submodule update --init --recursive` on a fresh clone checks it out.
- Given Vi6Clean, when bare `gradlew` (with the output-path property), `gradlew build` and `gradlew integrationTest` run, then all pass (`Vi6SmokeIT`, `Vi6MapIT`, `Vi6ConfigIT`).
- Given NekotineCore, when `gradlew build` runs, then `bots-test-host`'s shadow jar holds `paper-plugin.yml`, Core and bots classes, relocated CommandAPI, and no NMS or `net/minecraft` classes, and its layout check passes.
- Given a filled `bots-test-host/test-bundle/`, when `gradlew integrationTest` runs in NekotineCore, then one server boots with BotsTestHost and ProtocolLib, and `BotsTestHostSmokeIT` passes.

## Design Notes

When Vi6 includes NekotineCore, NekotineCore's own `includeBuild("paper-junit-server")` resolves to the same directory as Vi6's nested include, so Gradle sees one build. Vi6 still needs its own `pluginManagement` include, because plugins from a nested build's plugin management are not visible to the root.

## Verification

**Commands:**
- `gradlew.bat build` then `gradlew.bat integrationTest` (in `NekotineCore/`) -- expected: BUILD SUCCESSFUL, smoke test passed
- `gradlew.bat -PVi6CleanJarOutputPath=build/dist`, `gradlew.bat build`, `gradlew.bat integrationTest` (in Vi6Clean) -- expected: all green
- `git submodule status --recursive` (Vi6Clean) -- expected: `NekotineCore` and `NekotineCore/paper-junit-server` at `1a8b85b…`, no top-level `paper-junit-server`

**Manual checks:**
- Lockfile diffs: only the new `bots-test-host/gradle.lockfile`.

## Implementation Notes

- NekotineCore `de40085` on `feat/bots-1-2-harness-move` (from `origin/dev` `c9ccddf`); Vi6Clean `604d781` on `feat/bots-1-2-harness-move` (from `main` `c9e76cd`), committing the NekotineCore gitlink. Both local only: not pushed, no PRs opened.
- `--write-locks` on `:bots-test-host:dependencies` wrote only `bots-test-host/gradle.lockfile` (no other lockfile diff in either repo, nor inside paper-junit-server): dev bundle `26.2.build.65-beta`, `commandapi-paper-shade:11.2.0` on runtime, JUnit 5.13.4 on the integration suite.
- The layout check also forbids `net/minecraft/`, `com/mojang/` and duplicate entries, and requires `paper-plugin.yml` and `BotsTestHost.class`.
- Local bundle filled with a minimal `server.properties` (flat world, no RCON), `eula.txt` and Vi6's `ProtocolLib.jar`, which is `5.5.0-SNAPSHOT-2d3b3e3`, not the catalog's `5.4.0` (Vi6's bundle has the same mismatch).

## Spec Change Log

## Review Triage Log

Review pass 1 (2026-11-09). Layers: Blind Hunter (B), Edge Case Hunter (E), Verification Gap (G).

| # | Finding | Verdict | Evidence | Route |
|---|---------|---------|----------|-------|
| G1 / B4a | Smoke test never observes that `setupModules()` completed (it swallows exceptions; `Ioc.resolve(BotsTestHost.class)` is registered earlier in `setupIoc()`) | medium | `NekotinePlugin.java:67-95` catches `Exception` and only logs; no assertion resolves a module | patch |
| B4b | Smoke test doesn't resolve a bots module | false | `bots` has no `IPluginModule` yet (`NekotineBots` is a placeholder class); Story 1.3 adds the roster | reject |
| B4c | `assertTrue(instanceof)` instead of `assertInstanceOf` | low | Weaker failure message; one-line direct correction | patch |
| B4d / E3 | `loadedThroughHostClassLoader` may run before the server ticked | false | The harness's session listener boots the server and waits for the first tick before any test runs; `Vi6SmokeIT` relies on the same | reject |
| B1 / E1 | Vi6 gitlink `5fe75bb` isn't on any remote | false | Nothing is pushed yet by design; the PR step pushes NekotineCore and opens its PR before the Vi6 PR | reject |
| B2 | Existing clones get no migration steps (leftover top-level `paper-junit-server/`) | low | Every existing clone, including the maintainer's, meets it; a README note fixes it | patch |
| B3 | AGENTS.md gitlink order omits pushing each inner commit first | low | Real gap in the new workflow line; the fix edits an agent-context file | defer |
| B5 | Bundle README asks for the catalog ProtocolLib and suggests Vi6's jar, which is 5.5.0-SNAPSHOT | low | Contradictory instruction in one sentence; direct wording fix | patch |
| B6 | Shadow plugin `9.4.+` floats and isn't locked; two builds in the composite may load different versions | low | Plugin classpath isn't covered by `lockAllConfigurations`; same pattern already in Vi6's catalog | defer |
| B7 / E2 | Layout check doesn't forbid `org/bukkit/`, `io/papermc/` | false | `bots-test-host/gradle.lockfile` has no Paper artifact on `runtimeClasspath`, and locking fails resolution on any unlocked module | reject |
| B7b | Vi6's layout check lacks the new NMS/duplicate checks | low | Vi6 jar is unchanged by this story; parity is a nicety with no failure path shown | reject |
| B8 | `check`/`out` now cost a dev-bundle setup in NekotineCore | false | `bots` already applies paperweight on the same bundle, so the setup already ran; the extra `noshadow` jar is negligible | reject |
| B9 | NekotineCore README inventory doesn't list `bots-test-host` | false | README is the `IPluginModule` inventory; the host adds no module (same call as Story 1.1 B8) | reject |
| B10 | CommandAPI fresh-machine note sits in the intro and isn't tracked | false | It is the deferred item this story was asked to pick up; the README makes no fresh-build promise beyond JDK 25 | reject |
| B11 | Hard-coded `26.2.build.65-beta` in AGENTS.md; descriptor `version: 0.0.1` vs `0.0.1-SNAPSHOT` | low | AGENTS wording was the requested deferred fix; the descriptor version is cosmetic for a test-only plugin | reject |
| B12 | Catalog JUnit 5.14.4-SNAPSHOT vs harness 5.13.4 | false | Separate suites with separate configurations; no class conflict, and the catalog entry predates this story | reject |
| B13 | Spec and sprint status not in the diff | false | They are committed with the Vi6 PR in the PR step | reject |
