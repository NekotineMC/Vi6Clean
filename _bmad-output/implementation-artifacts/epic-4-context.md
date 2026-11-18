# Epic 4 Context: A full Vi6 game runs with zero humans

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

One console command starts an unattended Vi6 game with 4 or 6 bots and no humans. Bots clear the lobby, then in preparation thief bots pick an entrance and every bot readies up through new player commands, so preparation ends at once. In-map and infiltration, the bots stand idle and the infiltration lost timer ends the round with a recorded result and a clean log. The run can be repeated from a clean roster. This idle-bot run is the regression baseline that Epics 5 (navigation) and 6 (melee) later upgrade by swapping in smarter brains, so it has to be reliable and repeatable before any movement work starts.

## Stories

- Story 4.1: Players can pick a thief entrance with a command
- Story 4.2: Players can mark themselves ready in preparation with a command
- Story 4.3: Bot brains get through preparation and stay idle in-map
- Story 4.4: The operator starts an unattended run with one command
- Story 4.5: A soak test runs 4-bot and 6-bot games to a result
- Story 4.6: Unattended runs work on the dev server

## Requirements & Constraints

- **Unattended run:** one console command, count 4 or 6 only, zero humans. It goes lobby → preparation → in-map → infiltration to a win condition, and the log shows the result with no exception. It is refused past the lobby or for any other count.
- **Repeatable:** each run starts from a clean state: no leftover bots, no leftover scoreboard entries, no bot player data. A second and third run must also reach a result.
- **Result summary:** when a run ends and the game returns to the lobby, Vi6 logs one summary line (winner, artefacts stolen and escaped, duration). Tests assert on it.
- **Preparation commands are player commands:** the spawn-select command (entrance name from the current map's thief spawns, with tab suggestions) and the preparation-ready command are usable by humans too and need no entity interaction. They have the same effect as the existing minimap ArmorStand selection and the "Prêt" menu toggle, which keep working. Wrong team, wrong phase, or an unknown name is refused with a message and changes nothing. Preparation ends as soon as every player is ready, without waiting out the timer. The random-spawn fallback still applies when nothing was selected.
- **`/game ready` stays lobby-only.** Do not extend it to preparation.
- **Determinism:** a test of a phase gives the same result on 3 consecutive runs. Every random choice a brain makes (such as the entrance) comes from one seedable random source per run, which tests pin. A soak test passes when the game reaches a result with no exception in the log, and must pass 3 times in a row.
- **Sizing:** at most 6 players per game; 4 bots typical, 6 maximum.
- **Verification placement:** anything that needs no human runs in the integration suite on short timers. The dev-server check runs with real timers from the console; its results go in a Markdown note under `_bmad-output/`, never a raw log (logs hold player names and IPs).
- **Tool-less bots are fine:** bots buy nothing (the shop is an inventory menu and inventory-click is deferred). A round can end without PvP or movement because the infiltration lost timer ends it.

## Technical Decisions

- **Bots play by human rules:** brains read game state only through Vi6's public API and act only through bot controls and player commands sent as real chat-command packets. No Vi6 game-logic file checks whether a player is a bot. The team intent given at spawn is carried out only by the brain.
- **Brains** live in `fr.nekotine.vi6clean.bot` and never call Vi6 test hooks. Brain and strategy logic runs only inside the per-bot tick event (main thread); controls set there apply the same tick. Commands fire once, on the next apply step.
- **Brain failure isolation:** a brain guards its own tick listener: on exception it logs once with the bot's name, team and phase, then stops that brain (same pattern as the lobby brain). Library log lines carry bot context only; game context is added by Vi6.
- **Hosts own commands:** the `bots` library ships no command. The run command is a Vi6 command, alongside the existing add/list/remove bot commands.
- **Run start path:** the run command adds bots with intent `any` and starts the game through the same path as `/game lobby` (`Vi6Game.start()`).
- **Single reset implementation:** the pre-run reset (remove leftover bots through the roster, clear scoreboard entries, ensure no bot player data remains) lives in Vi6's main source and is shared with the existing test reset hook, with no second copy.
- **Roster is the only lifecycle owner:** every bot exit goes through roster removal (quit event, then wipe of state and statistics files by default). Spawn/remove requests made during the tick loop take effect at its end.
- **Short timers** come from the phase-duration seam built earlier; human defaults stay unchanged.
- **Soak test** runs a 4-bot then a 6-bot game in one server boot through the run command's code path, in `src/integrationTest/java`.

## Cross-Story Dependencies

- 4.3 depends on 4.1 and 4.2 (brains send those commands).
- 4.4 depends on 4.3 (brains drive the run) and reuses Epic 3's test reset hook logic (moved or shared, not duplicated); it builds on Epic 2's bot add/list/remove commands and lobby brain.
- 4.5 depends on 4.4 (same code path, summary line) and on Epic 3's phase-duration seam for short timers.
- 4.6 depends on 4.4 and a deployed jar; it is the manual dev-server gate.
- Epics 5 and 6 each end by upgrading this run's brains and must keep the 4.5 soak test green, so keep the brain wiring easy to swap per phase.
