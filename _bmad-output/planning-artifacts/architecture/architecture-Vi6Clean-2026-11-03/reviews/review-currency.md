---
review: currency
target: ../ARCHITECTURE-SPINE.md (Stack table, AD-1, AD-2, AD-6) and ../.memlog.md version entries
date: 2026-11-05
lens: "Every committed decision web-researched or reality-checked, not asserted from training data: current versions, named technologies still exist and fit."
verdict: CONCERNS
---

# Currency review: NekotineBots architecture spine

## Verdict

**CONCERNS.** Every named technology exists and fits the role the spine gives it. The one API the design rests on, Paper's `Pathfinder.PathResult`, is current and not deprecated. Three problems remain:

- The Stack table records a Paper build that the project does not compile against.
- The table describes a 26.2 stack that has already been superseded. It treats "moves to 26.3" as a version bump, but the move pulls paperweight, Gradle and CommandAPI along with it, and CommandAPI changes major version.
- AD-2 ("libraries never shade") contradicts what NekotineCore does today, and the spine does not record the migration this causes.

None of these breaks the design. All of them should be fixed before stories are cut from the Stack table.

## What was checked and how

| Item | Spine says | Project reality (files, cache) | Web, as of 2026-11-05 | Status |
| --- | --- | --- | --- | --- |
| Paper API | `26.2.build.130-stable` ("javap on the jar Vi6 builds with") | `gradle.lockfile`: `paper-api` and `dev-bundle` **26.2.build.65-beta**. `NekotineCore/gradle.lockfile`: `paper-api` **26.2.build.31-alpha**. Builds 130-stable and 135-stable are in the Gradle cache, but neither lockfile pins them. | 26.2 latest is build 135 (STABLE, 2026-10-25). Build 130 is STABLE (2026-10-05). Build 65 is BETA (2026-07-22). **26.3 has been STABLE since 2026-10-11** (build 242 on 2026-11-05). Paper's `main` README targets 26.3. 26.4 is not listed. | **Wrong / stale** |
| `Pathfinder.PathResult` | Shared path contract (AD-6) | 65-beta and 130-stable sources: `PathResult` is identical in both (`getPoints`, `getNextPointIndex`, `getNextPoint`, `getFinalPoint`, `canReachFinalPoint`). No `@Deprecated` or `@ApiStatus` on it. Between the two builds, only `findPath(..., int reachRange)` overloads were added. | Paper `main` (26.3) `Pathfinder.java`: same five methods. No `@Deprecated`, `@ApiStatus.Obsolete` or `@ScheduledForRemoval` on `Pathfinder`, `PathResult` or `findPath`. | **OK** |
| paperweight userdev | 2.0.0-beta.21 | Locked in Vi6Clean. The cache has only beta.21. | Latest is **2.0.0-beta.24** (2026-09-25). Paper's docs use beta.24 and say "only the latest version … is officially supported". beta.24 (PRs #408 and #411) requires **Gradle ≥ 9.7.1**; beta.23 requires Java 21 to run Gradle. beta.21 dates from 2026-04-03 ("prepare for 26.1"). | **Stale; blocks 26.3** |
| Gradle | 9.4.1 | The wrappers of both Vi6Clean and NekotineCore are on 9.4.1. | Latest is 9.8.2 (2026-10-19). 9.4.1 cannot resolve paperweight beta.24. | **Stale; coupled to the above** |
| userdev in one subproject only | AD-1: `bots` applies userdev, `core` does not | NekotineCore is a single-project build today (`settings.gradle` with no `include`). Vi6Clean's root applies userdev itself and consumes NekotineCore through `includeBuild`. | Multi-project is supported. The official `paperweight-test-plugin` `multi-project` branch applies userdev in several subprojects. Its README warns that applying it independently in several projects can fail, because of a Gradle bug, and that the workaround is `apply false` on the root. With userdev in exactly one subproject, as AD-1 does, the warning does not apply inside NekotineCore. Nothing checked covers the composite case: Vi6Clean's root userdev plus NekotineCore's `bots` userdev in an included build. | **Fits; composite case unverified** |
| ProtocolLib | 5.4.0 compile; 5.5.0-SNAPSHOT on test server | Locked at `ProtocolLib:5.4.0`. `test-bundle/plugins/ProtocolLib.jar` reports `5.5.0-SNAPSHOT-2d3b3e3`. | 5.4.0 (2025-08-07, Maven Central) is still the latest release and supports up to MC 1.21.8. 26.2 and 26.3 support exists only in the GitHub "Development Build" pre-release (5.5.0-SNAPSHOT). There is no 5.5.0 release. | **Accurate; risk unstated** |
| CommandAPI | 11.2.1-SNAPSHOT (hosts) | Vi6Clean locks `11.2.1-SNAPSHOT` with `commandapi-paper-26.2`, compile-only. **NekotineCore locks `11.2.0`, and that copy is shaded into the Vi6 jar at runtime.** | Central: 11.2.0 (26.1), **12.0.0 (adds 26.2)**, **12.1.0 (adds 26.3, latest, 2026-09-26)**. 11.2.1 was never released. The version range `11.2.+` can never reach 12.x. | **Stale; snapshot of a dead line** |
| Shadow | 9.4.+ | Resolves to 9.4.3, also in the cache. The plugin version is not locked; `settings-gradle.lockfile` is empty. | Latest is 9.6.1 (2026-07-22). | Minor |
| Pathetic | `de.bsommerfeld.pathetic:engine` 5.5.2 | Not yet a dependency. | Maven Central: 5.5.2 is the latest (2026-06-16). MIT, Java 8+, repository not archived. The engine is platform-agnostic: there is no Bukkit adapter, so NekotineCore writes its own world and passability adapter. | **OK** |
| Java | 25 | Toolchain pinned to 25. | Paper 26.1+ requires Java ≥ 25. | **OK** |
| Paper plugin classloader isolation | AD-2 assumes it implicitly: each host shades its own unrelocated `fr.nekotine.core` | Vi6 is a Paper plugin (`paper-plugin.yml`). It uses `join-classpath: true` only towards ProtocolLib, WorldEdit and voicechat. | Paper docs: Paper plugins "are not able to access each other unless given explicit access". `join-classpath: true` opts into another plugin's classloader. The docs do not say how two plugins bundling the same unrelocated package behave. | **Fits, with conditions the spine omits** |

## Findings

### F1 (High): The Stack table records a Paper build the project does not build against

The Stack row and the memlog `(version)` entry give Paper API as `26.2.build.130-stable` and say it was checked by "javap on the jar Vi6 builds with". The locked jar is **26.2.build.65-beta**, and NekotineCore is locked to **26.2.build.31-alpha**. The 130-stable jar was only in the Gradle cache. The `PathResult` conclusion still holds, because the interface is byte-identical across 65-beta, 130-stable and current `main`. The Stack table is not true of the project, though, and the two repositories already compile against different Paper builds.

**Fix:**
1. Correct the row to what the lockfiles say, or state the target build and add a story to move both lockfiles to it.
2. Add a convention that Vi6Clean and NekotineCore lock the same Paper and dev-bundle build. Without it, `bots` (NMS) compiles against one dev bundle while the host runs another.

### F2 (High): "26.2, moves to 26.3" ignores that 26.3 is already stable and that the move is a coupled jump

Paper 26.3 has had stable builds since 2026-10-11. Moving `bots` and Vi6 to 26.3 needs all of the following:

- the 26.3 dev bundle;
- paperweight **beta.24**, the only supported version and the one Paper's docs use for 26.3;
- **Gradle ≥ 9.7.1** in both wrappers, because beta.24's published variants refuse older Gradle;
- **CommandAPI 12.1.0**, a major-version change from the 11.x line;
- a ProtocolLib dev build that has the 26.3 commits.

Whether beta.21 can even set up a 26.3 dev bundle was not verified anywhere.

AD-1 says `bots` "moves with Vi6", so this toolchain jump is the normal upgrade path, not an edge case.

**Fix:**
1. Add a Stack note, or a deferred item, saying the version cluster moves together (Paper, dev-bundle, paperweight, Gradle, CommandAPI, ProtocolLib build).
2. Decide whether the first `bots` story targets 26.2 or 26.3. Building a new NMS module against a superseded line means re-verifying all of AD-3's NMS accessors within weeks.

### F3 (Medium): AD-2 contradicts NekotineCore's current shading, and the migration is unrecorded

Today `NekotineCore/build.gradle.kts` runs `shadowJar`, relocates CommandAPI to `fr.nekotine.core.commandapi`, and publishes the shadow jar as `apiElements` and `runtimeElements`, excluding `commandapi-paper-shade`. Vi6 declares CommandAPI `compileOnly`. The CommandAPI in Vi6's jar is therefore NekotineCore's locked **11.2.0**, whose stated target is 26.1, running on 26.2. Vi6's 11.2.1-SNAPSHOT is used only at compile time.

AD-2 ("`core` and `bots` publish plain jars and never run shadow") reverses this. After the change:

- `core` must expose CommandAPI as a normal `implementation` or `api` dependency;
- every host must shade and relocate it;
- Vi6's `compileOnly(libs.commandapi)` and its `verifyShadowJarLayout` expectations must be reconciled.

The relocation target, `fr.nekotine.core.commandapi`, matches across hosts. Under isolated classloaders that is harmless.

**Fix:**
1. Record this as a known consequence of AD-2, or as a story.
2. Align the CommandAPI version in one place (see F4).

### F4 (Medium): CommandAPI 11.2.1-SNAPSHOT is a snapshot of an abandoned line

11.2.1 was never released. Central has 11.2.0, 12.0.0 (26.2) and 12.1.0 (26.3). The range `11.2.+` on CodeMC snapshots cannot pick up 12.x. If the snapshot is purged from CodeMC, a lock-only resolution breaks.

**Fix:** Name CommandAPI 12.0.0 (26.2) or 12.1.0 (26.3) in the Stack table, in both repositories, and check its breaking changes before the bump. The 12.0.0 release page lists none, so read the full changelog.

### F5 (Medium): ProtocolLib for 26.x exists only as an unreleased dev build

This is correctly recorded: compile against 5.4.0, run 5.5.0-SNAPSHOT `2d3b3e3`. The spine does not record what follows from it:

- The `EmbeddedChannel` injection behaviour that the whole bot body depends on (SPEC "Verified" note) was proven only on dev build `2d3b3e3`. ProtocolLib is a required server plugin, so production and dev servers must run a matching or newer dev build.
- 5.4.0's API predates 26.x. No 5.5.0 artifact is on Central or in an enabled repository; the dmulloy2 repository is commented out in `build.gradle.kts`.

**Fix:**
1. Pin the dev-build commit in `test-bundle/README.md` or the Stack table.
2. Add the boot self-test (AD-3) check that ProtocolLib injected the bot's channel.

### F6 (Medium): AD-2 depends on Paper plugin isolation, but the spine never states it

The Paper docs confirm that Paper plugins are classloader-isolated unless they opt in with `join-classpath`. The docs do not cover duplicate unrelocated packages. AD-2 is safe only under these conditions:

1. Every host is a Paper plugin (`paper-plugin.yml`), not a legacy `plugin.yml` plugin.
2. No host declares another Nekotine host as a dependency with `join-classpath: true`.
3. No Nekotine library types cross plugin boundaries, for example through Bukkit services or events carrying `fr.nekotine.core` types.

Two hosts on one server each load their own `fr.nekotine.core.bot` classes. This is consistent with AD-10's "separate rosters", but `instanceof`/cast across hosts fails.

**Fix:** Add these conditions to AD-2's rule, or to "Prevents".

### F7 (Low): Composite build with two userdev applications is unverified

The multi-project guidance covers userdev in several subprojects of one build. Vi6Clean applies userdev at its root and would also build NekotineCore `:bots` (userdev) through `includeBuild`. The two may also resolve different dev bundles (F1). The current `implementation(project(":NekotineCore"))` substitution would also need to target `:core` and `:bots` explicitly.

**Fix:** Spike `gradlew` on Vi6Clean with a two-module NekotineCore before the first `bots` story. Then document the `dependencySubstitution` or project path.

### F8 (Low): Shadow and Gradle trail current releases

Shadow 9.4.+ resolves to 9.4.3, while 9.6.1 is current. The plugin version is not locked. Gradle 9.4.1 trails 9.8.2. Neither causes a problem on its own, but Gradle is forced to move by F2.

## Confirmed as current (no action)

- `com.destroystokyo.paper.entity.Pathfinder.PathResult`: present and unchanged from 26.2 build 65 through Paper `main` (26.3), with no deprecation or removal markers.
- Pathetic `de.bsommerfeld.pathetic:engine` 5.5.2: latest on Central, MIT, Java 8+, maintained, no platform module. The memlog's "no follower, no world" description matches the README.
- Java 25: required by Paper 26.1+.
- Using paperweight userdev in one subproject only is a supported shape.

## Sources

- Project: `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `gradle.lockfile`, `gradle/wrapper/gradle-wrapper.properties`, `NekotineCore/build.gradle.kts`, `NekotineCore/gradle/libs.versions.toml`, `NekotineCore/gradle.lockfile`, `paper-junit-server/harness/gradle.lockfile`, `src/main/resources/paper-plugin.yml`, `test-bundle/plugins/ProtocolLib.jar` (`plugin.yml`)
- Gradle cache: `paper-api` 26.2.build.65-beta and 130-stable sources jars, `Pathfinder.java` diff; `javap -v` on the 65-beta `Pathfinder$PathResult` (no deprecation attributes)
- https://fill.papermc.io/v3/projects/paper
- https://fill.papermc.io/v3/projects/paper/versions/26.2/builds
- https://fill.papermc.io/v3/projects/paper/versions/26.3/builds
- https://github.com/PaperMC/Paper
- https://raw.githubusercontent.com/PaperMC/Paper/main/paper-api/src/main/java/com/destroystokyo/paper/entity/Pathfinder.java
- https://plugins.gradle.org/plugin/io.papermc.paperweight.userdev
- https://github.com/PaperMC/paperweight/releases
- https://github.com/PaperMC/paperweight/pull/408
- https://github.com/PaperMC/paperweight/pull/411
- https://docs.papermc.io/paper/dev/userdev/
- https://raw.githubusercontent.com/PaperMC/paperweight-test-plugin/multi-project/README.md
- https://docs.papermc.io/paper/dev/getting-started/paper-plugins/
- https://docs.papermc.io/paper/getting-started/
- https://gradle.org/releases/
- https://github.com/dmulloy2/ProtocolLib/releases
- https://repo1.maven.org/maven2/net/dmulloy2/ProtocolLib/maven-metadata.xml
- https://github.com/CommandAPI/CommandAPI/releases
- https://repo1.maven.org/maven2/dev/jorel/commandapi-paper-shade/maven-metadata.xml
- https://plugins.gradle.org/plugin/com.gradleup.shadow
- https://repo1.maven.org/maven2/de/bsommerfeld/pathetic/engine/maven-metadata.xml
- https://github.com/bsommerfeld/pathetic
