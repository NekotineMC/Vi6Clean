---
title: 'Story 4.5: A soak test runs 4-bot and 6-bot games to a result'
type: 'feature'
created: '2026-11-20'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'ba0a14d4cf02b1b00dd7c6ff52921b7a8ff48bdd'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Nothing yet guards the whole unattended run as one regression baseline: `Vi6BotRunIT` checks the summary line and the clean second run, but not that each game goes lobby → preparation → in-map → infiltration, not that a seed replays the same brain choices, and not that the run holds up when repeated (FR13, FR14, NFR10). Epics 5 and 6 must keep "the soak test from Story 4.5" green when they swap brains.

**Approach:** Add `Vi6SoakIT`, a `@RepeatedTest(3)` in one server boot. Each repetition, on short timers with no KoTH, dispatches `bot run 4 <seed>`, records the phases game 1 goes through and the brains' entrance picks, waits for its `Game ended: game=1` line, then from the lobby dispatches `bot run 6 <seed>`, checks nothing of the first run is left, and records the same for its game 1. The picks of each run must equal the first repetition's.

**Decisions (human, 2026-11-20):**
- Epic 3 retro item 6 (shared IT support with `awaitPhase`, `assertAround`, `game()`, `assertNear`; `Vi6PhaseDurationsIT` migration; stale `Vi6TestHooksIT` comments) ships first, in its own PR. This story starts after it merges into `bot` and adds the run helpers to that shared support; it does not redo any part of item 6.

## Boundaries & Constraints

**Always:** Go through `/bot run` (`Bukkit.dispatchCommand`), never `Vi6TestHooks` set-up hooks, to start games. Short timers through the config seam and `Vi6TestHooks.forceKoths()` with no KoTH, as `Vi6BotRunIT` does. `Vi6TestHooks.reset()` before and after each repetition. Each game asserts: phase order, one well-formed `Game ended: game=1, …` line, no captured failure or error line. Fixed seeds, so a failure can be replayed by hand with `bot run <count> <seed>`.

**Never:** Change main source (game logic, brains, `/bot run`). Assert stolen/escaped counts or the end reason beyond the line's format (idle bots always give `lost-timer`, 0/0; Epics 5–6 will change that). Leave two copies of the clean-second-run check.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 4-bot game | Lobby, clean roster, `bot run 4 42` | Phases sampled each tick, collapsed: lobby, preparation (in-map present), infiltration (in-map present), lobby; one `Game ended: game=1` line | fails with the sequence and log seen |
| 6-bot game | Lobby of the looping 4-bot run, `bot run 6 7` | Before its game: no first-run bot online or in roster, no stale `Bot_` scoreboard entry, no first-run bot data/stats/advancements file; then same phase and line checks | fails naming what was left |
| Seed replay | Repetitions 2 and 3 | Per run, bot name → entrance pick (guards: none) equals repetition 1's | fails with both maps |
| Logged exception | Any captured line with a throwable or at ERROR | Repetition fails, listing them | N/A |

</frozen-after-approval>

## Code Map

- `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6BotRunIT.java` -- `runsLoopAndASecondRunStartsClean` holds the clean-second-run check (save then check old files in the command's tick; roster `Bot` identity, since names and offline UUIDs repeat; stale `Bot_` scoreboard entries). Move that half into the soak test; keep a loop test here (`bot run 4`, `game=1` then `game=2`). Helpers to share: `shortTimersWithoutKoths`, `awaitGameEnded`, `gameEndedLines`, `GAME_ENDED`, `savedFiles`, `run`, `dispatch`, `game()`, `GAME_TIMEOUT_TICKS` (600).
- `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6BotPreparationIT.java:82-92` -- reading picks: `(PhaseBrain) vi6Bots.getBrain(bot)` → `((PreparationBrain) brain.preparation()).pickedEntrance()`; null for guards. The pick stays set until the next preparation, so read it while infiltration is current.
- `src/integrationTest/java/fr/nekotine/vi6clean/it/LogCapture.java` -- `messages()`, `failures()`, `errors()`, `clear()`.
- `PaperServer.awaitUntil(condition, ticks)` -- condition runs on the main thread each tick: use it to sample the phase (`getPhase(Vi6PhaseLobby|Vi6PhasePreparation|Vi6PhaseInfiltration|Vi6PhaseInMap)`) until the game's line appears.
- `src/main/java/fr/nekotine/vi6clean/bot/Vi6Bots.java` -- one `Random` reseeded by `bot run`; bots tick in roster order, so a seed fixes picks. Read only.

## Tasks & Acceptance

**Execution:**
- [x] Shared IT support class from retro item 6 (re-read its name and contents first) -- add the run helpers listed in the Code Map, skipping any it already has -- one copy for both ITs.
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6SoakIT.java` -- new soak test per the Intent and matrix; static map of repetition-1 picks; at least 2 thief spawns on the test map asserted, so the replay check means something.
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6BotRunIT.java` -- use `Vi6ItSupport`; reduce the first test to the loop check.
- [x] `_bmad-output/implementation-artifacts/deferred-work.md` -- remove the Story 4.4 seed-replay entry once the soak test covers it.

**Acceptance Criteria:**
- Given the full IT suite, when `gradlew integrationTest` runs 3 times in a row, then every IT passes each time.
- Given `bots.seedRandom(seed)` removed from `/bot run`, when the soak test runs, then it fails (checked once by hand, not committed).

## Implementation Notes

- The shared IT support from retro item 6 is `Vi6TestHooks` (`game()`, `awaitPhase`) plus `Vi6Asserts`; there is no `Vi6ItSupport`. The run helpers (`GAME_TIMEOUT_TICKS`, `GAME_ENDED`, `shortTimersWithoutKoths`, `awaitGameEnded`, `gameEndedLines`, `savedFiles`, `run`, `dispatch`) went to `Vi6TestHooks`, next to `awaitPhase`; `Vi6Asserts.assertCleanLog(capture)` replaces the failures/errors pair. `Vi6BotBrainIT` and `Vi6BotCommandsIT` keep their own `run` copies (out of scope).
- Sampling and the second run's lobby dispatch run in `Vi6SoakIT.everyTick`, a task that reschedules itself one tick later after each check, so it runs every tick after the game's own timers (`PaperServer.awaitUntil` sleeps 50 ms between checks and skips ticks; a repeating `runTaskTimer` task runs before those timers and never saw the looping run's lobby). Per-tick sampling still may not see the lobby a game returns to: the looping run's lobby brains ready up at once, so the lobby can pass between two samples, and the next game can reach preparation before `Vi6Run`'s line task runs. The test samples each tick and requires the collapsed sequence to start lobby, preparation+in-map, infiltration+in-map, followed by nothing, lobby, preparation+in-map, or both. The return to the lobby is then proven from the captured `PhaseMachine` lines: the phases set up between the start and the game's line must be exactly InMap, Preparation, Infiltration, Lobby.
- `Player.isOnline()` on a first-run bot's old player object is true again once a second-run bot takes its offline UUID, so "no first-run bot online" checks identity: every online player is one of the new roster's players and none is a first-run player object.
- Mutation check (by hand, not committed): with `bots.seedRandom(seed)` commented out in `Vi6BotCommands`, repetitions 2 and 3 fail with "the same seed gave other picks in the 4-bot run".

## Spec Change Log

## Review Triage Log

Review pass 1 (blind, edge-case, verification-gap layers).

| # | Layer | Finding | Verdict | Evidence | Route |
|---|-------|---------|---------|----------|-------|
| 1 | blind, edge-case | `SETUPS` equality fails when the next game sets up InMap/Preparation before game 1's line | false | `Vi6Run.onRoundEnd` logs the line in a `runTask` the tick after the round ends, while the game is in the lobby; preparation needs the `LobbyBrain` commands, which land after a tick. `AFTER_GAME_PHASES` only absorbs sampling lag. | reject |
| 2 | blind | Second run waits on a lobby sample that can be missed every time until timeout | medium | `PaperServer.awaitUntil` runs `onMain` then sleeps 50 ms (about one sample every 2 ticks); the looping lobby lasts about 1-2 ticks. | patch |
| 3 | blind | Phases are not sampled each tick, as the Javadoc and Design Notes say | low | Same `awaitUntil` cadence; same root cause as #2 (sampling through `awaitUntil`). | patch (with #2) |
| 4 | blind, edge-case | `FIRST_PICKS` takes whichever repetition checks first as the baseline; a lone repetition compares nothing | low | Real, but only after repetition 1 already failed the class or when running a filtered single repetition; the fix adds a `RepetitionInfo` guard branch. | reject |
| 5 | blind, verification-gap | Tasks still name `Vi6ItSupport` | low | The fix is a spec edit; the Implementation Notes already record the real class names. | reject |
| 6 | blind | `Vi6BotMixedLobbyIT` keeps a private `run`, and five ITs repeat the `awaitTicks(40)` config-load wait | low | Pre-existing copies, not made by this change. | defer |
| 7 | blind, edge-case | `check` waits for game 1's line again and wraps the exception without context | low | `awaitEnd` already waited for the same line; `new AssertionError(e)` drops the phases/log message. | patch |
| 8 | blind | `Vi6TestHooks` Javadoc covers only set-up hooks; the new run helpers have mixed visibility | low | The spec's Always rule (start games through `/bot run`, not set-up hooks) is harder to follow when one class holds both without saying so. | patch |
| 9 | blind | The `SETUP` regex depends on NekotineCore `PhaseMachine`'s French log text | low | A rewording there fails the soak test (loudly) with no pointer to the source. | patch (source comment) |
| 10 | blind | No recorded results for the acceptance criteria | low | The fix is a spec edit; the results are reported with the PR. | reject |
| 11 | blind | The retro item 6 `done` flip in `sprint-status.yaml` belongs with #99 | low | The item shipped in #99; the flip goes in its own `docs(sprint)` commit, apart from the story commit. | patch (commit split) |
| 12 | edge-case, verification-gap | The stale `Bot_` scoreboard check cannot fail for first-run bots | medium | The second run reuses the names `Bot_1`..`Bot_4`, and all are online again when the filter runs. The check moved verbatim from `Vi6BotRunIT`; `Vi6TestHooksIT` pins scoreboard cleanup per name after `reset()`. | defer |

## Design Notes

Repeating in one boot proves a third (and later) run starts clean and reaches a result; running the task 3 times proves it across fresh boots. Phase sampling per tick is enough: preparation lasts at least the ticks for the brains' commands to land, and the other phases last seconds.

## Verification

**Commands:**
- `gradlew.bat` -- expected: Spotless applied, shadow jar built.
- `gradlew.bat build` -- expected: BUILD SUCCESSFUL.
- `gradlew.bat integrationTest` (3 times) -- expected: all ITs pass each time, `Vi6SoakIT` 3/3 repetitions.
