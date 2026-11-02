---
title: 'Wire Vi6 to paper-junit-server'
type: 'feature'
created: '2026-11-01'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '5185308bcfe82317b10a58228b181072a4a3a35b'
context:
  - '{project-root}/_bmad-output/specs/spec-testing/SPEC.md'
  - '{project-root}/_bmad-output/specs/spec-testing/harness-design.md'
  - '{project-root}/paper-junit-server/README.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Vi6Clean has the paper-junit-server submodule but doesn't use it: it has no `test` or `integrationTest` suite. Its shaded jar also relocates NekotineCore, so tests can't import Core types.

**Approach:** Include the harness build, apply its Gradle plugin with Vi6Clean as the target, and switch the shadow relocation to the spike's verified layout. Add the plain `test` suite (CAP-1) and one Vi6-typed smoke test (CAP-2, CAP-4) that boots the real server from the user's bundle.

**Decision (bundle):** Vi6's build sets `paperJunitServer.bundle` to `test-bundle/` at the repository root. Developers put the server configs, plugin jars (ProtocolLib, FAWE), plugin config folders and `worlds/` in that folder and keep it in line with production. Its contents are gitignored except a `README.md` that lists what goes in. paper-junit-server has no default bundle, and a missing bundle fails loudly.

## Boundaries & Constraints

**Always:** Lock only the new configurations. Every existing line in `gradle.lockfile` and `settings-gradle.lockfile` stays as it is, and the dev bundle stays on `26.2.build.65-beta`. `gradlew build` and `check` don't run `integrationTest`. Run named tasks only (`spotlessApply`, `build`, `integrationTest`, `dependencies`). Revert the formatting drift Spotless introduces in `FlagBasedStatusEffectType.java` and `HotPotatoHandler.java`.

**Never:** Running bare `gradlew` or the `out` task, which restarts the dev server. Editing `paper-junit-server/` or `NekotineCore/`. Writing to the bundle folder. A default bundle location in the build. Game hooks (story 3). Pushing.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Plain build | no bundle setting | `gradlew build` green, as fast as today; `test` runs (or NO-SOURCE) | — |
| Integration run | bundle set | one boot; smoke test green; per-test XML report; clean stop | — |
| Bundle missing | `test-bundle/` absent | `integrationTest` fails at staging | message shows the path |
| Bundle not filled | `test-bundle/` holds only its README | boot fails: ProtocolLib and WorldEdit are missing, so Vi6Clean is not loaded | harness names the target plugin as not loaded |
| Stale Vi6 jar in bundle | bundle `plugins/Vi6Clean-0.0.1.jar` | skipped; only the fresh shadow jar is staged | — |

</frozen-after-approval>

## Code Map

- `settings.gradle.kts` -- holds only `includeBuild("NekotineCore")`. A `pluginManagement { includeBuild("paper-junit-server") }` block must come first, plus a second `includeBuild("paper-junit-server")` (see the harness README).
- `build.gradle.kts` -- `tasks.withType<ShadowJar>` relocates `fr.nekotine.core` → `fr.nekotine.vi6clean.nekotinecore` and CommandAPI below it. Spike Q6 layout: drop the Core relocation, use `relocate("dev.jorel.commandapi", "fr.nekotine.core.commandapi")`. Core's own shadowJar already uses that package and its outgoing variants exclude CommandAPI. `lockAllConfigurations()` is on.
- `src/main/resources/paper-plugin.yml` -- `name: Vi6Clean`, which is the `targetPlugin` value. The shadow jar is `build/libs/Vi6Clean-0.0.1.jar`.
- `fr.nekotine.vi6clean.game.Vi6Game` via `Ioc.resolve(Vi6Game.class)`; `getPhaseMachine()` is non-null after enable (spike `InnerVi6Test`).
- `spike/test-framework:src/itPlugin/.../InnerVi6Test.java` -- shape of the smoke test. Use `PaperServer.onMain` / `PaperServer.plugin()` instead of the raw scheduler.
- Lock state: `--write-locks` on a full build re-resolves `26.2.build.+` and `11.2.+`, which moves the dev bundle and CommandAPI. Instead, resolve only the new configurations, e.g. `gradlew dependencies --configuration <name> --write-locks` for `integrationTestCompileClasspath`, `integrationTestRuntimeClasspath`, `paperJunitServerAgent`, `testCompileClasspath`, `testRuntimeClasspath`. Then diff the lockfile.
- Dev server `D:/shared/MinecraftServer/Vi6Clean_latest` (read-only source for filling the bundle): `server.properties` (flat, `level-name=world`), `bukkit.yml`, `spigot.yml`, `commands.yml`, `permissions.yml`, `help.yml`, `eula.txt`, `config/`, `worlds/` (188 MB), `plugins/ProtocolLib.jar`, `plugins/FastAsyncWorldEdit-Paper-2.15.3.jar`, `plugins/ProtocolLib/`, `plugins/FastAsyncWorldEdit/`, `plugins/Vi6Clean/` (config, koths, tools, Maps). The spike staged exactly this set. Don't copy the other plugins (Citizens, SkinsRestorer, voicechat, which binds a fixed UDP port, spark, …) or `plugins/update/`.
- `AGENTS.md` -- lists only NekotineCore as a submodule and says "No tests exist". Deferred from story 1 to this story.
- Known noise: Vi6 `onDisable` NPE at stop, because staging excludes `plugins/update/`. It's harmless and already tracked in `deferred-work.md`.

## Tasks & Acceptance

**Execution:**
- [x] `settings.gradle.kts` -- include `paper-junit-server` through `pluginManagement` and as a regular build, keeping NekotineCore.
- [x] `build.gradle.kts` -- apply `fr.xxgoldenbluexx.paper-junit-server`; `paperJunitServer { targetPlugin = "Vi6Clean"; pluginJar = tasks.shadowJar.flatMap { it.archiveFile }; bundle = layout.projectDirectory.dir("test-bundle") }`; change the relocation as in the Code Map. Register the `test` suite with JUnit Jupiter 5.13.4, with no tests now (CAP-1).
- [x] `.gitignore`, `test-bundle/README.md` -- ignore `test-bundle/*` except `README.md`. The README lists the expected contents (the Code Map set), says jar versions must match `gradle/libs.versions.toml`, and says never to copy `plugins/update/` or the dev server's other plugins.
- [x] `test-bundle/` (local, uncommitted) -- fill it from the dev server's Code Map set, copying only, for verification.
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6SmokeIT.java` -- resolves `Vi6Game` on the main thread and asserts the phase machine is non-null. Asserts that the test class's loader parent is Vi6Clean's plugin classloader.
- [x] `gradle.lockfile` -- add lock state for the new configurations only, as described in the Code Map.
- [x] `AGENTS.md` -- add `paper-junit-server/` as a second submodule (read its AGENTS.md first; commit the gitlink with any change). Document `test-bundle/` (what goes in, gitignored), `gradlew integrationTest` as separate from `build`, `src/test` as the plain suite, and the new relocation.

**Acceptance Criteria:**
- Given the change, when `gradlew spotlessApply build` runs, then it succeeds without staging or booting a server.
- Given `git diff gradle.lockfile`, when inspected, then no existing coordinate changes version, and only new configuration names are added.
- Given a filled `test-bundle/`, when `gradlew integrationTest` runs, then `Vi6SmokeIT` passes, the report lists it as its own class, and the log shows Vi6Clean disabled and worlds saved.
- Given the shadow jar, when listed, then Core classes sit under `fr/nekotine/core/` and CommandAPI under `fr/nekotine/core/commandapi/`, with nothing left under `fr/nekotine/vi6clean/nekotinecore/`.

## Implementation Notes

- Lock state: `--write-locks` on `testCompileClasspath`/`testRuntimeClasspath` re-resolved `26.2.build.+` (dev bundle and `paperweightDevelopmentBundle` moved to `135-stable`, plus lz4, leafpile, velocity-native, spark, fastutil), so it was reverted. Locks were written instead with `gradlew dependencies --configuration <name> --update-locks 'org.junit:*,org.junit.jupiter:*,org.junit.platform:*,org.opentest4j:*,org.apiguardian:*'` for the five new or extended configurations. Diff: JUnit/opentest4j/apiguardian lines added, and `paperJunitServerAgent` added to the `empty=` line (the agent is an included-build substitution, so it has no locked coordinates; neither does the harness). No existing coordinate changed version.
- Spotless drift in `FlagBasedStatusEffectType.java` and `HotPotatoHandler.java` is reverted after each `spotlessApply`. Consequence: a standalone `gradlew build` fails `spotlessJavaCheck` on those two files (pre-existing drift); `gradlew spotlessApply build` is green.
- Bundle versions: the dev server's ProtocolLib is `5.5.0-SNAPSHOT-2d3b3e3` while the build compiles against `5.4.0`; verification booted with it. The tests boot Paper `26.2-65` (locked dev bundle) while the dev server runs build 134. Aligning the bundle jars is the developer's job (see `test-bundle/README.md`).
- Verification copied the dev server's stale `plugins/Vi6Clean-0.0.1.jar` into the local bundle on purpose: staging skipped it (staged jar md5 equals `build/libs/Vi6Clean-0.0.1.jar`).
- Core bug found: `PhaseMachine.goTo` never sets `running = true` on the first transition (the `currentPhase == null` branch), so `isRunning()` is false after a successful `Vi6Game.start()`. `Vi6SmokeIT` asserts `getPhase(Vi6PhaseLobby.class)` is non-null instead. Fixing it belongs in NekotineCore.
- `check` now depends on `verifyShadowJarLayout`, which fails on `fr/nekotine/vi6clean/nekotinecore/` or `dev/jorel/commandapi/` entries, or when `fr/nekotine/core/commandapi/` is empty (checked against the old relocation: fails).
- `NekotineCore/AGENTS.md` line 18 ("relocates `fr.nekotine.core` to `fr.nekotine.vi6clean.nekotinecore`") is now stale; editing `NekotineCore/` is out of scope here.

## Spec Change Log

## Review Triage Log

| # | Layer | Finding | Verdict | Evidence | Route |
|---|---|---|---|---|---|
| 1 | verif, blind | Relocation layout unchecked by `build`/`check` | medium | `test` has no sources and `integrationTest` is outside `check`; spike Q6 shows a wrong CommandAPI relocation throws `NoClassDefFoundError` in `onLoad` | patch |
| 2 | edge | Smoke test can't fail once `resolve` succeeds | medium | `Vi6Game.phaseMachine` is a field initializer; `onEnable` catches and logs, so a half-failed enable still passes | patch |
| 3 | blind | Assert Core's `Ioc` comes from the plugin loader | low | Proves the unrelocated Core is shaded; one assertion, fixed with #2 | patch |
| 4 | blind, edge, verif-other | AGENTS.md presents a `src/test` that doesn't exist | low | `src` holds only `main` and `integrationTest`; direct rewording | patch |
| 5 | edge | AGENTS.md says the run fails if Vi6Clean doesn't load | low | Enable errors are swallowed; only not-loaded or disabled fail; rewording | patch |
| 6 | blind | Bundle README is silent on the Paper version and shutdown NPE noise | low | Tests boot the locked dev bundle (build 65), not the bundle's Paper; NPE tracked; two README lines | patch |
| 7 | blind | Plain `gradlew build` fails `spotlessJavaCheck` | low | The two drifted files are identical to the baseline, so this already fails on the base commit | defer |
| 8 | blind, verif-other | `NekotineCore/AGENTS.md:18` now stale | low | Real; Core edits are excluded by intent and it's an agent-context file | defer |
| 9 | blind | No deploy note for the production jar layout change | low | The AGENTS.md intro now states the layout; spike Q6 isolation is in SPEC | reject |
| 10 | blind | JUnit version set in two places | low | Drift needs a JUnit major bump; the fix adds catalog wiring | reject |
| 11 | blind, edge | README unclear on `world-container` | false | The bundle's `bukkit.yml` sets `world-container: worlds`, and the README says to copy `bukkit.yml` | reject |
| 12 | blind | AGENTS.md lock guidance incomplete | low | `settings-gradle.lockfile` is untouched; wording only | reject |
| 13 | edge | Lockfile `empty=` line modified | false | Gradle records new empty configurations on that line; no coordinate version moved, which is the intent | reject |

## Design Notes

A plain JUnit class added to `src/test` must run under `build`, but no test is committed now. To prove the wiring, add a throwaway test, run `gradlew test`, and delete it. A committed test is left for real pure logic such as database access. The dev server's ProtocolLib is 5.5.0-SNAPSHOT while the build compiles against 5.4.0. Verification may boot with it, but record the mismatch in the Implementation Notes; aligning the jars is the developer's job, per the bundle README. The relocation change also alters the production jar the next time it's deployed. Spike Q6 showed that a paper plugin's Core is isolated from other plugins.

## Verification

**Commands:**
- `gradlew.bat spotlessApply build` -- expected: BUILD SUCCESSFUL, no `integrationTest` task executed
- `gradlew.bat integrationTest` -- expected: BUILD SUCCESSFUL, `Vi6SmokeIT` passed, `build/test-results/integrationTest/*.xml` present
- `git diff gradle.lockfile settings-gradle.lockfile` -- expected: additions only
- `jar tf build/libs/Vi6Clean-0.0.1.jar | grep -c nekotinecore` -- expected: 0
