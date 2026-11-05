---
id: SPEC-bots
companions:
  - test-coverage-boundary.md
  - ../../planning-artifacts/architecture/architecture-Vi6Clean-2026-11-03/ARCHITECTURE-SPINE.md
sources: []
---

> **Canonical contract.** This SPEC and the files in `companions:` are the complete, preservation-validated contract for what to build, test, and validate. Source documents listed in frontmatter are for traceability — consult them only if you need narrative rationale or prose color this contract intentionally omits.

# Fake Player Bots

## Why

Vi6Clean is a two-team asymmetric game. It is usually played 2v2 and never beyond 3v3, on a small, self-hosted, friends-only server. It has two pains that need a player-shaped stand-in:
- A round cannot start without enough players, and the operator wants to say "add bots" instead of waiting.
- There is no test harness: every regression check of a phase transition, a KoTH capture or a win condition needs a human at a keyboard.

One bot serves both. It is our own fake player, built once in NekotineCore so other Nekotine minigames can reuse it. Bots are the operator's own tool: ugly and command-only is fine.

## Capabilities

- **CAP-1 — Runtime bot control**
  - **intent:** The operator can add and remove bots on a running server, choosing count and team, without a restart.
  - **success:** One command adds N bots to the live roster, a list command reports them, a second command removes them, and no restart occurs in between.

- **CAP-2 — Player parity in the game layer**
  - **intent:** A bot is treated exactly as a human by team assignment, every phase transition, scoring and cleanup.
  - **success:**
    - A game with bots runs the full phase sequence to a recorded result, with bots balanced, scored and cleaned up.
    - No Vi6 game-logic file special-cases bots.
    - With no human connected, bots pick their team and ready up through the same commands a player sends, and the lobby advances.

- **CAP-3 — Tool use by bots**
  - **intent:** A bot holding a Vi6Clean tool triggers the same handlers a human triggers.
  - **success:** A bot can reach every handler gated on `PlayerInteractEvent`, `PlayerMoveEvent`, `PlayerItemHeldEvent`, sneak and sprint, and the tool's observable effect occurs. → `test-coverage-boundary.md`

- **CAP-4 — Visible autonomous activity**
  - **intent:** A bot moves and navigates the world on its own, so a bot-filled round is watchable.
  - **success:** With no operator input, a spawned bot travels between map areas for the whole round, respecting the map's entrance and exit rules.

- **CAP-5 — Unattended game execution**
  - **intent:** A complete game runs start to finish with zero humans connected.
  - **success:**
    - A 4-bot run and a 6-bot run each clear the lobby, play preparation, in-map and infiltration to a win condition, and the log shows the result with no exception.
    - One console command starts the run.
    - The run can be repeated, each time starting from a clean roster with no leftover bots, scoreboard entries or bot player data.

- **CAP-7 — Reusable bots**
  - **intent:** Any Nekotine minigame can spawn, drive and remove bots, and make them walk and fight, through NekotineCore's API without writing NMS code of its own.
  - **success:** A second plugin with no NMS code of its own uses NekotineCore to spawn a bot, make it use an item and remove it. Vi6 uses the same API for all its bot work.

- **CAP-8 — Close-range combat**
  - **intent:** A bot can fight another player at melee range in Vi6 PvP.
  - **success:** In a 1v1 against a human, a bot picks its target, aims, keeps distance, strafes and lands hits that respect the attack cooldown and reach, without being snapped back by the server.

## Constraints

- **Bots are real fake players.**
  - Each bot is a `ServerPlayer` joined through `PlayerList.placeNewPlayer` on a fake `Connection` backed by an `EmbeddedChannel`. It is therefore in `getOnlinePlayers()`, `isOnline()` is true, `Bukkit.getPlayer(uuid)` resolves, and `PlayerJoinEvent`/`PlayerQuitEvent` fire. Admission may use Vi6's normal join path.
  - A bot's UUID is the offline UUID derived from its name.
- **Our own implementation.**
  - The body is written from scratch. [rs4t/egors-fakeplayer](https://github.com/rs4t/egors-fakeplayer) and other fake-player plugins are references only: no fork, no dependency.
  - Their direct `gameMode`/`setSelectedSlot`/`setShiftKeyDown` calls and Carpet features (chunk loading, auto-eat, follow, restart persistence) are not reproduced.
  - Any snippet copied verbatim keeps its MIT notice.
- **Real handlers only.** Every input reaches the game through a real `ServerGamePacketListenerImpl.handleX` call with a real serverbound packet. That includes the commands a bot sends. There is no fabricated Bukkit event, no direct game-mode call, and no bot branch in game logic.
- **Packets enter through the channel pipeline.** Every bot packet is written inbound into the fake connection's channel rather than handed straight to `handleX`. ProtocolLib inbound listeners (`ClientTrackModule`) therefore see bot packets exactly as they see human ones.
- **The body confirms what a client confirms.**
  - One teleport ack per received position id, latest only, on the next tick, and never from inside `send()`. A matching id with nothing pending disconnects the bot.
  - A player-loaded ack after join and after each respawn.
- **The bot ticks itself.** Nothing ticks a connection outside `ServerConnectionListener`. The bot's own tick therefore resets the listener position, sends pending acks, runs actions and physics, and flushes.
- **Movement fires `PlayerMoveEvent`.** The bot computes its next position with the server's own physics and submits it through `handleMovePlayer` as a `Pos`/`PosRot` packet. Refused moves are diagnosed through `PlayerFailMoveEvent`.
- **Bot code lives in NekotineCore, in its own module.**
  - NekotineCore becomes a multi-module repository: `core` stays Paper-API-only and unpinned, and `bots` holds the NMS body and depends on `core`.
  - A host consumes both through one NekotineCore submodule plus `includeBuild`. Only the final plugin shades them.
  - The bot API uses Bukkit, Paper and `core` types only. Host plugins own bot behaviour and commands. Each host ships its own copy, so two hosts on one server run separate bot rosters.
- **Walking and fighting are reusable, not bot-only.**
  - Pathfinding, path following and melee control are `core` strategies, usable for mobs too.
  - The path contract is Paper's `Pathfinder.PathResult`. Pathfinder implementations are swappable and take the caller's passability rules. Baritone does not run server-side.
- **Small, compile-checked NMS surface.**
  - About 25 NMS types in 2-3 files, with minimal reflection.
  - A startup self-test checks every NMS or reflective accessor. If it fails, the bots module disables itself loudly and the host keeps running for humans.
- **One Minecraft version per release.** The `bots` module moves when Vi6 moves (26.3, then 26.4, as Paper and ProtocolLib allow), with no multi-version layer.
- **Java.** Not Kotlin: Kotlin's API features would leak into the Java plugins that call the library.
- **Sized for 2v2 to 3v3.** At most 6 players per game: lobby fill means 1-5 bots; an unattended run means 4 bots typically and 6 at most.
- **Bots are ephemeral.**
  - They never survive a server restart or `/reload`: the library removes every bot when its host plugin disables, and nothing persists or restores them.
  - On removal, a bot's saved state and statistics are wiped by default. Each can be kept per bot at spawn.
- **Bot failures are loud and isolated.**
  - A failure in library bot work is logged once, with the bot's name, and disables that bot. It never stops acting silently.
  - Library logs carry no game concepts. A host logs its own behaviour failures with its own context, such as the Vi6 phase.
- **Verification runs in the integration suite where it can.**
  - Checks that need no human client run as integration tests on an in-process Paper server through paper-junit-server (`gradlew integrationTest`).
  - Scripted-bot tests of a tool or phase give the same result on 3 consecutive runs.
  - A soak test with autonomous bots passes when the game reaches a result with no exception in the log.
  - NekotineCore tests the bots module itself, through a tiny host plugin and its own paper-junit-server submodule, with no copy of the harness and no dependency on Vi6. Vi6 tests test Vi6 and use bots only as a driver.
  - Checks that need real players or a human client, such as the fill 2v2 in front of real players, run on the dev server. → `test-coverage-boundary.md`

## Non-goals

- **Network-client bots:**
  - MCProtocolLib loopback, which needs a hand-written client physics model;
  - Mineflayer, whose upstream is stuck at 26.1;
  - a HeadlessMC + Baritone test layer, since packet bots already cover the server-side logic every capability needs.
- **Verifying what a human sees:** dialog rendering, resource-pack application, ProtocolLib visuals, voice chat.
- **Dialogs.** Bots neither read nor answer dialogs; they may return later as a hand-made custom click.
- **F3 path debug rendering.**
- **Skins, crafted names, or passing as human.**
- **Player-facing bot management UX.** Commands only.
- **Chat sent by bots.**
- **Difficulty tiers or bot-vs-bot matchmaking.**
- **Anti-cheat compatibility.** The server is friends-only; cheating is not a concern.
- **Machine-learning data generation.**
- **A unit-test suite.**

## Success signal

On the dev server, one command adds 4 bots. With no human connected, they pick their teams and ready up through commands, phases advance, and the round reaches a win condition with a recorded result and no exception in the log. Then 1-3 bots complete a 2v2 in front of real players, and a second minigame spawns and drives a bot through the same NekotineCore API.

## Assumptions

- Vi6's existing join listeners (resource-pack send, lobby admission at `Vi6PhaseLobby`) accept a fake player without change.
- Vi6's outbound packet sends for bots (`PantsHandler`, `TrapWireHandler`) are safe now that they reach the bot's `send()`.
- **Verified (integration-test spike, since removed; Paper 26.2 + ProtocolLib 5.5.0-SNAPSHOT):** ProtocolLib injects into the fake connection's `EmbeddedChannel` on its own at `PlayerJoinEvent`. A decoded packet written with `EmbeddedChannel.writeInbound` on the main thread reaches its receiving listeners (`event.getPlayer()` matched the bot by UUID) and then `handleX`. A listener's `setCancelled(true)` stops it before the handler. The bot body must provide:
  - **An active channel at join, handlers in place first.** Build the pipeline on an unregistered `new EmbeddedChannel(false, false)` (`Connection.configureInMemoryPipeline(pipeline, SERVERBOUND)`, `connection.configurePacketHandler(pipeline)`), then `register()` it, so `channelActive` sets `Connection.channel`. ProtocolLib's `inject()` silently skips an inactive channel.
  - **The game protocol on both codecs, as the real login path leaves them.**
    - Before `placeNewPlayer`, replace `decoder` with `UnconfiguredPipelineHandler.Inbound` named `inbound_config`, as the terminal configuration packet does on a real connection. `placeNewPlayer`'s `setupInboundProtocol` then installs a PLAY `decoder`.
    - Call `connection.setupOutboundProtocol(GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registryAccess)))`, which turns `outbound_config` into `encoder`.
    - Without the decoder swap, the injection still happens and `handleX` still runs, but the decoder stays HANDSHAKING and ProtocolLib's listeners never fire. That ProtocolLib maps packets by the decoder's protocol is inferred from that observation and from the swap fixing it, not tested directly.
    - ProtocolLib anchors on `decoder` and `encoder`, which both exist after join.
  - **The raw `EmbeddedChannel` kept for `writeInbound`.** ProtocolLib replaces `Connection.channel` with its `NettyChannelProxy`.
  - **Flushing.** Nothing ticks the connection, so outbound writes stay unflushed. Flush them (`flushOutbound()`) from the bot's tick and before closing the channel, or every pending write fails with `StacklessClosedChannelException` warnings on close.
  - **Not checked:**
    - writing inbound off the main thread (ProtocolLib may then defer main-thread listeners);
    - `ClientTrackModule` itself (the spike used its own `PacketAdapter` on `HELD_ITEM_SLOT`);
    - whether `event.getPlayer()` is the bot's own `CraftPlayer` rather than a ProtocolLib temporary player. This matters because `ClientTrackModule` keys a `HashMap<Player, …>` on it;
    - reading any packet field through `PacketContainer` (such as `getIntegers()`, or `getPlayerActions()` on `ENTITY_ACTION` as `ClientTrackModule` does).

    The `ClientTrackModule` checks are deferred until the bots work is complete.
