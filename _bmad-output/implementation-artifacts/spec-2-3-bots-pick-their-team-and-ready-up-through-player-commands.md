---
title: 'Story 2.3: Bots pick their team and ready up through player commands'
type: 'feature'
created: '2026-11-10'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Bots added with `/bot add` (Story 2.2) carry a team intent but never act on it: they keep the team the join path gave them and never ready up, so a bot-only lobby never reaches preparation (FR5, FR6, FR18 brain side, AD-12, NFR10).

**Approach:** Each bot gets a lobby brain, handed its intent at spawn and run from the per-bot `BotTickEvent` in `fr.nekotine.vi6clean.bot`. Once per lobby (while the bot has a `LobbyPhasePlayerWrapper`), it sends `/game team <guard|thief>` then `/game ready` through `Bot.sendCommand`; intent `any` reads `Vi6Game.getGuards()` / `getThiefs()` without counting the bot itself, joins the smaller team, and picks `guard` on a tie. The brain remembers it acted (commands land after the tick, and `/game ready` toggles) and acts again in the next lobby. Nothing else assigns a bot's team. A brain that throws logs one line with the bot's name, its Vi6 team and the current Vi6 phase, then stops; the bot stays connected. An integration test adds 4 `any` bots with no human and checks 2v2, all ready, and the move to `Vi6PhasePreparation`, 3 times in a row.

</frozen-after-approval>

## Implementation Notes

- Branch `feat/bots-2-3-lobby-brain` from `bot`; PR to `bot`. No NekotineCore change.
- `Vi6Bots` keeps `Bot → BotTeamIntent` and `Bot → BotBrain`; `setIntent` installs a `LobbyBrain` for that intent. It listens to `BotTickEvent` (registered in its constructor, unregistered in `unload`), runs the bot's brain inside a try/catch, and on a throw logs once and drops that brain. `setBrain(Bot, BotBrain)` is public: the failure test installs a throwing brain, and Epic 4's preparation brains will use it.
- `BotBrain` is a one-method interface (`tick(Bot)`); `LobbyBrain` holds the intent and remembers the lobby wrapper it acted in (a new lobby is a new wrapper), plus the team it picked there.
- `any` counts the other players in `getPlayerList()`, using a bot's picked team when its brain has acted (`Vi6Bots.pickedTeam`, package-private) and its current team otherwise: `/game team` runs through `server.execute`, so brains acting in the same tick would otherwise all read the same counts and pile into one team (review E3).
- A brain failure is caught as `RuntimeException | LinkageError` (as the library does), logged as one line with the bot's name, team and phase, with the exception attached.
- `Vi6Bots` drops a bot's intent and brain on its `PlayerQuitEvent`, so removals that bypass `/bot` (roster calls, kicks, lost connections) no longer leave them behind.
- AC "no code calls `addPlayerInGuards` / `addPlayerInThiefs` for a bot" is read as no bot-specific call: `Vi6Game.addPlayer`'s join balancing and the lobby's team swap treat bots like every player, and the brain only changes teams through `/game team`.
- The current phase for the failure log is found by checking `getPhase(...)` for Infiltration, Preparation, then Lobby; Core's `IPhaseMachine` has no current-phase getter, and none is added.
- Behaviour that is the game's rule, not the brain's: a lobby whose players are all ready advances, so a single bot added alone readies and starts the game, as a lone ready human would.
- Test: `Vi6BotBrainIT` — the 4-bot `any` run (`@RepeatedTest(3)`: 2v2 with the same names on each team every run, then `Vi6PhasePreparation`), a lone `thief` bot, a next lobby where both remaining bots come back on the same team and must split, and a throwing brain (one error line with team and phase, exception attached, brain called once, bot still online). Teardown removes the bots and returns the game to `Vi6PhaseLobby`.
- `Vi6BotCommandsIT`: Bot_2's `guard` intent now makes it a guard (its brain sends `/game team guard`), and the test asserts the lobby holds while Bot_1, spawned without a brain, is not ready. The log capture moved out of `Vi6BotLobbyIT` into `LogCapture` and also keeps error lines without an exception.
- Changed: new `bot/BotBrain.java`, `bot/LobbyBrain.java`; `bot/Vi6Bots.java`; new `it/Vi6BotBrainIT.java`, `it/LogCapture.java`; `it/Vi6BotLobbyIT.java`, `it/Vi6BotCommandsIT.java`.
- Verified (2026-11-10): bare `gradlew -PVi6CleanJarOutputPath=build/dist`, `gradlew build integrationTest` green, 20 tests. IT log: three `Vi6PhasePreparation` setups from the 4-bot run, the expected brain error line, no new errors (the `UpdateFolderRestart.close` NPE at shutdown predates this story).

## Review Triage Log

Review pass 1 (2026-11-10), Blind Hunter (E).

| # | Finding | Verdict | Evidence | Route |
|---|---------|---------|----------|-------|
| E1 | Brain failure log drops the stack trace | low | One line was asked for, not a stack-less one; the exception is now attached to that line | patch |
| E2 | Only `RuntimeException` caught; context building inside the catch could throw | low | An `Error` would be logged by Paper every tick; now catches `LinkageError` too, like the library. `team`/`phase` only read `Vi6Game`, which exists while bots tick | patch (catch) / reject (context) |
| E3 | `any` brains acting in the same tick read stale counts and pile into one team | medium | `handleChatCommand` runs through `server.execute`; a next lobby that swaps two guards to thiefs made both pick guard. Picks now count; `anyBotsBalanceAgainInTheNextLobby` covers it | patch |
| E4 | `acted` not reset if the lobby restarts within one tick | low | `goTo` the current phase is a no-op, so not reachable today; keying on the wrapper instance is as simple | patch |
| E5 | The brain never checks its commands took effect; a toggle can undo a ready | low | Act-once is the design (spec); bots get no dialog, and no rule refuses `/game team` | reject |
| E6 | No test for an explicit intent, nor for the next lobby | low | Added the lone `thief` test and the next-lobby test; `guard` is covered by `Vi6BotCommandsIT` | patch |
| E7 | Join balancing and the lobby swap call `addPlayerIn*` for bots, against AC 3 | false | They apply to every player, so the AC's no-bot-special-casing rule holds; reading recorded in Implementation Notes | reject |
| E8 | Brains of bots removed outside `/bot` stay until the next command | low | Now dropped on `PlayerQuitEvent` | patch |
| E9 | `setIntent` silently replaces a brain set by `setBrain` | low | Its Javadoc says it hands the intent to a new lobby brain; Epic 4 decides how brains chain | reject |
| E10 | `Vi6BotCommandsIT` comment claims the lobby holds without checking; phase could leak | low | Assertion added; no phase change happens there to leak | patch |
| E11 | `LogCapture` fixed appender name; Javadoc vague | low | ITs run sequentially in one JVM, never nested; Javadoc fixed | reject (name) / patch (doc) |
