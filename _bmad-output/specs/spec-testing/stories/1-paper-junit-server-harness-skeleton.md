---
title: 'paper-junit-server harness skeleton'
type: 'feature'
created: '2026-11-01'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '46e9561516f89ea268fe4151532d9fce73e35d13'
context:
  - '{project-root}/_bmad-output/specs/spec-testing/SPEC.md'
  - '{project-root}/_bmad-output/specs/spec-testing/harness-design.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The in-process Paper test mechanism exists only as throwaway, Vi6-hard-coded spike code on `spike/test-framework`. Neither Vi6 nor NekotineBots can reuse it.

**Approach:** Create paper-junit-server as a standalone Gradle build at `Vi6Clean/paper-junit-server`, registered as a git submodule of Vi6Clean. It has three projects: the fixture library, the no-exit agent and a Gradle plugin that wires bundle staging, the `integrationTest` suite and the agent into any consumer. All of it is generalized from the spike, and the target plugin is a setting.

## Boundaries & Constraints

**Always:** Maven group `fr.xxgoldenbluexx`, package `fr.xxgoldenbluexx.paperjunitserver`. Gradle 9.4.1 wrapper, Java 25 toolchain for library and agent, Spotless config identical to Vi6's, dependency locking on with a committed lockfile. Consumer-facing failures (missing bundle setting, agent not attached or not patching, server thread dead or still alive) throw with a message naming the cause and the setting to fix. The submodule URL is `https://github.com/XxGoldenbluexX/paper-junit-server.git`.

**Never:** No Vi6, Nekotine or bundle-path literals in harness code. No hard-coded paperweight cache paths. No consumer changes beyond `.gitmodules` and the gitlink: `includeBuild`, suites and the relocation belong to story 2. No push, no GitHub repo creation. No running bare `gradlew` in Vi6Clean.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Bundle unset | `paperJunitServer.bundle` property absent | `integrationTest` fails at staging; `build` unaffected | Message names `paperJunitServer.bundle` |
| Bundle missing on disk | property points to a non-directory | staging fails | Message shows the path |
| Agent not attached | `shutdown=agent`, no `-javaagent` | boot fails before any test runs | Names the agent and the `shutdown=hook` fallback |
| Agent attached, nothing patched | Paper renamed `onServerExit` | boot fails after first tick | Names `DedicatedServer.onServerExit` |
| Hook fallback | `shutdown=hook` | no agent arg; shutdown-hook hold | — |

</frozen-after-approval>

## Code Map

- `spike/test-framework:src/integrationTest/java/fr/nekotine/vi6clean/it/PaperServerExtension.java` -- boot (daemon thread, first-tick wait), `onMain`/`awaitTicks`/`awaitUntil`, agent and hook stop modes. Reuse the logic; drop the timing/`spike-shutdown.txt` output and the `"Vi6Clean"` literal.
- `spike/test-framework:.../it/InPluginSessionListener.java` -- TCCL swap to a `URLClassLoader` child of the target plugin's loader. Generalize the property names.
- `spike/test-framework:src/itAgent/.../NoExitAgent.java` -- `java.lang.classfile` transform of `DedicatedServer.onServerExit`. Add a patched flag and catch transformer errors.
- `spike/test-framework:build.gradle.kts` (from the `SPIKE` banner) -- staging `Sync`, suite, JVM props, agent arg. Move it into the plugin.
- Paperweight userdev 2.0.0-beta.21 configurations `mojangMappedServer` (mapped server jar) and `mojangMappedServerRuntime` (its libraries) replace the spike's `.gradle/caches/.../mappedServerJar.jar` path.
- Vi6 `gradle/wrapper/`, `build.gradle.kts` Spotless block, `.gitmodules` -- copy or extend.

## Tasks & Acceptance

**Execution:**
- [x] `paper-junit-server/settings.gradle.kts`, `build.gradle.kts`, `gradle/`, `gradlew*`, `.gitignore`, `README.md`, `AGENTS.md` -- root build including `paper-junit-server` (dir `harness/`), `paper-junit-server-agent` (dir `agent/`), `paper-junit-server-gradle-plugin` (dir `gradle-plugin/`). Project names equal artifact names so `includeBuild` substitution works.
- [x] `harness/.../PaperServer.java` -- static fixture: `start()` (free port via `--port`, `Main.main` by reflection on a daemon thread, wait for tick > 0 or launcher death, agent self-check), `stop()` (agent mode: `Bukkit.shutdown()` then join `Server thread`; hook mode: spike hold), `onMain`, `awaitTicks`, `awaitUntil`, `plugin()`. Compiles against paper-api (compileOnly) and JUnit (compileOnly).
- [x] `harness/.../PaperServerSessionListener.java` + `META-INF/services/org.junit.platform.launcher.LauncherSessionListener` -- acts only when `paperjunitserver.targetPlugin` is set. Boots, swaps the TCCL to a child of that plugin's loader over `paperjunitserver.testClasses` (path list), stops on session close.
- [x] `agent/.../NoExitAgent.java` -- `Premain-Class`; sets `paperjunitserver.agent.loaded`, then `.patched` after removing the call; stores any transform error in `.error`.
- [x] `gradle-plugin/.../PaperJunitServerPlugin.java` + extension -- plugin id `fr.xxgoldenbluexx.paper-junit-server`, Java `release 17`. Requires paperweight userdev. Extension: `targetPlugin`, `pluginJar`, `bundle` (default from the Gradle property `paperJunitServer.bundle`), `shutdown` (`agent`|`hook`, default `agent`), `junitVersion` (5.13.4), `bundleExcludes`, `maxHeapSize` (3g). Registers the `preparePaperTestServer` Sync into `build/it-server/` (copies the bundle minus logs, `session.lock`, cache/libraries/versions, `plugins/update/` and root jars; adds `pluginJar` to `plugins/`; rewrites `enable-rcon=false`, `enable-query=false`) and an `integrationTest` JvmTestSuite that `check` doesn't depend on. Suite sources compile against main, but the test runtime classpath holds only the server configurations, the harness, the agent and JUnit; the suite output goes in `paperjunitserver.testClasses`. Sets workingDir, the JVM props from harness-design.md, and the agent arg in agent mode.
- [x] `Vi6Clean/.gitmodules` + gitlink -- `git submodule add` the local repo at `paper-junit-server` with the GitHub URL, on Vi6 branch `feat/test-harness`.

**Acceptance Criteria:**
- Given the new repo, when `paper-junit-server\gradlew.bat spotlessApply build` runs, then all three projects compile and Spotless passes.
- Given the harness and agent sources, when grepped for `Vi6`, `nekotine` or `D:/`, then there are no matches.
- Given Vi6Clean, when `git submodule status` runs, then `paper-junit-server` is listed next to `NekotineCore`, and `settings.gradle.kts` is unchanged.

## Implementation Notes

- Harness repo commit `aa58e750139779ba6625cc6a67483ac2dda47347` on its local `main`. Vi6: `.gitmodules` and the gitlink are staged on `feat/test-harness`, and the submodule git dir has been absorbed into `.git/modules/paper-junit-server` like NekotineCore's.
- Root project is named `paper-junit-server-build` so it doesn't clash with the harness project during `includeBuild` substitution. The Spotless java step is applied only to projects that have the `java` plugin.
- Additions beyond the tasks: `paperjunitserver.bootTimeoutSeconds` (default 300), and `stop()` fails if the server thread has already died.
- Matrix audit: no automated tests, because the user chose compile-only verification for this story. The implementer covered the rows by hand in a throwaway consumer: bundle unset, bundle missing, agent not attached, agent mode, and hook mode all behaved as the matrix says. "Agent attached, nothing patched" can't be reproduced without changing Paper; the check is in place.
- Story 2 has to confirm `mojangMappedServer` resolution inside Vi6, and lock the new `integrationTest*` and `paperJunitServerAgent` configurations.
- Namespace renamed at the user's request (2026-11-01), after the review diff was staged: package, Maven group and plugin id moved from `io.github.xxgoldenbluexx` to `fr.xxgoldenbluexx`, and the frozen block was updated to match. The rename is uncommitted in the harness repo; the clean rebuild passes.

## Spec Change Log

## Review Triage Log

| # | Layer | Finding | Verdict | Evidence | Route |
|---|---|---|---|---|---|
| 1 | blind, edge, verif-other | Bundle `plugins/` keeps the old target-plugin jar beside `pluginJar` | high | `DEFAULT_BUNDLE_EXCLUDES` only drops root `*.jar`; the dev-server bundle holds a Vi6Clean jar (the spike had to include plugin jars explicitly) | patch |
| 2 | blind, edge | `awaitTicks`/`awaitUntil` on the main thread hang | medium | `onMain` runs inline on the primary thread, then `Thread.sleep` blocks ticking, so the deadline never comes | patch |
| 3 | blind, edge | `-Xmx` provider argument vs the Test task's `maxHeapSize` | medium | Test workers default to 512m; two `-Xmx` flags leave the effective heap order-dependent | patch |
| 4 | edge | Target plugin disabled after boot leaves every test failing on unrelated-looking errors | medium | `plugin()` checks only that the plugin is loaded, not enabled | patch |
| 5 | edge | A throw after `start()` in `launcherSessionOpened` never stops the server | low | JUnit doesn't call `launcherSessionClosed` after a failed open; fixed together with #4 | patch |
| 6 | edge | Test loader closed before `stop()` | low | `onDisable` callbacks touching unloaded test classes would get `NoClassDefFoundError`; the fix is a reorder | patch |
| 7 | edge | Root build comment names Vi6Clean | low | Breaks the repo's own no-consumer-names policy; the fix is a rewording | patch |
| 8 | blind, edge | Failed boot (timeout or agent unpatched) leaves the server unstopped | maybe-false | Worker exit runs Paper's shutdown hook; whether an unpatched `System.exit` inside that hook deadlocks the worker is unverified | defer |
| 9 | edge | `stop()` throws without `Bukkit.shutdown()` when `serverThread` is null | low | Only if Paper renames "Server thread"; rare, and the fix adds a branch | reject |
| 10 | blind | Agent only patches `onServerExit`; watchdog or restart exits still kill the JVM | low | Spec'd design; rare outside a hung main thread | reject |
| 11 | blind | TCCL swap relies on Gradle internals with no guard | false | Test classes are off the classpath, so a Gradle change fails loudly with `ClassNotFoundException`; a silent wrong loader is impossible | reject |
| 12 | blind | Wrapper and lockfiles missing | false | Present in commit `aa58e75`; the review diff excluded them on purpose | reject |
| 13 | blind | No consumer wiring or smoke test | false | Intent: compile-only, first boot in story 2 | reject |
| 14 | blind | Vi6 root AGENTS.md doesn't mention the new submodule | low | Real, but the fix edits an agent-context file | defer |
| 15 | blind | Versions pinned and duplicated (JUnit, paper-api) | low | Drift only bites on a JUnit major change; the fix adds a catalog | reject |
| 16 | blind, edge, verif-other | Thread found by name; free-port probe race | low | Spike-verified name; the port race is negligible on a dev machine; a retry adds complexity | reject |
| 17 | blind | `@Optional` vs throw, eager workingDir, providers throwing during IDE sync | low | Cosmetic, or not reachable unless the task is scheduled | reject |
| 18 | edge | `awaitTicks` overflow on extreme `n` | low | Not everyday; the fix adds a guard | reject |
| 19 | edge | `onMain` after the plugin is disabled gives a raw `IllegalPluginAccessException` | low | Rare; already a loud failure | reject |
| 20 | edge | `maxParallelForks > 1` boots several servers in one dir | low | Consumer misconfiguration, not a default | reject |
| 21 | edge | `enable-rcon = true` with spaces is not rewritten | low | Paper writes `key=value` without spaces | reject |
| 22 | edge | Version property missing or unexpanded | low | The file is always produced by `processResources` expand | reject |
| 23 | edge | Paperweight without the `mojangMapped*` configurations gives a generic error | low | Still fails loudly; one paperweight version is in use | reject |
| 24 | edge | Thread dead or alive messages name no setting | low | No setting fixes those causes; the message names the cause | reject |
| 25 | verif | Staging rewrite and excludes unchecked | gap | No test covers them; a story-2 smoke test would miss a regression | defer |
| 26 | verif | `hook` shutdown fallback never run by a test | gap | No test sets `hook` | defer |
| 27 | verif | Agent fail-on-upgrade guard unexercised | gap | No transformer unit test for the missing or present `System.exit` cases | defer |

## Design Notes

The listener is the only entry point; there's no JUnit extension, because every consumer test runs through the target plugin's loader (CAP-4). Run the agent self-check right after the first tick, since `DedicatedServer` is loaded by then: an unpatched exit would otherwise kill the worker after the tests and lose the reports. The consumer settings in story 2 include the build twice, `pluginManagement { includeBuild(...) }` for the plugin and `includeBuild(...)` for library substitution. Confirm in story 2 that `mojangMappedServer` resolves to the mapped jar with its task dependency.

## Verification

**Commands:**
- `cd paper-junit-server && ./gradlew.bat spotlessApply build` -- expected: BUILD SUCCESSFUL
- `git -C paper-junit-server log --oneline` -- expected: initial commit(s) present
- `git submodule status` (Vi6Clean) -- expected: NekotineCore and paper-junit-server listed
