# Test-framework spike — findings

Spike for `_bmad-output/testing-strategy-brief.md`, run 2026-10-29 on branch `spike/test-framework` (throwaway code, not for merge).
Environment: Windows 10, Temurin 25.0.2, Gradle 9.4.1, Paper dev bundle `26.2.build.135-stable` (server reports `26.2-135-e4d6dfd`), JUnit 5.13.4.
Bundle: a filtered copy of the dev server `D:\shared\MinecraftServer\Vi6Clean_latest` (configs, `worlds/`, ProtocolLib 5.5.0-SNAPSHOT, FastAsyncWorldEdit 2.15.3, Vi6Clean configs and maps), staged by a Gradle `Sync` task into `build/it-server/`. The original folder was only read.

## Decision: **GO** with the in-process design

The child-process fallback is not needed. All six questions came out positive; the caveats below change details of the design, not its direction.

| # | Question | Answer |
|---|----------|--------|
| 1 | Does Paper boot inside Gradle's test worker? | **Yes** |
| 2 | Can it stop without `System.exit` killing the worker? | **Yes, with a 1-method Java agent** (without it: no) |
| 3 | Do ProtocolLib and FAWE load? | **Yes** |
| 4 | How long does a boot take? | **~11.5 s** to first tick; ~0.9 s to stop |
| 5 | Does loading test classes through the plugin classloader work? | **Yes, including Gradle-native discovery and reports** |
| 6 | With Core unrelocated, do two plugins that each ship Core stay isolated? | **Yes**, with one caveat about legacy plugins that do *not* ship Core |

## Answers

### 1. Paper boots in the test worker — yes

- `integrationTest` (JvmTestSuite, JUnit Jupiter) runtime classpath = `mappedServerJar.jar` + paperweight's `mojangMappedServerRuntime` configuration. Nothing else: no Vi6, Core, ProtocolLib or WorldEdit.
- A JUnit extension calls `org.bukkit.craftbukkit.Main.main({"--nogui"})` on a daemon thread and polls until `Bukkit.getCurrentTick() > 0`. The test task's `workingDir` is the staged server folder, so every relative path Paper uses (`server.properties`, `plugins/`, `worlds/`) resolves there.
- Properties needed: `terminal.jline=false`, `com.mojang.eula.agree=true`. The staged `server.properties` gets `enable-rcon=false` and a separate port (25599) so it never clashes with a running dev server.
- From tests: `Bukkit.getWorlds()` (4 worlds), the plugin manager, the scheduler, and NMS (`MinecraftServer.getServer().getTickCount()`) all work. `onMain` (`callSyncMethod`), `awaitTicks` and `awaitUntil` work as sketched in the brief.
- Confirmed the constraint from the brief: `Class.forName("fr.nekotine.vi6clean.Vi6Main")` and `fr.nekotine.core.ioc.Ioc` both throw `ClassNotFoundException` on the test classpath, while Vi6 loads normally as a plugin.

### 2. Stopping without killing the worker — yes, via agent

- `DedicatedServer.onServerExit` calls `System.exit` unconditionally after a normal stop. A bytecode scan of the server jar found it, plus the exits in `RestartCommand`, `ServerWatchdog` and the bootstrap `Main` that don't run in this path. With a plain `Bukkit.shutdown()`, the worker died during JUnit's store cleanup and Gradle failed the task with `Could not write '/127.0.0.1:…'`. The XML for the 7 tests that had already finished was still written, but the build was red.
- Java 25 has no SecurityManager to block it. **Fix used:** a ~40-line `-javaagent` (`java.lang.classfile`) that removes the `System.exit` call from `DedicatedServer.onServerExit` only. With it, `Bukkit.shutdown()` completes, the server thread ends in 834–1024 ms, worlds save, `onDisable` runs, the worker keeps running, and reports are complete. BUILD SUCCESSFUL.
- Alternative tried: never calling `Bukkit.shutdown()` and letting Gradle's worker exit the JVM. That also produces a green build with complete reports, but no stop sequence shows in the output and `onDisable` isn't observable. Use the agent.

#### Other bypasses (researched 2026-10-29)

The exit is unconditional: the bytecode is `System.exit(abnormalExit ? 70 : 0)` at the end of `onServerExit`, called from `MinecraftServer.stopServer()`, which every stop path goes through. No Paper flag or system property skips it.

| Option | Status | Verdict |
|---|---|---|
| SecurityManager `checkExit` | Removed in JDK 24 ([JEP 486](https://openjdk.org/jeps/486)); system-rules / system-lambda / itsallcode `ExitGuard` all depend on it | Dead |
| Supported JDK API to veto exit | None. JEP 486 prototyped interception mechanisms and rejected them, and points to agent rewriting instead | Doesn't exist |
| **Java agent rewriting the call site** (this spike, JEP 486 appendix, [Dodona `ExitAgent`](https://github.com/dodona-edu/judge-java/pull/76)) | Verified, ~20 s builds | **Primary choice** |
| Agent instrumenting `java.lang.Runtime.exit` itself ([Kestra, Byte Buddy](https://medium.com/kestra-engineering/replacing-javas-securitymanager-with-a-byte-buddy-agent-539f218c3eb5)) | Not tried | Catches every caller (watchdog included) but needs bootstrap-class retransformation and Byte Buddy; overkill for one call site |
| [`junit5-system-exit`](https://todd.ginsberg.com/post/testing-system-exit/) (agent self-attached by a JUnit extension) | Not tried | Rewrites *every* `System.exit` into an exception and is designed for asserting exit codes; adds a dependency for what our 40 lines already do |
| Subclass `DedicatedServer` overriding the `protected onServerExit` | Inspected | Construction sits in a lambda in `net.minecraft.server.Main.main` (≈200 calls of setup, 12-argument constructor). We would have to copy and maintain Main per Paper release. Rejected |
| **Shutdown-hook hold, no agent** (`-Pvi6ItShutdown=hook`) | **Verified: 22.6 s, 9/9 reports, full graceful stop in 1.2 s** | **Fallback** — see below |
| Server in a child process ([sny1411/Bingo e2e](https://github.com/sny1411/Bingo/issues/96), [MiniTestFramework](https://github.com/MiniDigger/MiniTestFramework)) | Brief's fallback | Not needed |

**Shutdown-hook hold.** Before `Bukkit.shutdown()`, the fixture registers a JVM shutdown hook. The server's `System.exit` then starts JVM shutdown, but other threads keep running while hooks run, so JUnit and Gradle finish reporting. Once results are sent, Gradle's worker calls `System.exit` itself and blocks behind the hook. The hook polls for that state (the worker thread has a `java.lang.Shutdown` frame) and releases. The first attempt waited for the worker thread to *end* instead; it deadlocked until a 2-minute timeout (build 2 min 18 s, still green).

- Pros: plain test code, no agent jar or `-javaagent`. Independent of Paper's method names.
- Cons:
  - It depends on Gradle's worker calling `System.exit` after reporting (internal behaviour), and on the JDK-internal frame name `java.lang.Shutdown`.
  - The JVM's other shutdown hooks run as soon as the server exits. Log4j's hook is one of them, so server logging stops for whatever remains of the run.
  - The process exit code is the server's (70 on an abnormal exit, which would fail the Gradle task — arguably useful).
  - Not tried with `integrationTestInPlugin`.

**Recommendation unchanged:** use the agent (deterministic, the approach JEP 486 itself suggests). Keep the hook hold documented as the no-agent fallback if the agent ever becomes a problem, for example if `-javaagent` conflicts with another agent or tool.

### 3. ProtocolLib and FAWE — yes

Both are enabled (`isEnabled()`), loaded from the bundle's jars as ordinary server plugins. FAWE logs a harmless LZ4-binding error and history warnings on Windows; OSHI logs registry and perf-counter errors. Neither affects anything.

### 4. Boot timings

| Run | Paper `Done (…)` | Fixture: JVM start of boot → first tick | Stop | Gradle wall time¹ |
|-----|------------------|------------------------------------------|------|-------------------|
| 1 | — | — | — | 91 s (compile error in the fixture; includes the ~56 s `paperweightUserdevSetup` re-run) |
| 2 | 11.08 s | 11 133 ms | (worker killed) | 24 s (failed) |
| 3 | 11.50 s | 11 560 ms | 950 ms | 21 s |
| 4 | 11.31 s | 11 413 ms | 834 ms | 20.6 s |
| 5 | 11.71 s | 11 823 ms | (no explicit stop) | 17.2 s |
| 6² | — | 12 368 ms | 1 024 ms | 19.4 s |
| 7 | — | — | — | 15 s (`integrationTest` only, rerun after the Q5 change; timings not extracted) |
| final | — | — | 591–677 ms | 33 s (both tasks, two boots, after review fixes) |

¹ `gradlew integrationTest` with compilation up to date, including shadowJar and staging the bundle. The first run also re-ran `paperweightUserdevSetup` (~56 s) because the dev bundle was bumped (see Pitfalls).
² `integrationTestInPlugin` (Q5 target design).

Roughly 3 s of the boot is plugin load and enable plus world preparation; the rest is vanilla bootstrap. Booting once per test JVM is affordable; booting once per test class is not.

### 5. Test classes through the plugin classloader — yes, at both levels

- **Mechanism:** a `URLClassLoader` whose parent is Vi6's `PaperPluginClassLoader`, over a source set (`itPlugin`) that is compiled against `main` but is **not** on the test runtime classpath. A typed probe in it runs `Ioc.resolve(Vi6Game.class)` and reads the phase machine and map name (`SolarIndustries`) on the main thread. A nested `LauncherFactory` run of a Jupiter class loaded this way found and passed all 3 of its tests. `@Test` resolves to the same class through the parent chain.
- **Target design (Quarkus-style), verified:** Gradle 9.4's `JUnitPlatformTestDefinitionProcessor` opens the `LauncherSession` **before** reading the thread context classloader, and then loads every test class with `Class.forName(name, false, tccl)`. So a `LauncherSessionListener` (registered in `META-INF/services`) that boots Paper in `launcherSessionOpened` and sets the TCCL to the plugin-child loader is enough. A `Test` task with `testClassesDirs = itPlugin` output and the integration-test runtime classpath discovered, ran and reported `InnerVi6Test` natively (its own `TEST-*.xml`, 3/3 passed). The test asserts its own loader's parent is Vi6's plugin loader. No `LauncherInterceptor` was needed.
- So the `vi6-testapi` / `ServicesManager` fallback is **not needed**.

### 6. Unrelocated Core in several plugins — isolated, with a caveat

Vi6 was built with the `fr.nekotine.core` relocation dropped. Three extra plugins were added: `CoreTwinPaper` (paper-plugin.yml, ships Core), `CoreTwinLegacy` (plugin.yml, ships Core) and `CoreLeakLegacy` (plugin.yml, ships **no** Core). Each registers a `String` singleton in `Ioc`.

- Vi6, `CoreTwinPaper` and `CoreTwinLegacy` each get their **own** `Ioc` class (3 different loaders) and their own provider, and each resolves only its own singleton. **Isolated.**
- Paper plugins are sealed: `CoreTwinPaper` cannot see `Vi6Main` or ProtocolLib classes.
- **Caveat:** legacy `plugin.yml` plugins use Paper's global lookup. They can see `Vi6Main` (through Vi6's `PaperPluginClassLoader`) and ProtocolLib. `CoreLeakLegacy` therefore got **Vi6's** `Ioc`, and its singleton `twin:CoreLeakLegacy` landed in Vi6's container. Isolation only holds if every plugin that uses Core ships its own copy, which already holds for Vi6 and NekotineBots. It is not a risk in the test server as long as the bundle has no legacy plugin that relies on Core without shipping it.

## Things the real spec must carry over

1. **CommandAPI relocation must change with the Core relocation.** Core's jar already ships CommandAPI as `fr.nekotine.core.commandapi`. Vi6's `relocate("dev.jorel.commandapi", "fr.nekotine.vi6clean.nekotinecore.commandapi")` only matched because of the Core relocation. Without it, `Vi6Main.onLoad` throws `NoClassDefFoundError: …nekotinecore/commandapi/arguments/AbstractArgument`. Use `relocate("dev.jorel.commandapi", "fr.nekotine.core.commandapi")`.
2. **No-exit agent** as a small source set or module, added with `-javaagent` to every integration-test task. Re-check `DedicatedServer.onServerExit` on Paper upgrades, since the agent is keyed on that method name.
3. **Session-listener boot + TCCL swap** is the entry point. Vi6-typed tests live in a source set that is never on the test runtime classpath and is used only as `testClassesDirs`. The listener should only act when a system property is set, so plain suites stay unaffected.
4. **Bundle staging in Gradle** (`Sync` into `build/it-server/`, rewrite `server.properties`), not in the JUnit fixture: the worker's working directory has to exist before the JVM starts. The bundle location is one Gradle property (`vi6ItBundle` in the spike).
5. `mappedServerJar.jar` was taken from `.gradle/caches/paperweight/taskCache/`, with no task dependency producing it: on a clean clone it only exists because `paperweightUserdevSetup` runs for `compileJava` first. The real build should get it from paperweight's configuration or task output.
6. `integrationTest` stays out of `build`/`check`: confirmed, `gradlew build` ran only `test` (NO-SOURCE).
7. **The agent must check itself.** It logs when it removes the call, but if a Paper upgrade renames `onServerExit` it silently patches nothing and the worker dies again with Gradle's opaque `Could not write '/127.0.0.1:…'`. Record a flag on patch and fail loudly at shutdown if it is unset.
8. **Version alignment.** The bundle's ProtocolLib is 5.5.0-SNAPSHOT while Vi6 compiles against 5.4.0, and the bundle's Paper (134 on the dev server) is not the dev-bundle build the tests boot (135). Pin the bundle's plugin versions next to the build's.
9. **Port and world isolation.** The staged server uses fixed port 25599, so two concurrent runs (two worktrees) collide; pick a free port at staging time. `Sync` re-copies `worlds/` each run, which resets world state between *runs* but not between *tests*: game reset must not rely on the world.

## Pitfalls found

- `gradlew --write-locks` re-resolves every dynamic version, and it changed the production build on this branch:
  - `compileClasspath` moved to dev bundle `26.2.build.135-stable`. `mojangMappedServer` and `test*` stay on `65-beta`, so configurations now disagree. At runtime the server jar and `mojangMappedServerRuntime` were both 135.
  - The ~30 `dev.jorel:commandapi-*:11.2.1-SNAPSHOT` entries collapsed into `commandapi-paper-shade:11.2.0`, a downgrade.
  - It also rewrote `NekotineCore/gradle.lockfile` through `includeBuild` (reverted in the spike).
  
  The real change must write locks only for the new configurations (`--update-locks` on the specific modules, or lock per configuration) and review the lockfile diff. **This branch's `gradle.lockfile` must not be merged.**
- On this branch the production `shadowJar` has the Core relocation dropped. Running bare `gradlew` would deploy that jar to the dev server.
- Vi6 `onDisable` throws an NPE (`BukkitTask.isCancelled()` on a null task) when `plugins/update/` does not exist. `UpdateFolderRestart` fails to start, then `close()` dereferences the missing task. The staged bundle has no `plugins/update/`. This is a pre-existing bug, not caused by the spike.
- A `Jar` task that copies from `provider { zipTree(filteredConfiguration) }` can stay UP-TO-DATE with stale content after the filter changes. Delete the output or use a proper input when rebuilding the real twin or host plugin jars.
- Never run bare `gradlew` while testing: the `out` task drops the jar into the dev server's `plugins/update` and restarts it.

## Spike code (branch `spike/test-framework`)

- `build.gradle.kts`: `integrationTest` suite, `integrationTestInPlugin` task, `prepareItServer`, `itAgentJar`, twin jars, relocation changes (all marked `SPIKE`).
- Raw run output: `build/spike-logs/` (not versioned).
- `src/integrationTest/…/it/`: `PaperServerExtension`, `InPluginSessionListener`, `ServerBootIT`, `PluginClassLoaderIT`, `CoreIsolationIT`.
- `src/itPlugin/…`: `InnerProbe`, `InnerVi6Test`. `src/itAgent/…`: `NoExitAgent`. `src/coreTwin/…`: `CoreTwinPlugin` + descriptors.
- Run: `gradlew integrationTest` and `gradlew integrationTestInPlugin`. `-Pvi6ItBundle=<dir>` applies to both; `-Pvi6ItShutdown=none|hook` applies only to `integrationTest` (the in-plugin task always uses the agent).
