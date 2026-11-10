# Epic 2 Context: The operator fills a Vi6 lobby with bots

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Bring the Epic 1 bots library into Vi6Clean so an operator can add, list and remove bots on a live Vi6 server without a restart, and so bots get through the lobby on their own: with no human connected, each bot picks its team and readies up by sending `/game team` and `/game ready` as real chat-command packets, and the lobby advances to preparation. Bots stand idle once the round starts (movement and melee come in later epics). The epic also checks two SPEC assumptions: Vi6's join path accepts a fake player unchanged, and outbound packet sends to a bot are safe. This is the first Vi6-side bot work. It matters because it proves bots get the same treatment as humans, which every later epic (test hooks, unattended runs, roaming, melee) relies on. Vi6 bot code lives in `fr.nekotine.vi6clean.bot`.

## Stories

- Story 2.1: A bot joins the Vi6 lobby like a player
- Story 2.2: The operator adds, lists and removes bots with commands
- Story 2.3: Bots pick their team and ready up through player commands
- Story 2.4: Bots fill a lobby with humans

## Requirements & Constraints

- **Library wiring:** when `Vi6Main` enables, it resolves the bots roster eagerly so the self-test runs and its result is logged. If the self-test fails, Vi6 still enables, humans can play, and every bot command answers "bots unavailable" and changes nothing.
- **Human parity:** bots get the same team assignment, team balance, phase transitions, scoring and cleanup as humans. They join through Vi6's normal join path (`Vi6PhaseLobby.onPlayerJoined`, which puts them in `Vi6Game.getPlayerList()`) and leave through the normal quit path (`Vi6PhaseGlobal.onPlayerLeft`, which takes them off the player list and the scoreboards). Join listeners (resource-pack send, lobby dialog) must run without an exception for a bot.
- **No bot special-casing:** no file in `src/main/java` outside `fr.nekotine.vi6clean.bot` checks whether a player is a bot.
- **Operator commands (console and op only, refused for non-ops):**
  - add N bots with a team intent `guard`, `thief` or `any`. Names are unique: no clash with online players or existing bots.
  - list: each bot's name, team intent, current Vi6 team, and state (live, or failed).
  - remove one bot by name, or `all`, through the roster's single exit path.
  - an add that would push the game past 6 players is refused with a message that says why.
- **Team intent is carried out by the brain only:** the intent is handed to the brain at spawn. Nothing but the brain's commands assigns a bot's team; no code calls `addPlayerInGuards` / `addPlayerInThiefs` for a bot. Intent `any` reads team sizes through `Vi6Game.getGuards()` / `getThiefs()`, joins the smaller team, and picks `guard` on a tie.
- **Brain failure isolation:** a brain guards its own tick listener. On an exception it logs one line with the bot's name, its team and the current Vi6 phase, then stops that brain. The bot stays connected.
- **Determinism:** the 4-bot (`any`), no-human integration test ends 2v2, every bot is ready, the phase machine moves from `Vi6PhaseLobby` to `Vi6PhasePreparation`, and the test gives the same result on 3 consecutive runs.
- **Test bundle:** `test-bundle/README.md` requires `spawn-protection=0`, and the local `test-bundle/server.properties` (16 today) is updated to match, so non-op bots can click near spawn. `max-players=20` is enough.
- **Dev-server check:** the mixed lobby (1 human plus 3 `any` bots balancing to 2v2, then advancing to preparation; idle bots with no exceptions in the log; mid-round removal handled as a player quit) needs a real client. It runs on the dev server, and its result goes in the story's completion notes.
- **Sizing:** a lobby fill adds 1-5 bots, and a game has at most 6 players.

## Technical Decisions

- **Layering:** Vi6 brains and commands sit on top of the `bots` public API (`fr.nekotine.core.bot`) and never touch NMS. `bots` ships no commands, so Vi6 registers its own (add, list, remove now; the unattended-run command comes in a later epic).
- **Roster:** an `IPluginModule` resolved with `Ioc.resolve(...)`. It must be resolved eagerly at enable, because Core's `ModuleManager` loads lazily and only logs constructor errors. Vi6 already depends on and shades `fr.nekotine:bots`; the eager resolve is what is missing.
- **Brains play by human rules:** they read game state only through Vi6's public API, and act only through bot controls and the commands a player can send. They run inside the per-bot tick event. Controls and commands set there apply that same tick, and commands fire once.
- **Library failure model:** the library logs its own per-bot failures once (bot name plus stack trace), sets the bot's controls to neutral, stops ticking it, and lists it as failed. It does not catch host listener exceptions, so Vi6 adds game context (phase, team) in its own logs.
- **Lifecycle:** every exit (remove, `all`, unload, kick) goes through the roster's single exit path: flush, close, normal quit (`PlayerQuitEvent`), drop from the roster, then wipe. Wipe flags default on. Bots are ephemeral: nothing restores them after a restart or `/reload`. Spawns and removes requested during the tick loop apply at its end.
- **Threading:** commands, controls, the tick event and roster changes all run on the main thread.
- **Test placement:** Vi6 integration tests (`src/integrationTest/java`, run by `gradlew integrationTest`) test Vi6 and use bots only as a driver. Library defects belong in NekotineCore's own suite.
- **Assumption to verify:** outbound sends from `PantsHandler` and `TrapWireHandler` to a bot's connection are safe.
- **Out of scope:** dialogs, the minimap ArmorStand interaction, inventory-click (so bots buy no tools in the shop), and `ClientTrackModule` checks against bots.

## Cross-Story Dependencies

- Depends on Epic 1 (done): the roster, spawn/remove/list, the failed state, the per-bot tick event, the send-command control, and the self-test with "bots unavailable".
- 2.1 (eager roster resolve, join and quit parity, `spawn-protection=0`) gates 2.2-2.4.
- 2.2's add command carries the team intent that 2.3's brain carries out. 2.2's list shows each bot's current Vi6 team and its failed state.
- 2.4 needs 2.2 and 2.3 and runs on the dev server.
- Later epics build on this: Epic 3's test hooks (force teams and phase, reset) must stay separate from brains; Epic 4's unattended run reuses these commands and lobby brains, and adds preparation brains and the run command.
