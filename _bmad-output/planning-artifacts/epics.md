---
status: final
stepsCompleted: [1, 2, 3, 4]
inputDocuments:
  - _bmad-output/specs/spec-bots/SPEC.md
  - _bmad-output/specs/spec-bots/test-coverage-boundary.md
  - _bmad-output/planning-artifacts/architecture/architecture-Vi6Clean-2026-11-03/ARCHITECTURE-SPINE.md
  - _bmad-output/planning-artifacts/architecture/architecture-Vi6Clean-2026-11-03/bots-adopter-guide.md
---

# Vi6Clean (NekotineBots) - Epic Breakdown

## Overview

This document breaks NekotineBots down into epics and stories. NekotineBots is the NekotineCore `bots` module, the `core` navigation and combat strategies, and the Vi6Clean bot layer. There is no PRD. `SPEC-bots` (SPEC.md and its companion test-coverage-boundary.md) is the requirements contract, and the architecture spine (AD-1..AD-14) supplies the technical rules. There is no UX document, because bots are command-only by design.

## Requirements Inventory

### Functional Requirements

- FR1: An operator can add N bots, each with a team intent, to a running server with one command and no restart. (CAP-1, AD-14)
- FR2: An operator can list the live bots, including each bot's state (live, or failed per AD-11). (CAP-1, AD-11)
- FR3: An operator can remove bots (chosen ones or all of them) with a command and no restart. (CAP-1, AD-10)
- FR4: A bot joins as a real fake player. It is in `getOnlinePlayers()`, `isOnline()` is true, `Bukkit.getPlayer(uuid)` resolves, `PlayerJoinEvent`/`PlayerQuitEvent` fire, and its UUID is the offline UUID derived from its name. Admission may use Vi6's normal join path. (Constraint, AD-10)
- FR5: Bots get the same team assignment, team balance, phase transitions, scoring and cleanup as humans. No Vi6 game-logic file special-cases bots. (CAP-2, AD-12)
- FR6: With no human connected, bots pick their team and ready up by sending `/game team` and `/game ready` as real chat-command packets, and the lobby advances. The team intent given at spawn is carried out only by the bot's brain. (CAP-2, AD-12)
- FR7: A bot can reach Vi6 handlers gated on `PlayerInteractEvent` (right and left click), `PlayerMoveEvent`, `PlayerItemHeldEvent`, sneak and sprint, and the tool's observable effect occurs. For now this is proven on one representative tool. Per-tool tests for all 37 tools come later, when the user asks for them. Block clicks hit a real block within reach. `Usable` overrides need the registered stack in hand. (CAP-3, test-coverage-boundary)
- FR8: A bot is controlled only through the AD-4 groups:
  - lifecycle: spawn (name, location, wipe flags), remove, list, `Player` handle;
  - movement: heading yaw, forward/back, strafe, jump, sneak, sprint;
  - aim: yaw/pitch;
  - hands: hotbar slot, use item, use item on block + face, left-click/start-break, attack entity, interact entity, swap hands;
  - other: send command, respawn.

  Movement keys, sneak/sprint, look and slot persist until changed. Hands actions and commands fire once. There is no setPosition, teleport or dialog control. (AD-4)
- FR9: With no operator input, a bot travels between map areas on its own for the whole round and respects the map's entrance and exit rules. (CAP-4)
- FR10: `core` provides a pathfinder port. It returns Paper's `Pathfinder.PathResult`, takes the caller's passability and cost rule for each search, and lets implementations be swapped without changing callers. (AD-6, AD-7)
- FR11: `core` provides a walker strategy that follows any `PathResult`, including a vanilla mob's. It keeps its own progress cursor and infers step, jump, drop and door from the point geometry and the world. (AD-6, AD-8)
- FR12: `core` provides a melee controller with which a bot picks a target, aims, positions itself and lands hits that respect the attack cooldown and reach, without being snapped back by the server. Positioning is set by the caller: a standoff distance and a positioning policy. In Vi6, PvP is one-sided: guards are armed, and thieves don't fight back. Guard bots therefore stand as close as possible to the thief and cut off its retreat, and thief bots never attack (they may flee). It is checked in a 1v1 against a human. (CAP-8, AD-8, user decision 2026-11-05)
- FR13: One console command starts an unattended run with 4 or 6 bots and zero humans. The run clears lobby, preparation, in-map and infiltration to a win condition, and the log shows the result with no exception. (CAP-5, AD-14)
- FR14: An unattended run can be repeated. Each run starts from a clean roster with no leftover bots, scoreboard entries or bot player data. (CAP-5)
- FR15: Any Nekotine minigame can spawn, drive, walk, fight with and remove bots through the NekotineCore API without NMS code of its own. A second plugin with no NMS code spawns a bot, makes it use an item and removes it. Vi6 uses the same API for all of its bot work. (CAP-7)
- FR16: Bots are ephemeral. The library removes every bot when its host plugin disables, and nothing persists or restores them. On removal, a bot's saved state and statistics are wiped by default, and each wipe can be turned off per bot at spawn. (Constraint, AD-10)
- FR17: A startup self-test checks every NMS or reflective accessor when the host enables, through a forced roster load. If it fails, the bots module logs loudly and disables itself, every bot API call reports "bots unavailable", and the host keeps running for humans. (Constraint, AD-3)
- FR18: When library bot work fails, the failure is logged once with the bot's name and stack trace. The bot's controls go neutral and it stops ticking, but it stays connected and the roster reports it as failed. A Vi6 brain guards its own tick listener: it catches, logs with bot and game context (such as the phase), and stops that brain. (Constraint, AD-11, AD-12)
- FR19: Vi6 owns test hooks for forcing teams and phase, shortening timers and resetting the game between tests (phase, teams, scoreboard). The hooks depend only on the bot API, and brains never call them. (test-coverage-boundary, AD-13)
- FR20: In preparation, a thief bot picks its entrance with a new Vi6 spawn-select command, which takes an entrance name from the map's thief spawns. Every bot then marks itself ready with a new Vi6 preparation-ready command, separate from the lobby's `/game ready`. Both commands are usable by humans too and need no entity interaction. Preparation ends as soon as all players are ready, without waiting out the timer. (User decision 2026-11-05, AD-12)

### NonFunctional Requirements

- NFR1: **Real handlers only.** Every input reaches the game as a real serverbound packet. It is written inbound into the fake connection's channel pipeline, so ProtocolLib inbound listeners see it, and is then handled by the real `ServerGamePacketListenerImpl.handleX`. No Bukkit event is fabricated, and no direct game-mode, slot, sneak or position call is made. Controls are sent on change, the way a client sends them. (Constraint, AD-4)
- NFR2: **Client-confirmation fidelity.** The bot sends one teleport ack per received position id: the latest id only, on the next tick, never from inside `send()`. A matching id with nothing pending disconnects the bot. It sends a player-loaded ack after join and after each respawn. (Constraint)
- NFR3: **Self-ticking body.** Each tick, the bot resets the listener's awaited position, sends pending acks, runs actions, steps the server's own physics along the movement heading, submits `Pos`/`PosRot` with the current look through `handleMovePlayer` (which fires `PlayerMoveEvent`), and flushes outbound. Refused moves are diagnosed through `PlayerFailMoveEvent`. (Constraint, spine tick diagram)
- NFR4: **Small, compile-checked NMS surface.** About 25 NMS types sit in 2-3 files of one internal package, `fr.nekotine.core.bot.internal`, with minimal reflection. NMS `BlockPos`/`Vec3` never leave that package. Points cross module boundaries as Bukkit `Location`. (Constraint, AD-3)
- NFR5: **One Minecraft version per release.** The `bots` dev bundle matches the host's dev bundle and the server's (Paper 26.2.build.65-beta today). There is no multi-version layer. `core` stays unpinned. (Constraint, AD-1)
- NFR6: **Java only**, no Kotlin. (Constraint)
- NFR7: **Sized for 2v2 to 3v3.** A game has at most 6 players: a lobby fill adds 1-5 bots, and an unattended run uses 4 bots typically and 6 at most. (Constraint)
- NFR8: **Main thread.** Controls, the tick event, roster changes, every `writeInbound` and every flush run on the main thread. The connection's `send()` only records state thread-safely. Only a pathfinder search may run off-thread. (AD-9, AD-7)
- NFR9: **Logging hygiene.** Library log lines start with the bot's name and carry no host or game concepts, with one stack trace per failure. The host adds game context in its own logs. (Constraint, AD-11)
- NFR10: **Determinism.** A scripted-bot test of a tool or phase gives the same result on 3 consecutive runs. A soak test with autonomous bots passes when the game reaches a result with no exception in the log. (Constraint)
- NFR11: **Verification placement.** Checks that need no human run in an integration suite on an in-process Paper server (paper-junit-server). NekotineCore tests the bots module through a tiny host plugin and its own paper-junit-server submodule, with no harness copy and no Vi6 dependency. Vi6 tests test Vi6 and use bots only as a driver. Checks that need a real player or client run on the dev server, driven by console command and checked in the log. (Constraint, AD-13)
- NFR12: **Own implementation.** The body is written from scratch, with no fork of or dependency on rs4t/egors-fakeplayer or similar plugins. Carpet features (chunk loading, auto-eat, follow, restart persistence) are not reproduced. Any snippet copied verbatim keeps its MIT notice, and shaded MIT dependencies (such as Pathetic) ship theirs. (Constraint)
- NFR13: **Repository policy.** `gradlew` builds green before a PR, commits follow Conventional Commits, and every NekotineCore or paper-junit-server change ships with its gitlink commit in the same Vi6Clean PR. Agents push branches and open PRs but never merge. (AGENTS.md, spine conventions)

### Additional Requirements

**Starter / build structure (AD-1, AD-2): first story of Epic 1.** The project is brownfield, so it needs no starter template, but the bots work starts with a repository migration:
- NekotineCore becomes a multi-module repository:
  - `core`: the existing Core code, Paper API only, no paperweight, unpinned;
  - `bots`: paperweight userdev, depends on `:core`, never the reverse;
  - `bots-test-host`: a tiny host plugin for the library's own integration tests;
  - `paper-junit-server`: moved from Vi6Clean into NekotineCore as a submodule.
- Vi6Clean consumes NekotineCore through one submodule plus `includeBuild`, replaces its `project(":NekotineCore")` reference, and reaches paper-junit-server through NekotineCore, keeping no copy of its own.
- Settled by the migration story: Gradle coordinates for `core`/`bots`, lockfiles (`--update-locks` for what changed), a shared version catalog, and Spotless across modules.
- Only the final plugin shades. `core` and `bots` publish plain jars. Vi6Clean shades exactly one copy of each and does its own relocation, so CommandAPI shading and relocation (to `fr.nekotine.core.commandapi` today) move from Core to Vi6. This relies on Paper-plugin classloader isolation.

**Bot body bring-up (verified by the ProtocolLib spike):**
- Build the pipeline on an unregistered `new EmbeddedChannel(false, false)` with `Connection.configureInMemoryPipeline(pipeline, SERVERBOUND)` and `connection.configurePacketHandler(pipeline)`, then `register()` it, so that `channelActive` sets `Connection.channel`. ProtocolLib skips an inactive channel.
- Before `placeNewPlayer`, replace `decoder` with `UnconfiguredPipelineHandler.Inbound` named `inbound_config`. Call `connection.setupOutboundProtocol(GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registryAccess)))`.
- Keep the raw `EmbeddedChannel` for `writeInbound`, because ProtocolLib replaces `Connection.channel` with a proxy. Call `flushOutbound()` on every bot tick and before closing.

**Library API and lifecycle:**
- Public `bots` types expose only Bukkit, Paper and `core` types. Packages: `fr.nekotine.core.bot` (public), `fr.nekotine.core.bot.internal` (NMS), `fr.nekotine.core.navigation` and `fr.nekotine.core.combat` (in `core`), and `fr.nekotine.vi6clean.bot` (Vi6). (AD-3, conventions)
- The roster is an `IPluginModule` in `bots`, resolved through `Ioc`, and resolved eagerly at host enable so the self-test runs. (AD-3, AD-10)
- Every exit takes one path: flush, close the connection, normal quit (`PlayerQuitEvent`), drop from the roster, then apply the wipe flags after the quit save. "Reset state" deletes `world/players/data/<uuid>.dat`, and "reset statistics" deletes the stats and advancements files. Spawn and remove requests made during the tick loop take effect at its end, and `unload()` removes every bot. (AD-10)
- Each host has its own roster, so two hosts on one server run separate rosters. (Constraint)

**Tick model and control priority (AD-5, amended 2026-11-05):**
- The roster is driven by Core's `TickingModule` `TickElapsedEvent`. It fires one per-bot Bukkit event per live bot on the main thread, in spawn order, after acks and before inputs, physics and flush.
- Controls set in the event apply that same tick, and controls set elsewhere apply on the next tick.
- Strategies run only inside the per-bot tick event.
- There is no ownership or claiming. When several writers set the same control group (movement, aim, hands) on a bot in one tick, a fixed priority order decides which write applies, whatever order the listeners ran in. For aim (head direction), the walker's write applies first and the melee controller's write overrides it, so a bot looks along its path unless it is fighting. A writer that sets nothing leaves the lower-priority write in place.

**Strategies (AD-6, AD-7, AD-8):**
- A pathfinder that searches off the main thread reads only a main-thread world snapshot, calls the caller's rule only on that snapshot, and completes the result on the main thread.
- A pathfinder may offer a Nekotine sub-interface with per-point hints. Walkers may read it but must work without it.
- The control port mirrors the movement, aim and hands groups and exposes location, velocity, on-ground and look as world-space values. Its exact method names are set by the first walker story. `bots` ships the bot adapter, which maps the port 1:1 onto AD-4.
- Keep-distance and strafing in melee are done by the walker following a moving goal.
- The pathfinder algorithm for Vi6 is chosen by the navigation story. The candidates are our own A*, a Pathetic adapter (`de.bsommerfeld.pathetic:engine` 5.5.2), a per-map graph, and vanilla through Paper's mob pathfinder.

**Vi6 integration:**
- Vi6 owns its commands, since `bots` ships none: add N with a team intent, list, remove, and start one unattended run. (AD-14)
- Brains read game state only through Vi6's public API and act only through bot controls and player commands. (AD-12)
- Assumptions to verify:
  - Vi6's join listeners (resource-pack send, lobby admission at `Vi6PhaseLobby`) accept a fake player unchanged.
  - The outbound packet sends from `PantsHandler` and `TrapWireHandler` to a bot's `send()` are safe.
- Test-environment changes: `test-bundle/server.properties` needs `spawn-protection=0` (it is 16 today) so that non-op bots can click near spawn. `max-players=20` is enough. (test-coverage-boundary)

**Documentation:**
- Rewrite `bots-adopter-guide.md` against the real, shipped API once the bots work is complete. The current guide was written from the design, before any code.

**Dev-server demonstration (success signal):**
1. Restart the dev server and see the self-test pass.
2. Run 4 bots unattended, then 6, to a recorded win and a clean log.
3. Have 1-3 bots complete a 2v2 in front of real players.
4. Have a second minigame spawn and drive a bot through the same API.

**Explicitly out of scope (deferred or non-goals):** a mob control adapter, an RL walker, the Paper 26.3 upgrade, inventory-click and drop controls, Maven publishing, `ClientTrackModule` fixes and bot checks against it (parked until after the bots work), entity interaction beyond attack and interact (the minimap ArmorStand), dialogs, F3 path debug, skins and chat, difficulty tiers, anti-cheat, network-client bots, and a unit-test suite.

### UX Design Requirements

None. There is no UX document, and player-facing bot UX is a non-goal (commands only).

### FR Coverage Map

- FR1: Epic 2 - Operator command adds N bots with a team intent
- FR2: Epic 2 - Operator command lists bots and their state
- FR3: Epic 2 - Operator command removes bots
- FR4: Epic 1 - Bot joins as a real fake player with an offline UUID
- FR5: Epic 2 - Bots get human parity in teams, phases, scoring and cleanup
- FR6: Epic 2 - Bots pick a team and ready up through commands, and the lobby advances
- FR7: Epic 3 - Bots drive one representative tool (Portable Teleporter); per-tool tests later
- FR8: Epic 1 - AD-4 control surface through real packets
- FR9: Epic 5 - Bots roam the map autonomously and respect entrance and exit rules
- FR10: Epic 5 - `core` pathfinder port returning `PathResult`
- FR11: Epic 5 - `core` walker strategy
- FR12: Epic 6 - `core` melee controller; Vi6 guards chase and cut off retreat, thieves flee
- FR13: Epic 4 - One command starts an unattended 4-bot or 6-bot run to a win
- FR14: Epic 4 - Repeatable runs from a clean roster
- FR15: Epic 1 - Reusable API with no NMS in hosts, proven by `bots-test-host` (see the scope decisions below)
- FR16: Epic 1 - Ephemeral bots, wipe flags, removal on unload
- FR17: Epic 1 - Startup self-test and loud self-disable
- FR18: Epic 1 (library side), Epic 2 (brain side) - Failure isolation
- FR19: Epic 3 - Vi6 test hooks (force teams and phase, short timers, reset)
- FR20: Epic 4 - Spawn-select and preparation-ready commands; bots end preparation early

### Scope Decisions (2026-11-05)

- **Vi6Clean is the main goal.** No second Nekotine minigame is planned. `bots-test-host` (a plugin with no NMS code of its own) is the reuse proof for FR15. The SPEC success signal "a second minigame spawns and drives a bot" waits until a real second minigame exists. Every library story still keeps the API host-agnostic (AD-3, AD-13) so that the library stays future-proof.
- **Tool-less bots are acceptable for now.** The Vi6 shop is an inventory menu, and the inventory-click control stays deferred (AD-4), so bots buy no tools. Epic 3 tests get their tools through a Vi6 test hook (AD-13), not through the shop.
- **Bots don't wait out preparation (FR20).** Vi6 gains two player commands: one selects a thief entrance by name, and one marks the player ready in preparation.
  - `/game ready` stays lobby-only, because extending it is too broad. Today, preparation readiness is only the "Prêt" inventory-menu toggle, and spawn choice is only the minimap ArmorStand interaction.
  - Bots use only the commands. The ArmorStand interaction stays out of scope, as the SPEC says.
- **The unattended run comes before navigation and melee.** A round can end without PvP. Infiltration's lost timer (`Vi6PhaseInfiltration.java:159-170`) also ends a round in which thieves never steal or move. Bots that stand still therefore complete a full round, and that run becomes the regression baseline. Navigation (Epic 5) and melee (Epic 6) each end by upgrading the run's brains.
- **One representative tool, not 37.** Epic 3 tests a single tool end to end. Automated tests for every tool are a later piece of work, started when the user asks. The library's own tests (Epic 1) already cover each input type in general.
- **Priority order instead of ownership (amends AD-5).** The user dropped the one-owner-per-group rule with its loud failure. Same-tick writes to a control group are resolved by priority: for aim, movement (the walker) first, then melee. `ARCHITECTURE-SPINE.md` AD-5 must be updated to match.
- **Vi6 PvP is one-sided.** Guards have weapons, and thieves usually don't fight back. CAP-8's "keeps distance, strafes" therefore becomes a configurable part of the generic `core` controller. For Vi6, guard bots close in as tightly as possible and cut off the thief's retreat, and thief bots flee rather than fight.
- **Short timers need a seam.** Phase durations are read from immutable config into field initializers (`Vi6PhasePreparation.java:112`, `Vi6PhaseInfiltration.java:56`), so Epic 3 opens with a phase-duration seam that leaves the human defaults unchanged.

## Epic List

### Epic 1: Any Nekotine plugin can spawn, drive and remove a bot
A plugin developer uses NekotineCore's API to spawn a real fake player, drive it with client-equivalent controls, and remove it cleanly, without writing any NMS code. The epic opens with the AD-1/AD-2 multi-module migration (`core`, `bots`, `bots-test-host`, paper-junit-server moved into NekotineCore, CommandAPI shading moved to Vi6). The migration has hard green-build gates: Vi6 `build` and `integrationTest` stay green, the jar holds exactly one `fr.nekotine.core`, and CommandAPI keeps its relocation. Next comes a walking-skeleton bot that only joins and leaves, then the controls, the tick event, the self-test and failure isolation. Library tests run from `bots-test-host`.
**FRs covered:** FR4, FR8, FR15, FR16, FR17, FR18 (library side)

### Epic 2: The operator fills a Vi6 lobby with bots
The operator adds, lists and removes bots on a live Vi6 server. With no human connected, bots pick their team and ready up through `/game team` and `/game ready`, and the lobby advances. Bots stand idle once the round starts; movement comes in Epic 4. The epic checks the join-path and outbound-send assumptions and sets `spawn-protection=0` in the test bundle.
**FRs covered:** FR1, FR2, FR3, FR5, FR6, FR18 (brain side)

### Epic 3: Vi6 tools and phases get regression tests driven by bots
A Vi6 test author forces teams and phase, shortens timers, grants tools, resets between tests, and uses scripted bots to drive one representative tool and the phase machine. The tests give the same result on 3 consecutive runs. The epic opens with a phase-duration seam, because durations are fixed in field initializers today, and that seam must leave the human defaults unchanged.
**FRs covered:** FR7, FR19

### Epic 4: A full Vi6 game runs with zero humans
One console command starts an unattended 4-bot or 6-bot run. In preparation, thief bots pick an entrance through the new spawn-select command, and every bot readies up through the new preparation-ready command, so the phase ends at once. Bots then stand idle, and the infiltration timer brings the round to a recorded result with a clean log. The run can be repeated from a clean roster. The epic covers the two preparation commands, the run command, the brains for each phase, cleanup between runs (using Epic 3's reset), a soak integration test on short timers, and the dev-server demo with 4 and then 6 bots.
**FRs covered:** FR13, FR14, FR20

### Epic 5: Bots move around the map on their own
A spawned bot travels between map areas for the whole round and respects Vi6's entrance and exit rules. The epic builds the `core` pathfinder port (the Vi6 algorithm is chosen in its first story), the control port and bot adapter, the vanilla-like walker, and Vi6's passability rules plus a roaming brain. Its last story upgrades the unattended run from idle bots to roaming ones, with the Epic 4 soak test still green.
**FRs covered:** FR9, FR10, FR11

### Epic 6: Bots fight at melee range
In Vi6's one-sided PvP, a guard bot chases a thief, stays as close as possible, cuts off its retreat, and lands hits within cooldown and reach. Thief bots flee instead of fighting. The epic builds a generic `core` melee controller (with a caller-set standoff distance and positioning policy, driving the walker's moving goal, with melee aim taking priority over the walker's), an integration test against a fleeing scripted bot, and the Vi6 guard-chase and thief-flee brains. Its last story adds the chases to the unattended run, with the soak test still green. A human 1v1 on the dev server is the acceptance gate.
**FRs covered:** FR12

### Epic 7: Bots are ready for real players and future adopters
1-3 bots complete a 2v2 in front of real players on the dev server, which closes the SPEC success signal for Vi6. The adopter guide is rewritten against the shipped API, so that a future Nekotine minigame can adopt bots.
**FRs covered:** none new; this epic delivers the closing success-signal demo and the adopter-guide requirement.

## Epic 1: Any Nekotine plugin can spawn, drive and remove a bot

A plugin developer uses NekotineCore's API to spawn a real fake player, drive it with client-equivalent controls, and remove it cleanly, without writing any NMS code. The work happens in the NekotineCore repository. Each story ships as a NekotineCore PR to `dev`, plus a Vi6Clean PR that bumps the gitlink.

### Story 1.1: NekotineCore splits into `core` and `bots` modules, and Vi6 shades both

**Implements:** AD-1, AD-2, NFR5, NFR13 (foundation for FR15)

As a Nekotine plugin developer,
I want NekotineCore to be a multi-module build with a Paper-API-only `core` and an NMS `bots` module,
So that a plugin that only uses Core never gets pinned to one Minecraft version, and bot code has a home.

**Acceptance Criteria:**

**Given** the NekotineCore repository
**When** the build is migrated
**Then** it has a `core` module that holds all of today's Core code, uses the Paper API only (no paperweight) and stays unpinned
**And** it has a `bots` module that applies paperweight userdev with the dev bundle Vi6 uses (`26.2.build.65-beta`), depends on `:core`, and holds a placeholder class under `fr.nekotine.core.bot`
**And** `core` has no dependency on `bots` (AD-1)

**Given** `core` and `bots`
**When** they are built
**Then** each produces a plain jar, and neither runs shadow (AD-2)

**Given** Vi6Clean consumes NekotineCore through its one submodule plus `includeBuild("NekotineCore")`
**When** Vi6's `build.gradle.kts` replaces `project(":NekotineCore")` with the new `core` and `bots` coordinates
**Then** Vi6 shades exactly one copy of each module and relocates CommandAPI to `fr.nekotine.core.commandapi` itself, because CommandAPI shading has moved from Core to Vi6
**And** `verifyShadowJarLayout` still passes and still forbids any relocated copy of Core, and the dependency report shows `core` resolved once

**Given** dependency locking
**When** the migration lands
**Then** lockfiles are updated only for what changed (`--update-locks <group>:*`, not a full `--write-locks`), and the lockfile diff is reviewed in the PR
**And** Spotless runs on every NekotineCore module and the build fails on wildcard imports, as it does in Vi6

**Given** the migrated build
**When** bare `gradlew`, then `gradlew build`, then `gradlew integrationTest` run in Vi6Clean
**Then** all three are green, `Vi6SmokeIT`, `Vi6MapIT` and `Vi6ConfigIT` pass, and the jar runs on the dev server unchanged for humans
**And** the Vi6Clean PR commits the NekotineCore gitlink (NFR13)

### Story 1.2: NekotineCore owns paper-junit-server and boots its own test host

**Implements:** AD-1, AD-13, NFR11

As a NekotineCore maintainer,
I want the in-process Paper test harness to live in NekotineCore, with a tiny host plugin booted by it,
So that the bots library can be tested on a real server without depending on Vi6 (AD-1, AD-13).

**Acceptance Criteria:**

**Given** paper-junit-server is a submodule of Vi6Clean today
**When** it moves
**Then** it is a submodule of NekotineCore, Vi6Clean reaches it through NekotineCore (`pluginManagement` `includeBuild` of the nested path), and Vi6Clean keeps no copy or submodule of its own
**And** `gradlew integrationTest` in Vi6Clean is still green
**And** both `AGENTS.md` files (Vi6Clean and NekotineCore) describe the new layout, and the Vi6Clean README says that a clone needs `git submodule update --init --recursive`, because paper-junit-server is now a submodule inside the NekotineCore submodule

**Given** NekotineCore
**When** the `bots-test-host` module is built
**Then** it is a Paper plugin (`paper-plugin.yml`) that shades `core` and `bots` and contains no NMS code of its own
**And** it has its own test-server bundle folder, gitignored except a README that lists what goes in (ProtocolLib, `spawn-protection=0`), on the model of Vi6's `test-bundle/`

**Given** NekotineCore's integration suite
**When** `gradlew integrationTest` runs in NekotineCore
**Then** one in-process Paper server boots with `bots-test-host` and ProtocolLib loaded, and a smoke test asserts the host plugin is enabled

### Story 1.3: A bot joins and leaves as a real player

**Implements:** FR4, FR8 (lifecycle), NFR1, NFR2, NFR3, NFR4, NFR8, NFR12

As a plugin developer,
I want to spawn a bot by name at a location and remove it through the roster,
So that I have a player-shaped stand-in that the server and my plugin treat as a real player.

**Acceptance Criteria:**

**Given** a host plugin resolves the roster through `Ioc.resolve(...)` (the roster is an `IPluginModule` in `bots`)
**When** it spawns a bot named `Bot_Alice` at a location
**Then** the bot joins through `PlayerList.placeNewPlayer` on a fake `Connection` backed by an `EmbeddedChannel`, using the spike's pipeline recipe:
  - the channel is created unregistered, the in-memory pipeline and packet handler are configured, and the channel is registered;
  - before join, `decoder` is replaced with `inbound_config`;
  - `setupOutboundProtocol` is called with the clientbound template.

**And** `PlayerJoinEvent` fires, the bot is in `getOnlinePlayers()`, `isOnline()` is true, `Bukkit.getPlayer(uuid)` resolves to it, and its UUID equals the offline UUID derived from `Bot_Alice`
**And** the API returns a Bukkit `Player` handle, and no public `bots` type exposes an NMS type (AD-3)

**Given** a spawned bot
**When** ProtocolLib is loaded
**Then** ProtocolLib has injected the bot's channel, and a test `PacketAdapter` receives a packet the bot writes inbound, with `event.getPlayer()` matching the bot's UUID

**Given** live bots
**When** the roster is ticked from `TickElapsedEvent`
**Then** each bot resets the listener's awaited position, sends at most one teleport ack per tick (for the latest position id recorded by `send()`, never from inside `send()`), sends a player-loaded ack after join, and calls `flushOutbound()`
**And** `send()` only records state thread-safely, and every `writeInbound` and flush happens on the main thread (AD-9)
**And** a bot left alone for 200 ticks stays connected and is not kicked

**Given** the roster
**When** the host lists bots
**Then** it gets each bot's name, `Player` handle and state

**Given** a spawned bot
**When** the host removes it
**Then** outbound writes are flushed, the connection is closed, `PlayerQuitEvent` fires, the bot leaves the roster, and the log shows no `StacklessClosedChannelException` warning

**Given** NMS and reflective code
**When** the story is reviewed
**Then** it sits only in `fr.nekotine.core.bot.internal`, NMS `BlockPos`/`Vec3` never leave it, and no code or dependency comes from fake-player plugins (NFR4, NFR12)

### Story 1.4: Bots tick in a per-bot event, are ephemeral and fail in isolation

**Implements:** FR16, FR18 (library side), AD-5, AD-10, AD-11, NFR9

As a plugin developer,
I want one main-thread tick event per bot, guaranteed cleanup and isolated failures,
So that my brain logic runs at the right moment, bots never outlive my plugin, and one broken bot never floods the log or stops the others.

**Acceptance Criteria:**

**Given** live bots
**When** a `TickElapsedEvent` fires
**Then** the roster fires one per-bot Bukkit event per live bot, on the main thread, in spawn order, after pending acks are sent and before inputs are applied and physics steps (AD-5)

**Given** a listener spawns or removes a bot during the per-bot tick loop
**When** the loop runs
**Then** the change takes effect only after the loop ends, and no bot is changed while it is being ticked (AD-10)

**Given** a bot leaves through any exit (roster removal, `unload()`, kick, or a server-side disconnect)
**When** it leaves
**Then** it goes through the single exit path: flush, close, normal quit, drop from the roster, then the wipe flags

**Given** a bot spawned with default flags
**When** it is removed and its quit save has completed
**Then** `world/players/data/<uuid>.dat` is deleted, and so are its stats and advancements files

**Given** a bot spawned with "reset state" or "reset statistics" turned off
**When** it is removed
**Then** the matching files are kept

**Given** live bots
**When** the host plugin disables
**Then** the roster's `unload()` removes every bot, and on the next boot no bot exists (nothing restores bots)

**Given** an exception in the library's own per-bot tick work
**When** it is thrown
**Then** it is logged exactly once, as a line that starts with the bot's name followed by the stack trace
**And** the bot's controls go neutral, it stops ticking but stays connected, the roster lists it as failed, and other bots keep ticking (AD-11)
**And** an exception thrown by a host listener of the per-bot event is not caught by the library

**Given** library log lines
**When** they are reviewed
**Then** they carry bot context only, never host game concepts (NFR9)

### Story 1.5: A startup self-test turns bots off loudly when NMS breaks

**Implements:** FR17, AD-3

As a server operator,
I want the bots module to check its NMS accessors when the host enables and to turn itself off if any of them is broken,
So that a Paper update that breaks the internals leaves the game running for humans, with a clear message instead of strange behaviour.

**Acceptance Criteria:**

**Given** a host plugin enables
**When** it resolves the roster eagerly at enable (AD-3), because `ModuleManager` loads modules lazily and only logs constructor errors
**Then** the roster runs a self-test that exercises every NMS or reflective accessor in `bot.internal`, and logs one line saying it passed

**Given** an accessor fails (simulated in a test through an internal fault-injection seam)
**When** the self-test runs
**Then** the module logs a loud error naming the failed accessor and disables itself
**And** every bot API call reports "bots unavailable" instead of throwing an NMS error
**And** the host plugin and the server keep running

### Story 1.6: Bots walk, look, jump, sneak and sprint through real packets

**Implements:** FR8 (movement, aim), NFR1, NFR3

As a plugin developer,
I want movement and aim controls that the server receives exactly as a client's input,
So that my game's `PlayerMoveEvent`, sneak and sprint handlers react to a bot as they would to a human.

**Acceptance Criteria:**

**Given** a bot
**When** the host sets forward/back, strafe, jump, sneak, sprint, a movement heading and a look (yaw/pitch)
**Then** keys, sneak/sprint, heading and look persist until changed, and the heading defaults to the look yaw (AD-4)
**And** changes made inside the per-bot event apply that same tick, and changes made elsewhere apply on the bot's next tick (AD-5)

**Given** sneak or sprint changes
**When** the bot ticks
**Then** a real serverbound player-input packet is written inbound only on change, reaches `handlePlayerInput`, and `PlayerToggleSneakEvent` / `PlayerToggleSprintEvent` fire

**Given** movement keys are held
**When** the bot ticks
**Then** the body computes its next position with the server's own physics along the movement heading, and submits it through `handleMovePlayer` as `Pos`/`PosRot` carrying the current look
**And** `PlayerMoveEvent` fires, and the bot walks onto a block, jumps up a 1-block step and falls down a drop on a flat test structure
**And** there is no `setPosition` or teleport control in the API

**Given** the server refuses a move
**When** `PlayerFailMoveEvent` fires for the bot
**Then** the library logs a diagnosis starting with the bot's name, and a test on a flat, legal walk shows zero refusals

**Given** any input
**When** it is sent
**Then** it is written inbound into the fake connection's channel, a ProtocolLib inbound listener sees it, and no Bukkit event is fabricated and no direct game-mode, slot, sneak or position call is made (NFR1)

### Story 1.7: Bots use their hands, send commands and respawn

**Implements:** FR8 (hands, other), FR15

As a plugin developer,
I want hand, command and respawn controls,
So that a bot can use items, click blocks, attack, run my plugin's player commands and come back after death, all through my normal handlers.

**Acceptance Criteria:**

**Given** a bot with items in its hotbar
**When** the host selects a hotbar slot
**Then** a real carried-item packet reaches `handleSetCarriedItem`, `PlayerItemHeldEvent` fires, and the slot persists until changed

**Given** a bot
**When** the host triggers use item, use item on block + face, left-click/start-break, attack entity, interact entity or swap hands
**Then** each fires once, in the next apply step, as a real packet to its `handleX`
**And** `PlayerInteractEvent` fires with `RIGHT_CLICK_*` or `LEFT_CLICK_*` as expected
**And** a block click on a real block within reach reports that block from `getClickedBlock()`
**And** `EntityDamageByEntityEvent` fires on a target in reach when the bot attacks

**Given** a bot
**When** the host sends a command string
**Then** it travels as a real chat-command packet to `handleChatCommand`, `PlayerCommandPreprocessEvent` fires, and a command the test host registers runs with the bot as its sender

**Given** a dead bot
**When** the host triggers respawn
**Then** the bot respawns through the real respawn packet and sends a player-loaded ack after respawning

**Given** `bots-test-host`, a plugin with no NMS code of its own
**When** its integration test spawns a bot, makes it right-click while holding an item and removes it
**Then** the host's `PlayerInteractEvent` listener sees that item, and the bot is gone afterwards (FR15)
**And** there is no dialog, inventory-click or drop control in the API (deferred per AD-4)

## Epic 2: The operator fills a Vi6 lobby with bots

The operator adds, lists and removes bots on a live Vi6 server. Bots pick their team and ready up through the same commands a player sends, and the lobby advances. Bots stand idle once the round starts. Vi6 bot code lives in `fr.nekotine.vi6clean.bot`.

### Story 2.1: A bot joins the Vi6 lobby like a player

**Implements:** FR4, FR5, AD-3, AD-12

As the operator,
I want Vi6 to run the bots library and admit a bot through its normal join path,
So that bots exist on the Vi6 server without any change to game logic.

**Acceptance Criteria:**

**Given** Vi6Clean depends on and shades `bots`
**When** `Vi6Main` enables
**Then** it resolves the roster eagerly, and the log shows the self-test result (AD-3)
**And** if the self-test fails, Vi6 still enables, humans can play, and bot work reports "bots unavailable"

**Given** a running Vi6 server in the lobby phase
**When** an integration test spawns a bot through the roster
**Then** `Vi6PhaseLobby.onPlayerJoined` adds it to the game, and `Vi6Game.getPlayerList()` contains it
**And** Vi6's join listeners (resource-pack send, the lobby dialog) run without an exception, which confirms the SPEC assumption that join listeners accept a fake player unchanged

**Given** a bot in the game
**When** it is removed through the roster
**Then** `Vi6PhaseGlobal.onPlayerLeft` handles its quit as for a human, and it is no longer in the player list or on the scoreboards

**Given** `test-bundle/`
**When** this story lands
**Then** `test-bundle/README.md` requires `spawn-protection=0`, and the local bundle is updated to match

**Given** all of `src/main/java` outside `fr.nekotine.vi6clean.bot`
**When** the story is reviewed
**Then** no file checks whether a player is a bot (AD-12)

### Story 2.2: The operator adds, lists and removes bots with commands

**Implements:** FR1, FR2, FR3, AD-14, NFR7

As the operator,
I want console and op commands to add N bots with a team intent, list them, and remove one or all,
So that I can fill or clear a lobby without restarting the server.

**Acceptance Criteria:**

**Given** a running Vi6 server
**When** the operator runs the add command with a count N and a team intent (`guard`, `thief` or `any`)
**Then** N bots join with unique names (no clash with online players or existing bots), each carrying its team intent, and no restart happens (FR1, AD-14)

**Given** live bots
**When** the operator runs the list command
**Then** it shows each bot's name, team intent, current Vi6 team and state (live, or failed per AD-11) (FR2)

**Given** live bots
**When** the operator runs remove with a bot name, or with `all`
**Then** the matching bots leave through the roster's single exit path, and the list command no longer shows them (FR3)

**Given** the commands
**When** a non-op player runs them
**Then** they are refused

**Given** an add request that would push the game beyond 6 players (NFR7)
**When** the operator runs it
**Then** it is refused with a message that says why

**Given** the bots module is unavailable
**When** any bot command runs
**Then** it answers "bots unavailable" and changes nothing

### Story 2.3: Bots pick their team and ready up through player commands

**Implements:** FR5, FR6, FR18 (brain side), AD-12, NFR10

As the operator,
I want bots in the lobby to join their intended team and mark themselves ready with the commands a player uses,
So that the lobby advances with no human connected, and the game treats bots exactly like players.

**Acceptance Criteria:**

**Given** a bot spawned with team intent `guard` or `thief`
**When** its Vi6 lobby brain runs in the per-bot tick event
**Then** it sends `/game team guard` or `/game team thief` as a real chat command, followed by `/game ready` (FR6, AD-12)

**Given** a bot spawned with team intent `any`
**When** its brain picks a team
**Then** it reads team sizes through Vi6's public API (`Vi6Game.getGuards()` / `getThiefs()`) and joins the smaller team, or `guard` on a tie

**Given** the intent was handed to the brain at spawn
**When** the bot is added
**Then** nothing but the brain's commands assigns its team, and no code calls `addPlayerInGuards` / `addPlayerInThiefs` for a bot

**Given** 4 bots (intent `any`) and no human
**When** an integration test starts the game
**Then** the teams end up 2v2, every bot is ready, and the phase machine leaves `Vi6PhaseLobby` for `Vi6PhasePreparation` (FR5, FR6)

**Given** a brain throws an exception
**When** its tick listener runs
**Then** the brain catches it, logs one line with the bot's name, its team and the current Vi6 phase, and stops that brain, while the bot stays connected (FR18 brain side, AD-12)

**Given** the same integration test
**When** it runs 3 times in a row
**Then** it gives the same result each time (NFR10)

### Story 2.4: Bots fill a lobby with humans

**Implements:** FR1, FR5, NFR11

As the operator,
I want to add 1-3 bots next to human players and start a 2v2,
So that a round can start when not enough friends are online.

**Acceptance Criteria:**

**Given** the dev server with 1 human in the lobby
**When** the operator adds 3 bots with intent `any`, and the human picks a team and readies up
**Then** the teams balance to 2v2 and the lobby advances to preparation

**Given** the round has started
**When** bots stand idle
**Then** no exception appears in the log, and the operator can remove the bots mid-round, which the game handles as players quitting

**Given** this check needs a real client
**When** it is run
**Then** it runs on the dev server, and its result is recorded in the story's completion notes (NFR11)

## Epic 3: Vi6 tools and phases get regression tests driven by bots

A Vi6 test author sets a game up quickly (teams, phase, short timers, tools), drives scripted bots through the real inputs, and asserts Vi6's behaviour. Tests live in `src/integrationTest/java` and use bots only as a driver (AD-13). One representative tool proves that the tool path works; per-tool tests for the other tools come later, on the user's call.

### Story 3.1: Phase durations can be shortened without changing the game for humans

**Implements:** FR19, AD-13

As a Vi6 test author,
I want a seam that overrides phase durations at runtime,
So that a full round runs in seconds in tests instead of real minutes.

**Acceptance Criteria:**

**Given** phase durations are read from immutable config into field initializers today (`Vi6PhasePreparation.java:112`, `Vi6PhaseInfiltration.java:56`, plus any others found)
**When** the seam is added
**Then** every phase reads its durations through one Vi6 point, which defaults to the configured values, and tests can override it before a phase starts
**And** without an override, durations match `config.yml` exactly, and `Vi6ConfigIT` stays green

**Given** a test overrides the preparation and infiltration lost durations to a few seconds
**When** a game runs through those phases
**Then** each phase ends on the overridden timer

**Given** the seam
**When** the story is reviewed
**Then** it carries no bot concept, and no brain calls it (AD-13)

### Story 3.2: Vi6 test hooks set a game up and reset it between tests

**Implements:** FR19, AD-13, NFR10

As a Vi6 test author,
I want public test hooks to force teams and phase, grant tools, place players, and reset the game,
So that each test starts from a known state, and tests don't leak into each other.

**Acceptance Criteria:**

**Given** the hooks are public Vi6 methods in a test-support class, which settles the spine's "commands vs public methods" deferral for tests
**When** a test calls them
**Then** it can do each of these:
  - put given players on given teams;
  - move the phase machine to a given phase with the state that phase needs (map loaded, thieves given a spawn);
  - give a player a given tool as the registered stack;
  - place a player at a location with Bukkit `teleport`, which the bot acks like a client;
  - force which KoTHs spawn in-map (or inject the random source behind `Vi6PhaseInMap`'s shuffle and probability roll), so that KoTH tests are deterministic.

**And** the hooks depend only on Vi6 and the bot API (AD-13)

**Given** a test has finished, whatever its state
**When** the reset hook runs
**Then** every bot is removed through the roster, the game is back in the lobby with empty teams, the scoreboards have no leftover entries, timer and KoTH overrides are cleared, and no bot player data is left on disk
**And** the reset logic lives in Vi6's main source from the start (the hook only calls it), so that Story 4.4's run command can reuse it unchanged

**Given** two tests that use the hooks
**When** they run in either order
**Then** each passes, and each gives the same result on 3 consecutive runs (NFR10)

**Given** `PhaseMachine.isRunning()` stays false after the first `goTo` (a deferred NekotineCore bug)
**When** the hooks check the phase
**Then** they use `getPhase(...)`, as `Vi6SmokeIT` does

### Story 3.3: Bots drive a representative tool, the Portable Teleporter

**Implements:** FR7, NFR10

As a Vi6 test author,
I want one tool tested end to end with a bot,
So that the whole path (test hooks, bot inputs, tool handler, observable effect) is proven before per-tool tests are written.

**Acceptance Criteria:**

**Given** the Portable Teleporter, chosen because one tool exercises left-click (`HIT_ANY`), sneak, `PlayerMoveEvent` and a server-side teleport that the bot must ack
**When** a test gives a bot the tool as the registered stack (through the hooks) and the bot left-clicks
**Then** a pad is placed, and the tool's charge display drops by one

**Given** a placed pad that the bot aims at
**When** the bot sneaks and left-clicks
**Then** the pad is picked up, and the charge display goes back up

**Given** two placed pads
**When** the bot triggers a teleport between them
**Then** `onPlayerMove` holds the bot in place while it is teleporting, the bot is moved to the target pad, its teleport ack is accepted, and it is not kicked

**Given** a tool that sends Vi6 outbound packets (this tool, or `PantsHandler` / `TrapWireHandler` if it sends none)
**When** a bot is the receiver
**Then** the packets reach the bot's `send()` with no exception, which confirms the SPEC assumption

**Given** the test
**When** it runs 3 times in a row
**Then** it gives the same result each time (NFR10)

### Story 3.4: Bots drive the phase machine, KoTH capture and scoring

**Implements:** FR5, FR19, NFR10

As a Vi6 test author,
I want tests in which bots go through the phases, capture a KoTH and steal an artefact,
So that phase transitions, win conditions and scoring are covered.

**Acceptance Criteria:**

**Given** short timers and 2 bot guards and 2 bot thieves set up through the hooks
**When** the game starts from the lobby with no further hook calls
**Then** it goes through lobby → preparation → in-map → infiltration, ends on the infiltration timer, and returns to the lobby, with the result in the log and no exception

**Given** a KoTH forced to spawn through the hooks, and a bot placed in its zone during in-map
**When** it stays there for the capture time
**Then** the KoTH is captured by its team

**Given** a thief bot that can capture artefacts, placed in an artefact's zone, with no guard inside
**When** it stands there until the capture progress reaches `CAPTURE_AMOUNT_NEEDED` (`Artefact.java`), which is how a human steals
**Then** `ArtefactStealEvent` fires, the scoreboards show the artefact as stolen, and the infiltration lost timer restarts

**Given** a thief bot that steals and then reaches an exit
**When** it leaves the map
**Then** the score records the escape, and the game ends when every thief has left

**Given** every test in this epic
**When** the suite runs 3 times in a row
**Then** each test gives the same result each time (NFR10)

## Epic 4: A full Vi6 game runs with zero humans

One console command starts an unattended run of 4 or 6 bots. The bots clear the lobby, pick their entrances and ready up in preparation through player commands, and then stand idle. The infiltration lost timer brings the round to a recorded result. The run is repeatable from a clean roster, and it becomes the regression baseline for Epics 5 and 6.

### Story 4.1: Players can pick a thief entrance with a command

**Implements:** FR20

As a Vi6 player (human or bot),
I want a command that selects my thief entrance by name during preparation,
So that I can choose an entrance without clicking the minimap ArmorStand.

**Acceptance Criteria:**

**Given** the preparation phase and a player on the thief team
**When** the player runs the spawn-select command with an entrance name from the current map's thief spawns
**Then** their `PreparationPhasePlayerWrapper` selected spawn is set, exactly as clicking that entrance's minimap ArmorStand would set it, and they get the same confirmation message

**Given** the command
**When** a player types its argument
**Then** it suggests the current map's entrance names

**Given** a guard, a phase other than preparation, or an unknown entrance name
**When** the command runs
**Then** it is refused with a message, and nothing changes

**Given** the existing minimap ArmorStand selection
**When** this story lands
**Then** it still works for humans, and the random-spawn fallback still applies when nothing was selected

### Story 4.2: Players can mark themselves ready in preparation with a command

**Implements:** FR20

As a Vi6 player (human or bot),
I want a preparation-ready command that is separate from the lobby's `/game ready`,
So that I can end preparation early without the inventory menu.

**Acceptance Criteria:**

**Given** the preparation phase
**When** a player runs the preparation-ready command
**Then** their `PreparationPhasePlayerWrapper` ready state is set through `setReadyForNextPhase`, the "Prêt" menu toggle shows the same state, and `checkForCompletion()` runs

**Given** every player in the game is ready
**When** the last one runs the command
**Then** preparation completes at once, without waiting for its timer

**Given** `/game ready`
**When** this story lands
**Then** it still affects only the lobby (decided 2026-11-05)

**Given** a phase other than preparation
**When** the preparation-ready command runs
**Then** it is refused with a message

### Story 4.3: Bot brains get through preparation and stay idle in-map

**Implements:** FR20, FR18 (brain side), AD-12, NFR10

As the operator,
I want bot brains that select an entrance and ready up in preparation, then do nothing harmful in-map,
So that a bot-only game reaches the in-map and infiltration phases on its own.

**Acceptance Criteria:**

**Given** a thief bot in preparation
**When** its brain runs in the per-bot tick event
**Then** it reads the entrance names through Vi6's public API, sends the spawn-select command for one of them, and then sends the preparation-ready command (FR20, AD-12)
**And** every random choice a brain makes (here, the entrance) comes from one seedable random source per run, which tests pin, so that brain-driven tests give the same result on 3 consecutive runs (NFR10)

**Given** a guard bot in preparation
**When** its brain runs
**Then** it sends the preparation-ready command

**Given** 2 bot guards and 2 bot thieves with no human
**When** preparation starts
**Then** it completes within a few seconds, and each thief enters through the entrance it selected

**Given** the in-map and infiltration phases
**When** the brains run
**Then** they send no input, and the bots stand where they spawned

**Given** a brain exception in any phase
**When** it is thrown
**Then** the brain logs it once with the bot's name, team and phase, and stops, as in Story 2.3

**Given** the brain code
**When** it is reviewed
**Then** it lives in `fr.nekotine.vi6clean.bot`, uses only the Vi6 public API and bot controls, and never calls the test hooks (AD-12, AD-13)

### Story 4.4: The operator starts an unattended run with one command

**Implements:** FR13, FR14, AD-14

As the operator,
I want one console command that clears any leftover bots, adds 4 or 6 bots and starts the game,
So that I can run a full bot-only game whenever I want, and run it again.

**Acceptance Criteria:**

**Given** a running Vi6 server in the lobby
**When** the operator runs the run command with a count of 4 or 6
**Then** it first resets: leftover bots are removed through the roster, scoreboards hold no leftover entries, and no bot player data is left
**And** it adds the bots with intent `any`, and the game starts through the same path as `/game lobby` (`Vi6Game.start()`) (FR13, AD-14)

**Given** the reset logic
**When** it is implemented
**Then** it lives in Vi6's main source and is shared with the Story 3.2 test reset hook, so there is no second copy

**Given** a game past the lobby, or a count other than 4 or 6
**When** the run command is issued
**Then** it is refused with a message

**Given** a run reaches its result
**When** the game returns to the lobby
**Then** the log shows one summary line from Vi6 (winner, artefacts stolen and escaped, duration), and no exception

**Given** a finished run
**When** the operator issues the run command again
**Then** the second run starts from a clean roster and also reaches a result (FR14)

### Story 4.5: A soak test runs 4-bot and 6-bot games to a result

**Implements:** FR13, FR14, NFR10

As a Vi6 test author,
I want an integration test that plays complete bot-only games on short timers,
So that any regression that stops a round from finishing is caught automatically.

**Acceptance Criteria:**

**Given** short timers from Story 3.1
**When** the soak test runs a 4-bot game and then a 6-bot game in the same server boot, through the run command's code path
**Then** each game goes through lobby → preparation → in-map → infiltration to a result, and the test asserts the summary line and the absence of any logged exception (NFR10)

**Given** the second game in the boot
**When** it starts
**Then** no bot, scoreboard entry or bot player file is left over from the first game (FR14)

**Given** the soak test
**When** it runs 3 times in a row
**Then** it passes each time

### Story 4.6: Unattended runs work on the dev server

**Implements:** FR13, FR14, NFR11

As the operator,
I want to see unattended runs succeed on the real dev server,
So that I know the success signal holds outside the test harness.

**Acceptance Criteria:**

**Given** the dev server restarted with the new jar and no human connected
**When** the operator starts a 4-bot run and then a 6-bot run from the console, with real timers
**Then** each run reaches a recorded result, and the log shows the self-test pass and no exception

**Given** both runs are done
**When** the operator runs a third one
**Then** it starts clean and reaches a result

**Given** these checks need the dev server
**When** they are run
**Then** their results are summarized in a Markdown note under `_bmad-output/`, with no raw log committed (AGENTS.md)

## Epic 5: Bots move around the map on their own

A spawned bot travels between map areas for the whole round and respects the map's entrance and exit rules. The pathfinder, walker and control port are reusable `core` strategies, so mobs can use them later (AD-6, AD-7, AD-8). Vi6 supplies only its rules and brains. The library stories are tested in NekotineCore through `bots-test-host`, and the Vi6 stories in Vi6's suite (AD-13).

### Story 5.1: A control port lets strategies drive a body, with a priority order per control group

**Implements:** AD-5 (amended), AD-8 (foundation for FR9, FR11, FR12)

As a plugin developer,
I want a body-agnostic control port with a bot adapter and a fixed priority between strategies that set the same controls,
So that walkers and fighters drive bots now, and mobs later, without jittering over the same controls.

**Acceptance Criteria:**

**Given** `fr.nekotine.core.navigation` in `core`
**When** the port is defined
**Then** it mirrors AD-4's movement, aim and hands groups, exposes location, velocity, on-ground and look as world-space Bukkit values, and uses Paper API types only
**And** this story fixes the method names, which the spine deferred to the first walker story

**Given** `bots`
**When** it ships the bot adapter
**Then** the adapter maps the port 1:1 onto the bot controls, with no extra behaviour (AD-8)

**Given** a bot, and several writers (brain, walker, melee controller) that set the same control group in one tick
**When** the controls are applied
**Then** a fixed priority order decides which write applies, whatever order the listeners ran in (AD-5, amended)
**And** for aim, the walker's write applies first and the melee controller's write overrides it
**And** a writer that sets nothing in a tick leaves the lower-priority write in place, so a bot looks along its path again as soon as melee stops writing aim
**And** there is no claim, release or ownership error

**Given** the brains from Epics 2-4, which write controls directly
**When** this story lands
**Then** they keep working unchanged, and the Epic 4 soak test is still green

**Given** a strategy
**When** it runs
**Then** it runs only from inside the per-bot tick event, never from its own `TickElapsedEvent` listener (AD-5)

### Story 5.2: A pathfinder port, with an implementation chosen for Vi6

**Implements:** FR10, AD-6, AD-7, NFR8, NFR12

As a plugin developer,
I want a pathfinder interface that returns Paper's `PathResult` and takes my passability rule,
So that I can find paths with game rules applied, and swap the algorithm without changing callers.

**Acceptance Criteria:**

**Given** `core`
**When** the pathfinder port is defined
**Then** `find(from, to, rule)` returns `com.destroystokyo.paper.entity.Pathfinder.PathResult`, and the caller supplies the passability and cost rule for each search (AD-6, AD-7)
**And** a pathfinder may also implement a Nekotine sub-interface with per-point hints

**Given** the candidates (our own A*, a Pathetic adapter, a per-map graph, vanilla through Paper's mob pathfinder)
**When** the story picks one for Vi6
**Then** the choice and the reasons (path quality on a Vi6 map, speed for 6 bots, licence) are recorded in the story's completion notes, and the chosen implementation ships
**And** if Pathetic is shaded, its MIT notice ships with the host jar (NFR12)

**Given** an implementation that searches off the main thread
**When** it searches
**Then** it reads the world only through a snapshot taken on the main thread, calls the caller's rule only on that snapshot, and completes the result on the main thread (AD-7, AD-9)

**Given** a test structure in `bots-test-host`'s world, with walls, a 1-block step, a drop and a door
**When** a path is requested around the walls
**Then** a valid `PathResult` is returned, and a rule that forbids a region makes the path avoid it

### Story 5.3: A vanilla-like walker follows any `PathResult`

**Implements:** FR11, AD-6, AD-8, NFR10

As a plugin developer,
I want a walker strategy that drives a body along a path,
So that my bot (or later a mob) reaches its goal over steps, drops and doors.

**Acceptance Criteria:**

**Given** a body through the control port, and any `PathResult` (including one from a vanilla mob's pathfinder)
**When** the walker follows it
**Then** it writes movement, and aim along the path at walker priority, keeps its own progress cursor, and never modifies the path (AD-6)
**And** it infers each move (step, jump, drop, door) from the point geometry and the world, and uses per-point hints only when they are present

**Given** the test structure from Story 5.2
**When** a bot walks the path
**Then** it reaches the goal, and the walker reports arrival
**And** no `PlayerFailMoveEvent` refusal is logged

**Given** the bot is blocked (the path is obstructed after planning)
**When** it makes no progress for a set time
**Then** the walker reports "stuck", releases the movement controls to neutral, and leaves the decision to the caller

**Given** a goal that moves
**When** the caller updates it
**Then** the walker follows the new goal without a restart, which Epic 6 uses for keep-distance and strafing (AD-8)

**Given** the walker test
**When** it runs 3 times in a row
**Then** it gives the same result each time (NFR10)

### Story 5.4: Vi6 passability rules respect entrances, exits and gates

**Implements:** FR9 (entrance and exit rules), AD-7

As a Vi6 developer,
I want a Vi6 passability rule built from the current map's data,
So that bots find paths that respect the same rules the map enforces on humans.

**Acceptance Criteria:**

**Given** the loaded `Vi6Map` (`getEntrances()` with trigger and blocking boxes, `getExits()`, `getGates()`, `getRoomCaptors()`)
**When** a rule is built for a guard
**Then** it never routes through an exit or an entrance blocking box

**Given** a thief that has not entered yet
**When** a rule is built for it
**Then** it routes only through the thief's selected entrance

**Given** a thief inside the map
**When** a rule is built for it
**Then** it may route to an exit only when the caller asks to leave

**Given** the rule
**When** it is reviewed
**Then** it lives in `fr.nekotine.vi6clean.bot`, reads map data through Vi6's public API, and the pathfinder knows nothing about Vi6 (AD-7)

**Given** a Vi6 integration test on a real map
**When** a guard bot and a thief bot request paths across the map
**Then** each path respects its rule

### Story 5.5: Vi6 bots roam between map areas for the whole round

**Implements:** FR9, FR18 (brain side), AD-12

As the operator,
I want guard and thief bots to walk between map areas on their own,
So that a bot-filled round is watchable.

**Acceptance Criteria:**

**Given** the in-map and infiltration phases
**When** a guard bot's brain runs
**Then** it picks map areas (rooms, artefacts, KoTHs) as goals, using the seedable random source from Story 4.3, walks to each through the walker and the Vi6 rule, and picks a new goal on arrival or when stuck

**Given** a thief bot
**When** infiltration starts
**Then** it walks from its spawn through its selected entrance into the map, and then roams the same way

**Given** a 2v2 bot game on real timers on a Vi6 map
**When** it runs
**Then** every bot keeps travelling between areas until the round ends, and no bot stays stuck for more than a set time without picking a new goal (CAP-4)

**Given** a brain or walker failure
**When** it happens
**Then** the brain logs it once with bot and game context and stops, and the bot stays connected (AD-11, AD-12)

### Story 5.6: The unattended run uses roaming bots

**Implements:** FR9, FR13, NFR10

As the operator,
I want the unattended run to use the roaming brains,
So that bot-only games show real movement and still finish.

**Acceptance Criteria:**

**Given** the run command from Story 4.4
**When** it starts a 4-bot or 6-bot run
**Then** in-map and infiltration use the roaming brains instead of idle ones

**Given** a thief bot that reaches an artefact while roaming
**When** it is next to the artefact
**Then** it stays in the artefact's zone until the capture completes (the way a human steals), and gives up if a guard enters the zone. After stealing, it walks to an exit to leave

**Given** the soak test from Story 4.5
**When** it runs with roaming brains on short timers
**Then** it still passes 3 times in a row, with a result and no exception (NFR10)

**Given** the dev server
**When** the operator runs a 4-bot and then a 6-bot unattended run
**Then** both reach a result with bots visibly roaming, and a summary is recorded in a Markdown note

## Epic 6: Bots fight at melee range

In Vi6, PvP is one-sided: guards are armed, and thieves usually don't fight back. A guard bot picks a thief, aims, stays as close as possible, cuts off its retreat, and lands hits that respect the attack cooldown and reach, without being snapped back by the server. Thief bots flee. The melee controller is a generic, reusable `core` strategy in `fr.nekotine.core.combat`, whose positioning the caller sets. Vi6 supplies who fights whom and how to position. A 1v1 against a human on the dev server is the acceptance gate.

### Story 6.1: A melee controller fights through the control port

**Implements:** FR12, AD-5 (amended), AD-8, NFR10

As a plugin developer,
I want a melee controller strategy that I point at a target and configure with a standoff distance and a positioning policy,
So that my bot (or later a mob) fights at close range in the style my game needs, with no combat code of my own.

**Acceptance Criteria:**

**Given** a body through the control port, and a target entity chosen by the caller
**When** the controller is engaged
**Then** it writes aim at melee priority (overriding the walker's look) and writes hands, aims at the target every tick, and attacks only when the target is within reach and the attack cooldown is ready (AD-5, AD-8)
**And** the port exposes what the controller needs to read cooldown readiness and reach, using Paper API types only

**Given** an engaged controller
**When** it positions the body
**Then** it does so by updating the walker's moving goal (Story 5.3), not by writing movement keys itself (AD-8)
**And** the caller sets a standoff distance (0 means as close as possible) and a positioning policy that turns the target's state into a goal point
**And** the library ships two policies: "close in", which goes straight at the target, and "cut retreat", which places the goal between the target and a retreat point the caller supplies

**Given** the controller is disengaged, or the target dies or leaves
**When** it stops
**Then** it stops writing aim and hands, and the walker's look applies again from the next tick

**Given** a `bots-test-host` integration test with a melee bot ("cut retreat", standoff 0) against a scripted bot that runs toward a fixed retreat point
**When** they run for a set time
**Then** the melee bot gets between the target and the retreat point, and hits land (`EntityDamageByEntityEvent` fires)
**And** consecutive hits are never closer than the cooldown allows, no hit lands beyond reach, and no `PlayerFailMoveEvent` refusal is logged
**And** the test gives the same result on 3 consecutive runs (NFR10)

### Story 6.2: Guard bots chase thieves, and thief bots flee

**Implements:** FR12, FR18 (brain side), AD-12

As the operator,
I want guard bots to hunt the thieves they meet and thief bots to run from guards,
So that bot-filled rounds play out Vi6's one-sided PvP.

**Acceptance Criteria:**

**Given** a guard bot roaming in-map, holding its weapon slot
**When** a thief comes within a set range and line of sight
**Then** its brain engages the melee controller on that thief with standoff 0 and "cut retreat", and the retreat point is the thief's nearest exit (or its entrance, if it has not stolen yet), read through Vi6's public API
**And** any random choice (such as which of two thieves to chase) uses the seedable random source from Story 4.3

**Given** an engaged guard bot
**When** the thief dies, leaves range or line of sight for a set time, or the phase ends
**Then** the brain disengages and returns to roaming (Story 5.5)

**Given** a thief bot
**When** a guard comes within a set range and line of sight
**Then** its brain never attacks, and walks away from the guard toward its exit or entrance through the walker and the Vi6 rule, then resumes roaming once it is clear

**Given** the brains pick targets
**When** they read game state
**Then** they use only Vi6's public API (teams, phase, the player list, map exits and entrances), with no bot branch in game logic (AD-12)

**Given** a brain or controller failure
**When** it happens
**Then** the brain logs it once with bot and game context and stops (AD-12)

**Given** a Vi6 integration test with one guard bot and one thief bot placed near each other in-map
**When** the round runs
**Then** the thief flees, the guard chases and lands hits, and the thief never damages the guard

### Story 6.3: A guard bot chases a human thief, and a thief bot flees a human guard

**Implements:** FR12, NFR11

As the operator,
I want to play against the bots myself on the dev server,
So that I can confirm the chase feels like playing against a person.

**Acceptance Criteria:**

**Given** the dev server, one human thief and one guard bot in a Vi6 round
**When** they meet in-map
**Then** the bot picks the human as its target, stays as close as possible, cuts off the retreat toward the exit, and lands hits within cooldown and reach, and the log shows no snap-back (CAP-8)

**Given** one human guard and one thief bot
**When** the human approaches
**Then** the bot flees and never attacks

**Given** the checks need a human client
**When** they are run
**Then** the outcome and any tuning changes are recorded in a Markdown note, and this is the acceptance gate for FR12 (NFR11)

### Story 6.4: The unattended run includes chases

**Implements:** FR12, FR13, NFR10

As the operator,
I want unattended runs to use the guard-chase and thief-flee brains,
So that bot-only games show the full game, including PvP.

**Acceptance Criteria:**

**Given** the run command
**When** it starts a 4-bot or 6-bot run
**Then** guard bots roam and chase, and thief bots roam, steal, flee and escape

**Given** the soak test from Story 4.5
**When** it runs with these brains on short timers
**Then** it passes 3 times in a row, with a result and no exception, including rounds in which thieves are caught as Vi6's rules decide (NFR10)

**Given** the dev server
**When** the operator runs a 4-bot and then a 6-bot run
**Then** both reach a result with visible chases, and a summary is recorded in a Markdown note

## Epic 7: Bots are ready for real players and future adopters

The closing epic. Bots fill a real 2v2 next to friends, which closes the SPEC success signal for Vi6. The adopter guide is rewritten against the shipped API, so that a future Nekotine minigame can adopt bots. It runs after Epics 1-6 are done.

### Story 7.1: Bots fill a 2v2 in front of real players

**Implements:** FR1, FR5, FR9, FR12, NFR11 (SPEC success signal for Vi6)

As the operator,
I want 1-3 bots to complete a full 2v2 round with my friends,
So that a round can be played whenever not enough people are online.

**Acceptance Criteria:**

**Given** the dev server with the final jar, and 1 human
**When** the operator adds 3 bots with intent `any`, and the round is played
**Then** the teams balance to 2v2, bots pick entrances and ready up in preparation, roam, steal, flee and chase in-map, and the round reaches a recorded result with no exception in the log

**Given** 2 humans and 2 bots, and then 3 humans and 1 bot
**When** each round is played
**Then** each also reaches a result, whichever team the humans pick

**Given** the rounds are over
**When** the operator removes the bots
**Then** no bot, scoreboard entry or bot player data is left

**Given** these checks need real players
**When** they are run
**Then** the outcome, player feedback and any tuning changes are summarized in a Markdown note, and the SPEC success signal is marked as met for Vi6 (with the second-minigame part deferred, per the scope decisions)

### Story 7.2: The adopter guide matches the shipped bots API

**Implements:** FR15, adopter-guide requirement (Additional Requirements: Documentation)

As a future Nekotine minigame developer,
I want an adopter guide written from the real API,
So that I can add bots to my game without reading the Vi6 code.

**Acceptance Criteria:**

**Given** `bots-adopter-guide.md` was written from the design before any code existed
**When** it is rewritten
**Then** every type, method and package it names exists in the shipped `core` and `bots` code, and every code example compiles against it (checked by copying it into `bots-test-host` or a scratch test)

**Given** the guide
**When** a reader follows it
**Then** it covers the setup (submodule, `includeBuild`, shading, Paper plugin), the roster lifecycle and wipe flags, the per-bot tick event, the controls, the control port and control priority, the pathfinder, walker and melee policies, failure handling, the self-test, and testing with `bots-test-host`

**Given** the guide
**When** it describes Vi6
**Then** Vi6 appears only as an example (commands, brains, one-sided PvP policies), never as a requirement

**Given** the deferred follow-ups recorded for after the bots work (the `ClientTrackModule` fixes, per-tool tests)
**When** this story completes
**Then** the story's completion notes list them as ready to start, so the user can launch them
