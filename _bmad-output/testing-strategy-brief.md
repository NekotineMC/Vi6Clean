# Vi6Clean testing strategy — agreed brief (input for `bmad-spec`, new folder e.g. `_bmad-output/specs/spec-testing/`)

Full discussion and evidence: `testing-strategy-discussion.md` (session notes, not versioned).

## Goal
Replace "compile, deploy to the dev server, play by hand" with tests written as ordinary JUnit 5 classes. Those tests run against a real, throwaway Paper 26.2 server that Gradle boots from paperweight. This covers new Vi6Clean development and the upcoming NekotineBots library.

## Agreed approach
- **Two test suites.**
  - `test`: plain JUnit, no MockBukkit, for pure logic (e.g. every shipped `tools/*.yml` and `koths/*.yml` deserializes into its Configurate record).
  - `integrationTest`: a Gradle JvmTestSuite with a real server. It runs **only when asked for** (`gradlew integrationTest`), never as part of `build` or `check`.
- **Throwaway server.**
  - It runs the runnable Paper server jar from the paperweight dev bundle (`mappedServerJar.jar`, Main-Class `org.bukkit.craftbukkit.Main`), with libraries from the dev bundle's runtime configuration.
  - The server is booted once per test JVM by a JUnit 5 extension or LauncherInterceptor. Tests are isolated from each other by a game reset (bots, scoreboard, phase), not by restarting the server.
- **Test-server bundle supplied by the user.** The real world, maps, plugin jars (ProtocolLib, FAWE/WorldEdit and the rest, from Hangar) and configs.
  - The fixture copies the bundle into a fresh folder under `build/` and adds the freshly built Vi6 jar to `plugins/`. It never resolves plugin jars itself.
  - The bundle's location is one setting.
- **Access from tests.**
  - The Bukkit API works directly: `Bukkit.getWorlds()`, players including bots, scoreboards, events via listeners. NMS is reachable too.
  - Bukkit work runs through `onMain(...)`. Waiting uses `awaitTicks(n)` and `awaitUntil(cond, timeoutTicks)`.
  - Vi6, Core and NekotineBots types: the target design loads the test classes through a child of Vi6's plugin classloader (Quarkus-style, swapping the thread context classloader in a LauncherInterceptor). Tests then use `Ioc.resolve(...)`, `Vi6Game` and the rest, fully typed.
  - Fallback: a small, unshaded `vi6-testapi` interface module on the shared classpath, published through the Bukkit `ServicesManager`.
- **Relocation.** Drop the `fr.nekotine.core` relocation, so tests import Core normally; keep the CommandAPI relocation. Core is already a shaded library, not a plugin, so it stays that way.
- **Bots in tests.**
  - Assertions use scripted, deterministic bot inputs.
  - The autonomous bot behaviour is used only for full-game soak runs, which pass if the game reaches a result with no exception in the log.
  - NekotineBots tests itself the same way, with a tiny host plugin in its own repository.
- **Game hooks the tests need.** Force map, teams and phase; shorten timers through config; reset between tests.

## Constraints
- `AGENTS.md`: Vi6 classes must **not** be on the test JVM's classpath. Paper's plugin classloader asks the parent first, and the `JavaPlugin` constructor throws "JavaPlugin requires to be created by a valid classloader".
- 16 Core files and 5 Vi6 files import ProtocolLib or WorldEdit, so Core and Vi6 can't be moved to the shared classpath to get around this.

## Spike (deferred; the user will start it in a later session)
1. Does Paper boot inside Gradle's test worker?
2. Can the server stop without `System.exit` killing the worker or losing the reports?
3. Do ProtocolLib and FAWE load in that setup?
4. How long does a boot take?
5. Does loading test classes through the plugin classloader work?
6. With the Core relocation dropped, do two plugins that each ship Core stay isolated?

Fallback if 1 or 2 fail: Gradle starts the server as a child process, and the server runs the JUnit launcher itself, writing JUnit XML and an exit code.

## Decided
- Existing specs (`spec-bots` says "no JVM test suite" and "verification is RCON + log") get updated **after** the test framework exists, not now.
- `integrationTest` runs only when asked for.
- The real world is used; the user provides the bundle (maps, plugins, configs).

## Out of scope
- MockBukkit: it only reaches 26.1.2, and it can't model NMS, ProtocolLib, CommandAPI, dialogs or bots.
- Vanilla GameTest.
- Third-party frameworks (Paperwright, Plugwright, FancyInnovations).
- RCON-and-log checks as the main way of verifying results.
