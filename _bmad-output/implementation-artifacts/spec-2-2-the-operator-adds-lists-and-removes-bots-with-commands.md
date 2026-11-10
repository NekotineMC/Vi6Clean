---
title: 'Story 2.2: The operator adds, lists and removes bots with commands'
type: 'feature'
created: '2026-11-10'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/human-coding-style.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Vi6 resolves the bots roster at enable (Story 2.1) but has no way to drive it: the operator cannot add, list or remove bots on a live server (FR1-FR3, AD-14, NFR7), and Story 2.3's brains need each bot's team intent recorded at spawn.

**Approach:** Vi6 registers its own `/bot` command in `fr.nekotine.vi6clean.bot`, op and console only: `/bot add <count> <guard|thief|any>` spawns `count` bots with free names (`Bot_1`, `Bot_2`… skipping names held by online players or roster bots) at the default world's spawn, so they join through Vi6's normal join path, and records each bot's intent; an add that would push `Vi6Game.getPlayerList()` past 6 players is refused with a message giving the current count and the limit. `/bot list` shows each roster bot's name, intent, current Vi6 team (guard, thief or none, read from `getGuards()`/`getThiefs()`) and state (live or failed). `/bot remove <name|all>` removes through `BotRoster.remove`. When the roster is null (unbuildable) or unavailable (failed self-test), every subcommand answers "Bots indisponibles" and changes nothing. Messages are in French. No code assigns a bot's team: the intent is only stored for Story 2.3's brain. An integration test drives the commands through a feedback command sender and a non-op bot.

</frozen-after-approval>

## Implementation Notes

- Branch `feat/bots-2-2-bot-commands` from `bot` (`e57aa13`); PR to `bot`. No NekotineCore change.
- Command name `/bot` follows the architecture review rubric (`/bot add`). Op-only through CommandAPI `withPermission(CommandPermission.OP)`; the console passes, a non-op player gets Brigadier's unknown-command reply.
- Intent store: `Vi6Bots` (`IPluginModule`, fetched with `Ioc.resolve(ModuleManager.class).get(Vi6Bots.class)`, like `ConfigManager`; Core only auto-registers `fr.nekotine.core` modules in Ioc) keeps `Bot → BotTeamIntent`; entries for bots no longer in the roster (kicked, lost connection) are dropped on each add and ignored by list.
- Registered from `Vi6Commands.registerCommands()` (runs in `onLoad`), so AD-12 still holds: only `fr.nekotine.vi6clean.bot` and `Vi6Main` reference `fr.nekotine.core.bot`.
- The "bots unavailable" path is not covered by a test: it needs fault injection, already deferred from Story 2.1.
- Deviation from the Intent: the 6-player cap counts `Bukkit.getOnlinePlayers()`, not `Vi6Game.getPlayerList()`. `Vi6PhaseLobby.globalSetup` adds every online player to the game, so bots added mid-round would otherwise pass the cap and join the next lobby; in the lobby the two counts are equal.
- `OP` permission is set on `/bot` and on each subcommand, so it holds whether or not CommandAPI passes a parent's permission down.
- A failed `spawn` (join error) is caught at the command boundary: logged in French with the stack trace, reported to the sender, and the remaining bots of that add are not spawned.
- Changed: new `bot/BotTeamIntent.java`, `bot/Vi6Bots.java`, `bot/Vi6BotCommands.java`; `Vi6Commands.registerCommands()` calls `Vi6BotCommands.register()`; new `src/integrationTest/.../Vi6BotCommandsIT.java` (add with a name clash, intents, list lines, cap refusal, remove by name, unknown name, `all`; a non-op bot's `add` and `remove all` change nothing).
- Verified (2026-11-10): bare `gradlew -PVi6CleanJarOutputPath=build/dist`, `gradlew build integrationTest` green, 14 tests. IT log: `Bot_2`, `Bot_3` join through the lobby and leave through the roster; no new errors (the `UpdateFolderRestart.close` NPE at shutdown predates this story).

## Review Triage Log

Review pass 1 (2026-11-10), Blind Hunter (B).

| # | Finding | Verdict | Evidence | Route |
|---|---------|---------|----------|-------|
| B1 | `/bot add` outside the lobby: the bot joins the server, not the game | false | Same as a human joining mid-round; the next lobby's `globalSetup` adds every online player | reject |
| B2 | Cap counts online players, not `getPlayerList()` as the Intent says | low | Deliberate, see Implementation Notes: the lobby adds every online player, so the online count is what the next game holds | reject (deviation flagged to the human) |
| B3 | `MAX_PLAYERS` hardcoded in the command class | low | No game-wide cap exists yet; 2.4 can move it if it needs it | reject |
| B4 | `isNameTaken` skips `BotRoster.spawn`'s offline-UUID check | false | An offline UUID matches only the same name, which `getPlayerExact` already catches; online-mode UUIDs never collide | reject |
| B5 | A failed spawn mid-add prints no total and rolls nothing back | low | Each spawned bot gets its own line, the failure is logged; rare (join error) | reject |
| B6 | `Vi6Bots` keeps entries for kicked bots until the next add/remove | low | `list` ignored them but the map kept the references; `list` now prunes too | patch |
| B7 | Raw casts on command arguments | low | Same pattern as `Vi6Commands`; names are local literals | reject |
| B8 | List assertion repeated the command's team lookup; no proof intent does not assign teams | low | Assertion now hardcodes the lobby's balance (Bot_2 thief despite guard intent) | patch |
| B9 | Coverage gaps (thief/any, case, failed state, pruning, outside lobby) | low | Case-insensitive remove now tested; the rest is trivial parsing or needs fault injection | patch / reject |
| B10 | Non-op test checks the roster, not the refusal reply | low | The roster is the observable; Brigadier's reply is the server's | reject |
| B11 | `@AfterEach` leaves intents; CAP assumes no other online player | low | Pruned on the next command; the IT server has no humans | reject |
| B12 | `sprint-status.yaml` `last_updated` went backwards | false | The previous value was ahead of the clock; the new one is the real time | reject |
| B13 | `/bot` not documented outside the spec | low | Edits `AGENTS.md` | defer |
