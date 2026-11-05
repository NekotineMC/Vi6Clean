---
reviews: ARCHITECTURE-SPINE.md (updated 2026-11-05)
lens: good-spine rubric
date: 2026-11-05
verdict: revise before epics/stories
---

# Rubric review: NekotineBots in NekotineCore + Vi6Clean bot integration

**Verdict: revise before handing to epics/stories.** The paradigm is sound. The library boundary (AD-3, AD-4), tick model (AD-5), path contract (AD-6, AD-7), human-rules rule (AD-12) and test split (AD-13) are the right divergence points, and they are mostly enforceable. The spine is weakest where it touches the existing repositories and the running servers. The build migration that AD-1 and AD-2 imply contradicts NekotineCore's current build and is not planned. The "ephemeral" rule ignores vanilla player-data saving. The operational envelope says nothing about what happens when the bot self-test fails or about production. The Stack table does not match the lockfiles. Two of the deferred items let parallel stories diverge.

Evidence was checked against the working trees at Vi6Clean `8504f05` and NekotineCore `d18d031`, and against the PaperMC, Maven Central and Gradle plugin portal repositories on 2026-11-05.

## Findings

### F1 [high] Build migration: AD-1 and AD-2 contradict NekotineCore's current build, and the migration is not planned

- **What the codebase does today.**
  - `NekotineCore/build.gradle.kts` applies Shadow, shades `commandapi-paper-shade` (`implementation`) and relocates it to `fr.nekotine.core.commandapi`.
  - It also replaces the `apiElements` and `runtimeElements` artifacts with the shadow jar, and excludes CommandAPI from both variants.
  - Vi6Clean depends on this: `relocate("dev.jorel.commandapi", "fr.nekotine.core.commandapi")` is commented "matches Core's own shading", and `verifyShadowJarLayout` (wired into `check`) requires entries under `fr/nekotine/core/commandapi/`.
- **What the spine says.** AD-2 says "`core` and `bots` publish plain jars and never run shadow on their dependencies". That is a breaking change to the only consumer's build. The spine presents it as a rule, not as a migration, so no story will own it. Questions it leaves open:
  - Does `core` expose CommandAPI as `api`/`implementation`, so that Vi6 shades and relocates it?
  - Does Vi6's own `compileOnly(libs.commandapi)` (locked at 11.2.1-SNAPSHOT, while Core locks 11.2.0) stay?
  - Which CommandAPI version wins?
- **Substitution and coordinates are undecided.** Vi6 declares `implementation(project(":NekotineCore"))` against `includeBuild("NekotineCore")`. After the split, the included build's root becomes an aggregator with no Java component. The spine names the modules `core` and `bots`, which would give the coordinates `fr.nekotine:core` and `fr.nekotine:bots`. Nothing says how Vi6's dependency declaration changes or what the root project is called.
- **There is an in-repo precedent.** `paper-junit-server/settings.gradle.kts` already uses this pattern: root `paper-junit-server-build`, subprojects named after their artifacts (`paper-junit-server`, `paper-junit-server-agent`, ...), so `includeBuild` substitution by `group:name` keeps working.
- **Also not addressed:**
  - per-module lockfiles (the root `gradle.lockfile` and `settings-gradle.lockfile` move);
  - shared Spotless configuration;
  - the version catalog that `core` and `bots` would share;
  - Core's `out` default task;
  - NekotineCore's `AGENTS.md`, which still says Vi6 relocates `fr.nekotine.core` to `fr.nekotine.vi6clean.nekotinecore`. That is stale, but story agents read it.
- **Fix.** Add an AD, or extend AD-1, covering:
  - the target project names and coordinates (for example root `NekotineCore-build`, with subprojects `NekotineCore` and `NekotineBots`, so `fr.nekotine:NekotineCore` stays stable);
  - Vi6's new dependency lines;
  - where CommandAPI is shaded and relocated after AD-2, and what happens to `verifyShadowJarLayout`;
  - lockfile handling;
  - one migration story that lands as one NekotineCore PR plus the Vi6 gitlink PR, with the build green, before any bots story starts.

### F2 [high] AD-10 "nothing persists" is not enforceable as written; bot identity is undecided

- **What vanilla does.** AD-10 routes bots through `PlayerList.placeNewPlayer` and "the normal quit path". In vanilla, that path loads `world/playerdata/<uuid>.dat` on join. On remove, it saves player data, stats and advancements.
- **The result.** A bot respawned with the same UUID gets back its old inventory, location and effects. A bot with a random UUID leaves one file per spawn in the world folder, including the Vi6 map worlds staged from `test-bundle/worlds/`. Either way something persists, and CAP-5's "each run starting from a clean roster" can be broken by restored state.
- **What is missing.** No AD or convention fixes:
  - how the UUID is generated (offline-style `OfflinePlayer:<name>`, random, or namespaced);
  - name rules (16-character limit, collisions with a real player or a bot from another host);
  - whether player-data saving and loading is suppressed.
- **Why it matters.** The body story and the Vi6 command story will each choose differently.
- **Fix.** Add these to AD-10 or to a new AD: bot identity is `<rule>`, and the body suppresses or erases player-data, stats and advancement I/O. The self-test, or a library test, should check that no `playerdata` file remains after remove.

### F3 [high] Operational envelope: what a failed self-test does is undecided, the self-test may not run at boot, and environments are incomplete

- **The self-test may not run at boot.** AD-3 says the self-test "runs when the bot module loads and fails loudly at boot". In Core, `ModuleManager` creates modules lazily on the first `Ioc.resolve` or `get`. If no host forces the roster to load at enable, the self-test runs at the first `/bot add`, not at boot. The spec's Demonstration step 1 expects it in the boot log.
- **"Fails loudly" is not defined.**
  - `ModuleManager.load` catches every constructor exception, logs it and leaves the module unregistered.
  - `Vi6Main.onEnable` only logs.
  - So "loudly" amounts to one error line, then a missing roster, and `Vi6SmokeIT` would still pass.
- **No degradation policy.** "One Minecraft version per release" means a Paper update on the server can break the NMS layer while the game itself is fine. The spine does not say whether Vi6 must keep running without bots (bots disabled, commands refuse) or must refuse to enable. This is the most likely production incident for this feature.
- **Environments.**
  - The Deployment paragraph covers only the dev server and the integration suite. Production is not mentioned: does the bots module ship and activate there, given the jar is the same?
  - It says bots need spawn protection disabled, but `test-bundle/server.properties` has `spawn-protection=16`. That folder is a gitignored copy of production, and `test-bundle/README.md` does not mention the setting, so bot integration tests near spawn fail differently on different machines.
  - "`max-players` ≥ 6" is probably not needed: `placeNewPlayer` skips the login-time capacity check, and `max-players=20` is already set. It is either unverified or wrong.
- **Fix.** Add one AD with these rules:
  - the host eagerly loads the roster at enable;
  - a self-test failure marks the bots module unavailable, logs at ERROR with the failing accessor, and makes bot commands refuse;
  - the game keeps running;
  - an integration test asserts the self-test passed.

  Also list the server settings bots need for each environment (dev, integration `test-bundle`, production) and add them to `test-bundle/README.md`.

### F4 [high] The Stack table does not match the repositories and is not current

| Item | Spine | Actually locked / current |
| --- | --- | --- |
| Paper API | 26.2.build.130-stable | Vi6 `gradle.lockfile` locks dev bundle and paper-api **26.2.build.65-beta**. NekotineCore locks paper-api **26.2.build.31-alpha**. paper-junit-server's harness hard-codes 65-beta. The newest 26.2 is 135-stable, and **26.3 is already out (26.3.build.242-stable)**. |
| paperweight userdev | 2.0.0-beta.21 | Matches Vi6. The plugin portal's latest is 2.0.0-beta.24. Check which beta supports 26.3, since the spine plans that move. |
| CommandAPI | 11.2.1-SNAPSHOT | Vi6 locks 11.2.1-SNAPSHOT; NekotineCore locks 11.2.0 (catalog `11.2.+`). Those are two versions in one shaded jar, see F1. |
| ProtocolLib | 5.4.0 compile; 5.5.0-SNAPSHOT test server | Matches: 5.4.0 is the latest release on repo.dmulloy2.net. `test-bundle/README.md` asks plugin jars to match the compile version, which this split breaks. |
| Pathetic | engine 5.5.2 | Verified current on Maven Central (released 2026-06-16). |
| Spotless 8.4.0, JUnit | missing | Spotless is in both builds. The `bots-test-host` will need a JUnit version. |

- **The Paper claim does not hold.** The memlog says 130-stable came from "javap on the jar Vi6 builds with". The lockfile says otherwise: 130-stable is only in the local Gradle cache.
- **Divergence the table does not cover.** NekotineCore and Vi6 have separate lockfiles. So `bots` can compile its NMS code against one dev-bundle build while Vi6 compiles, and the integration server runs, against another. AD-1's "moves with Vi6" is a statement, not a mechanism.
- **Fix.**
  - Correct the table to the locked versions.
  - Add a rule that the `bots` dev-bundle version equals the host's: share it through the catalog, or let the host's lock drive it.
  - Add a matching check, for example the self-test logs the build it was compiled against and the running build.

### F5 [medium-high] AD-11 leaves the spec's main failure case (host brains) unruled, and the canonical SPEC still contradicts the spine

- **The case AD-11 leaves out.** AD-11 hands exceptions in host tick listeners to Paper. Paper logs each listener exception on every call, without the bot's name. A broken Vi6 brain therefore logs about 20 stack traces a second and keeps the bot "acting", which is the exact failure AD-11 says it prevents. The spec's constraint ("An exception in bot behaviour is caught and logged with the bot's name") is about behaviour, which by this spine's layering is host code.
- **No owner for the Vi6 brain layer's error policy.** Possible owners: a Vi6 AD, or a library helper (for example a guarded listener base, or `Bot.fail(Throwable)` that hosts call), which keeps the policy in one place.
- **The SPEC is out of date in three places.** It declares itself the "canonical contract", but it still says:
  - NekotineBots is "its own repository" and "stays out of NekotineCore" (AD-1 reverses this);
  - failures are logged "with the current phase" (AD-11 drops this);
  - "depends on NekotineBots".

  The memlog records "Offer to update SPEC.md", and the update was not made. Story writers who follow the SPEC will build the wrong repository layout.
- **Fix.** Extend AD-11 with a host-side rule, or a library guard hosts must use, and update SPEC.md, or record the overrides in the spine with a "supersedes SPEC" note.

### F6 [medium] Two deferred items let parallel stories diverge

- **The control port's method set** is deferred to "the first walker story". The bot adapter (bots), the vanilla-like walker (core) and the melee controller (core) are three units that all code against that port. The melee story also needs `attack(target)`, whose target type (Bukkit `Entity`? UUID?) is not fixed. AD-8 itself is tagged `[ASSUMPTION]` although its heading says ADOPTED. Either fix the method signatures in the spine, or make "control port interface" a standalone first story that the other three depend on.
- **The pathfinder interface shape.** AD-7 allows off-thread search "completing the caller's result on the main thread". It does not decide:
  - whether the call is synchronous or returns `CompletableFuture<PathResult>`;
  - how "no path", "partial/fallback" and "length-limited" are represented (`null`, empty `PathResult`, `canReachFinalPoint()==false`). Pathetic has five states.
  - where a walker keeps its progress, given that `PathResult.getNextPointIndex()` is read-only.

  Different implementations (own A*, Pathetic, navgraph, vanilla) will answer these differently, and walkers will special-case them.

### F7 [medium] The open question blocks an ADOPTED decision

AD-13 (ADOPTED) needs NekotineCore's `bots-test-host` to run on paper-junit-server, and the one Open Question says that is unsolved. CAP-7's success criterion (a second plugin spawns, drives and removes a bot) depends on that test host. Name an owner and decide before the bots epic starts. Candidate answers:
- publish paper-junit-server to `mavenLocal` or GitHub Packages;
- a `NekotineCore/settings.gradle.kts` that includes `../paper-junit-server` when present (which works only inside the Vi6 checkout);
- add paper-junit-server as a sibling submodule of NekotineCore (the spine rules out nesting).

### F8 [medium] The tick sequence leaves out the player tick and listener reset; brownfield names are off

- **The player tick and listener reset are missing.** The spec says the bot's own tick "resets the listener position, sends pending acks, runs actions and physics". Nothing else ticks the fake connection, so `ServerGamePacketListenerImpl.tick()` and therefore `ServerPlayer.doTick()` do not run unless the body calls them. Vanilla per-player state hangs on that tick: the attack-strength ticker that CAP-8's cooldown depends on, item cooldowns, effects, the anti-cheat position reset. Neither AD-5 nor the sequence diagram places it relative to acks, the host event, inputs and physics. That ordering is a cross-story divergence point (body story vs melee story).
- **The event name is wrong.** Core's event is `TickElapsedEvent` (`fr.nekotine.core.ticking.event`), not `TickEvent` (Consistency Conventions, Ticking row, and the sequence diagram).
- **The convention needs care.** `TickingModule` is itself a `runTaskTimer(..., 0, 1)`, and its constructor throws before `onEnable`. The "never a separate `runTaskTimer`" convention holds, but the roster must not be resolved in `onLoad`.
- **The scan behaviour works but is not documented.** `setupModules()` scans every class whose name starts with `fr.nekotine.core` in the host plugin's classloader, and calls `load()` on each. This ratifies the registration convention for `fr.nekotine.core.bot`, because bots is shaded into the same jar. It also means every `bot.internal` class is loaded when the plugin loads. Fine, but the spine should note that internal classes must not run NMS work in static initialisers.

### F9 [medium] Multi-host claims depend on classloader isolation the spine does not state

- AD-2 and the Packages convention leave `fr.nekotine.core` and `fr.nekotine.core.bot` unrelocated, as Vi6 does today.
- AD-10 and CAP-7 expect a second host plugin on the same server with its own roster.
- That works only because Paper plugins (`paper-plugin.yml`) get isolated classloaders. A legacy `plugin.yml` host shading the same unrelocated packages can clash.
- **Fix.** State the requirement (hosts are Paper plugins, or must relocate) in AD-2.

### F10 [low] AD-1's "core stays unpinned" does not survive a shared commit

`core` and `bots` share one commit. A second host on 26.2 cannot take `core` fixes once `bots` moves to 26.3 without also taking the 26.3 `bots`. The Deferred item "publishing versioned artifacts" is the escape hatch. Say explicitly that this trade-off is accepted until then.

### F11 [low] Layer table order contradicts "each layer depends only on the one below"

The table lists the core Strategies and Ports above the bots API and Body. But `bots` depends on `core` (Ioc, the control port it implements), and Host depends on both. Either reorder the table, with core Ports at the bottom and bots beside or above the Strategies, or describe it as a dependency graph, which the mermaid diagram already does correctly.

### F12 [low] Smaller points

- AD-5: when two listeners at the same `EventPriority` set the same control, registration order wins. Say so, or ban the case.
- AD-4 has no control for "stop using item" (release use), which bows and charge items need. Vi6 has `BowChargeModule` and `ItemChargeModule` consumers (`test-coverage-boundary.md`). Add it, or defer it explicitly.
- AD-6: Paper's `PathResult` is an API interface, and Paper does not promise it is stable to implement outside Paper. Note the risk, and that a Paper change would break every pathfinder implementation at once.
- AD-14 / Vi6: CommandAPI's namespace is hard-coded `"vi6"` in `NekotinePlugin.onLoad`. That is harmless for Vi6, but CAP-7's second host inherits it.

## Checklist

| Check | Result |
| --- | --- |
| Fixes the real divergence points for the level below, misses none | **Partial.** Strong on boundary, inputs, tick and path contract. Missed: build migration (F1), bot identity and persistence (F2), self-test and degradation (F3), dev-bundle alignment (F4), host error policy (F5), player-tick ordering (F8). |
| Every AD's Rule is enforceable and prevents its divergence | **Partial.** AD-10 is not enforceable as written (F2). AD-11 does not prevent its stated divergence for host code (F5). AD-3 "fails loudly at boot" is undermined by lazy module loading and swallowed exceptions (F3). AD-8 is an ADOPTED decision resting on an `[ASSUMPTION]` (F6). The other ADs pass. |
| Nothing under Deferred lets two units diverge | **Fail** for the control-port method set and, implicitly, the pathfinder interface shape (F6). The other deferred items are safe. |
| Named tech verified-current | **Fail** for Paper (wrong build, 26.3 stable already out). paperweight is behind latest. CommandAPI is split across two locked versions (F4). Pathetic and ProtocolLib are fine. |
| Ratifies, doesn't contradict, the brownfield | **Partial.** It ratifies Ioc, `IPluginModule`, reflective `setupModules()`, `TickingModule`, the `fr.nekotine.core` prefix and includeBuild. It contradicts Core's shadow-as-variant build and Vi6's layout check without planning the change (F1). The event name is wrong (F8). |
| Covers the spec's capabilities | **Yes.** CAP-1 to CAP-5, CAP-7 and CAP-8 are all mapped (the spec has no CAP-6). Gaps inside them: CAP-5's clean repeat depends on F2, CAP-8's cooldown depends on F8, and CAP-7's proof depends on F7. |
| Altitude dimensions decided, deferred or open | **Fail on operations.** Deployment is covered for dev and integration only, with no production story, no failure or degradation policy, and an environment setting (spawn protection) that conflicts with the existing `test-bundle` (F3). Build migration: see F1. |
| Mermaid validity | **Pass.** Both diagrams parse: `graph TD` labels contain no reserved characters, and `-.->|never|` is valid. In the `sequenceDiagram`, the participant aliases, the `loop`/`end` block and the `Note over R,B` are well formed. Content issues only: `TickEvent` should be `TickElapsedEvent`, and the player tick is missing (F8). |

## Suggested order of fixes

1. F1 (build migration AD and first story)
2. F4 (correct the Stack, and add the dev-bundle alignment rule)
3. F2 (identity and persistence)
4. F3 (self-test and degradation, plus the environment settings)
5. F5 (host error policy, plus the SPEC update)
6. F6 and F7 (settle the port and pathfinder shapes, and answer the open question)
7. F8 to F12 as edits
