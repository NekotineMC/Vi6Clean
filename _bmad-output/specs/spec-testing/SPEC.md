---
id: SPEC-testing
companions:
  - harness-design.md
sources:
  - ../../testing-strategy-brief.md
---

> **Canonical contract.** This SPEC and the files in `companions:` are the complete, preservation-validated contract for what to build, test, and validate. Source documents listed in frontmatter are for traceability — consult them only if you need narrative rationale or prose color this contract intentionally omits.

# Vi6Clean test framework

## Why

Pain to solve: every Vi6Clean change is verified by compiling, deploying to the dev server and playing by hand. No tests exist, no CI builds Java, and the PR reviewer is the only gate. Upcoming work, NekotineBots and the bot-driven game, needs repeatable checks of phases, tools and win conditions. The spike (2026-10-29) proved that a real Paper 26.2 server can run inside Gradle's test worker, so tests can be ordinary JUnit 5 classes against the real game instead of mocks.

## Capabilities

- **CAP-1 — Pure-logic suite**
  - **intent:** A developer can test logic that doesn't touch the Bukkit API (for example, later, database access) as plain JUnit.
  - **success:** A plain JUnit class added under `src/test` runs in `gradlew test` and `gradlew build` with no server and no MockBukkit. No specific check is required now: tool and KoTH configs use the Bukkit API, so they belong to the integration suite.

- **CAP-2 — On-demand integration suite**
  - **intent:** A developer can run tests against a real Paper 26.2 server booted from their bundle, only when they ask for it.
  - **success:** `gradlew integrationTest` stages the bundle plus the freshly built Vi6 jar, boots one server for the test JVM, runs every integration test against it, and produces per-test JUnit reports. `gradlew build` and `gradlew check` never run it.

- **CAP-3 — Server access from tests**
  - **intent:** A test can use the Bukkit API and NMS directly, run work on the main thread, and wait in ticks.
  - **success:** A test reads worlds, players (bots included), scoreboards and plugin state, listens to events, calls NMS, and uses `onMain(...)`, `awaitTicks(n)` and `awaitUntil(cond, timeoutTicks)`. A wait that times out fails the test instead of hanging.

- **CAP-4 — Typed game access**
  - **intent:** A test can use Vi6, Core and NekotineBots types directly (`Ioc.resolve(...)`, `Vi6Game`, …), fully typed and with no reflection.
  - **success:** A test class compiled against Vi6 resolves `Vi6Game` from the running plugin and asserts on it. Gradle discovers and reports it as its own test class, and its classloader's parent is Vi6's plugin classloader.

- **CAP-5 — Clean stop**
  - **intent:** The server stops gracefully at the end of the run without taking the test worker down.
  - **success:** After the last test, plugins are disabled and worlds saved, the Gradle task finishes green with every result reported, and a run whose exit bypass did not take effect fails with a message naming the cause.

- **CAP-6 — Isolation by game reset**
  - **intent:** Each test starts from a known game state without restarting the server.
  - **success:** A test can force the map, the teams and the phase. Timers can be shortened through config. Between tests the bots, scoreboard and phase are reset, so two tests that each leave a game mid-phase pass in either order.

- **CAP-7 — Bot-driven tests**
  - **intent:** Assertions can drive bots with scripted, deterministic inputs, and full-game soak runs can use autonomous bots.
  - **success:** A scripted-bot test of a tool or phase gives the same result on 3 consecutive runs. A soak test passes when the game reaches a result with no exception in the log.

- **CAP-8 — Shared harness**
  - **intent:** Any Paper plugin project, NekotineBots first (with a tiny host plugin in its own repository), tests itself with the same harness, taken from paper-junit-server.
  - **success:** Vi6 and NekotineBots each run integration tests through paper-junit-server, pulled in as a git submodule. Neither contains a copy of the fixture, session listener, no-exit agent or shared Gradle wiring.

## Constraints

- Vi6, Core, ProtocolLib and WorldEdit classes are never on the test JVM classpath. Paper's plugin classloader asks its parent first, and `JavaPlugin` throws "requires to be created by a valid classloader". Core and Vi6 can't move to the shared classpath either: 16 Core files and 5 Vi6 files import ProtocolLib or WorldEdit.
- One server boot per test JVM (~11.5 s measured). Tests are isolated by game reset, never by a server restart.
- The bundle is a folder whose path each consumer project sets in its build (Vi6: `test-bundle/` at the repository root, contents gitignored). Developers put the configs, plugin jars and worlds inside it and keep it in line with the production server. paper-junit-server ships no default bundle: if the bundle is missing or not a directory, `integrationTest` fails with a loud message. The harness copies the bundle under `build/` and adds the plugin jar. It never resolves plugin jars itself and never writes to the bundle folder.
- The `fr.nekotine.core` relocation is dropped so tests import Core normally. CommandAPI is relocated to `fr.nekotine.core.commandapi`, matching Core's own shading. Core stays a shaded library, not a plugin.
- The harness, **paper-junit-server** (fixture, session listener, no-exit agent, shared Gradle wiring), is its own standalone library for any Paper plugin, not branded as Nekotine. Its Maven group is `fr.xxgoldenbluexx` and its Java package `fr.xxgoldenbluexx.paperjunitserver`. Consumers pull it in like NekotineCore: a git submodule plus `includeBuild`. It may use paperweight directly. It is not part of NekotineCore, which has no paperweight. Vi6 keeps only its own game hooks and tests.
- The harness is test-only: consumers take it on test configurations only, and it never enters a plugin's shaded jar.
- Harness changes go through the harness repository's own PR. A consumer's PR commits the updated submodule pin, because `includeBuild` compiles the submodule's working tree.
- Dropping the relocation leaves a line in `NekotineCore/AGENTS.md` stale ("Vi6 relocates `fr.nekotine.core`"), and that line needs a refresh.
- Every plugin that uses Core ships its own copy. A legacy `plugin.yml` plugin without Core borrows another plugin's Core and writes into its `Ioc`.
- Paper calls `System.exit` unconditionally at the end of shutdown, and Java 25 has no way to veto it. The harness must neutralize it (→ `harness-design.md`) and must fail loudly if that stops working after a Paper or Gradle upgrade.
- Lockfile changes are limited to the new configurations. Writing locks must not move existing dependencies; the spike's `--write-locks` bumped the dev bundle and downgraded CommandAPI.
- The server jar is taken from paperweight's output through a task dependency, never from a hard-coded cache path. Each run gets a free port, so concurrent runs (two worktrees) don't collide.
- The bundle's plugin versions are pinned to match the build's. In the spike they didn't: the bundle has ProtocolLib 5.5.0-SNAPSHOT while Vi6 compiles against 5.4.0, and the dev server runs Paper build 134 while the tests boot build 135.
- Staging re-copies the world on every run but not between tests, so CAP-6's reset must not rely on world state.

## Non-goals

- MockBukkit: it only reaches 26.1.2 and can't model NMS, ProtocolLib, CommandAPI, dialogs or bots.
- Vanilla GameTest.
- Third-party frameworks: Paperwright, Plugwright, FancyInnovations.
- RCON-and-log checks as the main way to verify, and a child-process server mode for now. The spike made it unnecessary; it is kept as plan B (→ `harness-design.md`).
- CI: no CI builds Java, and the integration suite runs on a developer machine that has the bundle.
- Distributing paper-junit-server as a published artifact or Gradle plugin. For now it is consumed only as a git submodule plus `includeBuild`.
- Editing `spec-bots` now. Its "no JVM test suite" and "verification is RCON + log" lines are updated after this framework exists.

## Success signal

- On a developer machine with the bundle, `gradlew integrationTest` boots the server once. A Vi6-typed test forces a map, teams and phase, drives scripted bots and asserts the outcome. The run ends green in about a minute with a per-test report, and `gradlew build` stays as fast as today.

## Assumptions

- No CI builds Java (AGENTS.md), so the integration suite runs only locally and is invoked by hand.
- Scripted bot inputs come from NekotineBots' library API (`spec-bots` CAP-7), so CAP-7 here waits on that library.
- The user creates the paper-junit-server repository; this spec doesn't cover creating it.
