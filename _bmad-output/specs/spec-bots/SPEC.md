---
id: SPEC-bots
companions:
  - test-coverage-boundary.md
sources: []
---

> **Canonical contract.** This SPEC and the files in `companions:` are the complete, preservation-validated contract for what to build, test, and validate. Source documents listed in frontmatter are for traceability — consult them only if you need narrative rationale or prose color this contract intentionally omits.

# Fake Player Bots

## Why

Vi6Clean is a two-team asymmetric game, usually played 2v2 and never beyond 3v3, on a small, self-hosted, friends-only server, and it has two pains that need a player-shaped stand-in. A round cannot start without enough players, and the operator wants to say "add bots" instead of waiting. And there is no test harness: every regression check of a phase transition, a KoTH capture or a win condition needs a human at a keyboard. One bot serves both: our own fake player, built once in a library, NekotineBots, that other Nekotine minigames can reuse. Bots are the operator's own tool: ugly and command-only is fine.

## Capabilities

- **CAP-1 — Runtime bot control**
  - **intent:** The operator can add and remove bots on a running server, choosing count and team, without a restart.
  - **success:** One command adds N bots to the live roster, a list command reports them, a second command removes them, and no restart occurs in between.

- **CAP-2 — Player parity in the game layer**
  - **intent:** A bot is treated exactly as a human by team assignment, every phase transition, scoring, cleanup and dialogs, answering a dialog with the same effect as a human clicking it.
  - **success:** A game with bots runs the full phase sequence to a recorded result with bots balanced, scored and cleaned up, and no Vi6 game-logic file special-cases bots. With no human connected, bots answer the lobby dialog and the lobby advances; the `DialogAction.customClick` lambda at `LobbyPhasePlayerWrapper.java:112-122` runs unmodified.

- **CAP-3 — Tool use by bots**
  - **intent:** A bot holding a Vi6Clean tool triggers the same handlers a human triggers.
  - **success:** Every handler gated on `PlayerInteractEvent`, `PlayerMoveEvent`, `PlayerItemHeldEvent`, sneak and sprint can be reached by a bot, and the tool's observable effect occurs. → `test-coverage-boundary.md`

- **CAP-4 — Visible autonomous activity**
  - **intent:** A bot moves and navigates the world on its own, so a bot-filled round is watchable.
  - **success:** With no operator input, a spawned bot travels between map areas for the whole round, respecting the map's entrance and exit rules.

- **CAP-5 — Unattended game execution**
  - **intent:** A complete game runs start to finish with zero humans connected.
  - **success:** A 4-bot run, and a 6-bot run, each clears the lobby, plays preparation, in-map and infiltration to a win condition, and the log shows the result with no exception. The run is started by one console command and can be repeated, each run starting from a clean roster with no leftover bots or scoreboard entries.

- **CAP-7 — Reusable bots**
  - **intent:** Any Nekotine minigame can spawn, drive and remove bots through one library API without writing NMS code of its own.
  - **success:** A second plugin with no NMS code of its own depends on NekotineBots, spawns a bot, makes it use an item and removes it, and Vi6 uses the same API for all its bot work.

- **CAP-8 — Close-range combat**
  - **intent:** A bot can fight another player at melee range in Vi6 PvP.
  - **success:** In a 1v1 against a human, a bot picks its target, aims, keeps distance, strafes and lands hits that respect the attack cooldown and reach, without being snapped back by the server.

## Constraints

- **Bots are real fake players.** Each bot is a `ServerPlayer` joined through `PlayerList.placeNewPlayer` on a fake `Connection` backed by an `EmbeddedChannel`, so it is in `getOnlinePlayers()`, `isOnline()` is true, `Bukkit.getPlayer(uuid)` resolves, and `PlayerJoinEvent`/`PlayerQuitEvent` fire. Admission may use Vi6's normal join path.
- **Our own implementation.** The body is written from scratch, with [rs4t/egors-fakeplayer](https://github.com/rs4t/egors-fakeplayer) and other fake-player plugins as references only — no fork, no dependency. Their direct `gameMode`/`setSelectedSlot`/`setShiftKeyDown` calls and Carpet features (chunk loading, auto-eat, follow, restart persistence) are not reproduced. Any snippet copied verbatim keeps its MIT notice.
- **Real handlers only.** Every input reaches Vi6 through a real `ServerGamePacketListenerImpl.handleX` call with a real serverbound packet: no fabricated Bukkit event, no direct game-mode calls, no bot branch in game logic.
- **Packets enter through the channel pipeline.** Every bot packet is written inbound into the fake connection's channel rather than handed straight to `handleX`, so ProtocolLib inbound listeners (`ClientTrackModule`) see bot packets exactly as they see human ones.
- **The body confirms what a client confirms.** One teleport ack per received position id, latest only, on the next tick and never from inside `send()` (a matching id with nothing pending disconnects the bot); a player-loaded ack after join and each respawn.
- **The bot ticks itself.** Nothing ticks a connection outside `ServerConnectionListener`, so the bot's own tick resets the listener position, sends pending acks, runs actions and physics.
- **Movement fires `PlayerMoveEvent`.** The bot computes its next position with the server's own physics and submits it through `handleMovePlayer` as a `Pos`/`PosRot` packet; refused moves are diagnosed through `PlayerFailMoveEvent`.
- **NMS stays in one library.** NekotineBots is a library, not a plugin: its own repository, included in Vi6Clean as a git submodule and Gradle subproject like NekotineCore, and shaded into each plugin that uses it. It owns the body and input layers; its public API uses Bukkit types only; host plugins own the behaviour layer and call its lifecycle hooks. It stays out of NekotineCore so Core is not pinned to one Paper build; it has no required dependency on Core but may take one when needed. Each host ships its own copy, so two hosts on one server would run separate bot rosters.
- **Small, compile-checked NMS surface.** About 25 NMS types in 2-3 files, minimal reflection, and a startup self-test of every NMS or reflective accessor that fails loudly at boot.
- **One Minecraft version per release.** NekotineBots moves when Vi6 moves — 26.3, then 26.4 as Paper and ProtocolLib allow — with no multi-version layer.
- **Java.** Not Kotlin: its API features leak into the Java plugins that call NekotineBots.
- **Sized for 2v2 to 3v3.** At most 6 players per game: lobby fill means 1-5 bots; an unattended run means 4 bots typically and 6 at most.
- **Bots are ephemeral.** They never survive a server restart or `/reload`: NekotineBots removes every bot when its host plugin disables, and nothing persists or restores them.
- **Bot failures are loud.** An exception in bot behaviour is caught and logged with the bot's name and the current phase; a bot never stops acting silently.
- **Navigation and combat are separate problems.** Long-range travel uses A* over Bukkit block data, the Pathetic library or a precomputed per-map navigation graph; close-range PvP is a per-tick reactive controller. Baritone does not run server-side.
- **Verification is a booted server.** No JVM test suite exists or is introduced; every acceptance check runs on the dev server over RCON and the log. → `test-coverage-boundary.md`

## Non-goals

- **Network-client bots** — MCProtocolLib loopback (needs a hand-written client physics model), Mineflayer (upstream stuck at 26.1), and a HeadlessMC + Baritone test layer (packet bots already cover the server-side logic every capability needs).
- **Verifying what a human sees** — dialog rendering, resource-pack application, ProtocolLib visuals, voice chat.
- **Skins, crafted names, or passing as human.**
- **Player-facing bot management UX.** Commands only.
- **Chat and commands sent by bots.** No consumers in either tree.
- **Difficulty tiers or bot-vs-bot matchmaking.**
- **Anti-cheat compatibility.** The server is friends-only; cheating is not a concern.
- **Machine-learning data generation.**
- **A unit-test suite.**

## Success signal

On the dev server, one command adds 4 bots; with no human connected, they answer the lobby dialog, phases advance, and the round reaches a win condition with a recorded result and no exception in the log. Then 1-3 bots complete a 2v2 in front of real players, and a second minigame spawns and drives a bot through the same NekotineBots API.

## Assumptions

- Vi6's existing join listeners (resource-pack send, lobby admission at `Vi6PhaseLobby`) accept a fake player without change.
- Vi6's outbound packet sends for bots (`PantsHandler`, `TrapWireHandler`) are safe now that they reach the bot's `send()`.
- ProtocolLib injects its inbound handler into the fake connection's `EmbeddedChannel`, and a decoded packet object written inbound reaches it and then the connection's own handler. ProtocolLib normally injects on a real network channel, so this is unverified.
