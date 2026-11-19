---
title: 'Story 4.4: The operator starts an unattended run with one command'
type: 'feature'
created: '2026-11-19'
baseline_commit: '79de1daf267a09aee9f689ab0c81dce839869350'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A bot-only game takes several commands (`/bot remove all`, `/bot add`, `/game lobby`), leaves earlier bots and scoreboard entries behind, and logs no per-game summary, so an unattended run cannot be repeated or checked (FR13, FR14).

**Approach:** Add `/bot run <count> [seed]`. It refuses unless the game is in the lobby and the count is 4 or 6. It resets through the shared `Vi6GameReset`, reseeds `Vi6Bots`'s random source, adds the bots with intent `any`, and calls `Vi6Game.start()`. Games then loop on their own until the next run or reset; each time a game of the run returns to the lobby, Vi6 logs one summary line.

**Decisions (human, 2026-11-20):**
- No `winner` field: Vi6 has no winner concept and the end reason plus counts prove a result. This departs from the epic's "winner" wording on purpose.
- Games loop: the run has no end of its own; bots keep playing until the next `bot run` or a reset, one summary line per finished game.
- Humans may be online. The reset removes bots only; humans stay in the game and the lobby waits for them to ready up. When humans + count exceed 6, the run adds only the bots that fit (`6 - humans`); if none fit, it is refused.

## Boundaries & Constraints

**Always:** Lifecycle only through `BotRoster`; brains unchanged (Story 4.3); no bot check in game logic; one reset implementation in main source, which the test hook calls. Lines are INFO, one line, `key=value`: `Run started: requested=…, added=…, seed=…` and `Game ended: game=<n>, reason=<lost-timer|all-thieves-left>, stolen=<n>, escaped=<n>, duration=<s>s`. The seed is logged so a run can be replayed.

**Never:** Change `/game ready|entrance|prepready`, phase timers or brain behavior. Put a command in the `bots` library. Log `Game ended:` outside a run. Write the soak test (Story 4.5).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Happy path | Lobby, no human, `bot run 4` | Leftover bots gone, 4 `Bot_n` with intent `any`, start line; game reaches a result; `Game ended: game=1, …` once back in the lobby; next game starts by itself | N/A |
| Loop | Run continues | `game=2` line after the next game | N/A |
| Wrong count | `bot run 5` / `bot run 2` | Red refusal; nothing changes | refused |
| Past lobby | Preparation or in-map | Red refusal; nothing changes | refused |
| Humans online | 1 human, `bot run 6` | Human kept, 5 bots added, `added=5` | N/A |
| No room | 6 humans online | Red refusal; nothing changes | refused |
| Bots unavailable | Roster disabled | Existing "Bots indisponibles" message | refused |
| Second run | Run looping, back in lobby, `bot run 6` | Clean roster (no old bot, scoreboard entry or bot file), counter restarts at `game=1` | N/A |
| Run aborted | Reset mid-game | No line for the aborted game; run cleared | N/A |
| Repeated `start()` | `/game lobby` twice | `Vi6Game`'s listener registered once | N/A |

</frozen-after-approval>

## Code Map

- `src/main/java/fr/nekotine/vi6clean/bot/Vi6BotCommands.java` -- `add` loop (unique `Bot_n` names, `roster.spawn`, `bots.setIntent`): extract a shared spawn helper for `run`; `availableRoster` for the unavailable case; `MAX_PLAYERS`.
- `src/main/java/fr/nekotine/vi6clean/bot/Vi6GameReset.java` -- today: remove roster bots, forget brains, `goTo(Lobby)`, clear forced KoTHs, `ConfigManager.tryLoad()`, reset removed names' scoreboard entries. Split: `clearBots()` (bots, brains, active run, lobby, scoreboard) for the run; `reset()` = `clearBots()` + forced-KoTH clear + `tryLoad()` for the test hook. The run keeps the server's live config and forced KoTHs, so Story 4.5 can set short timers before it.
- `src/main/java/fr/nekotine/vi6clean/bot/Vi6Bots.java` -- `seedRandom(long)`.
- `src/main/java/fr/nekotine/vi6clean/game/Vi6Game.java:138` -- `start()` calls `EventUtil.register(this)` every time; Bukkit stacks duplicate handlers. Unregister first.
- `src/main/java/fr/nekotine/vi6clean/game/phase/Vi6PhaseInfiltration.java:212,238` -- completion runs `logRoundResult` then `complete()`, which synchronously goes back to the lobby (`PhaseMachine.onPhaseComplete`, looping). Scores via `Vi6PhaseInMap.getThiefArtefactScore` (3 safe, 2 stolen, 1 escaped, 0 lost). Fire a Bukkit event with reason and scores inside the guarded log step, so game logic knows nothing of runs.
- `src/main/java/fr/nekotine/vi6clean/Vi6Main.java:74-90` -- module loading; load the run tracker.
- `src/integrationTest/.../Vi6TestHooks.java:195` -- `reset()` keeps calling `Vi6GameReset.reset()`. `Vi6BotCommandsIT` -- `Bukkit.dispatchCommand` pattern; `LogCapture` -- INFO and error capture.

## Tasks & Acceptance

**Execution:**
- [x] `src/main/java/fr/nekotine/vi6clean/game/phase/Vi6RoundEndEvent.java` + `Vi6PhaseInfiltration.java` -- new event (reason, artefact scores), fired in the guarded log step.
- [x] `src/main/java/fr/nekotine/vi6clean/bot/Vi6Run.java` -- new module/listener: active run (requested, added, seed, game counter, last lobby time); on the event, logs `Game ended:` once the lobby is current; `abort()` clears it silently.
- [x] `src/main/java/fr/nekotine/vi6clean/bot/Vi6BotCommands.java` -- `run` subcommand per the matrix; shared spawn helper.
- [x] `src/main/java/fr/nekotine/vi6clean/bot/Vi6GameReset.java` -- split as in the Code Map; `clearBots()` aborts the run.
- [x] `src/main/java/fr/nekotine/vi6clean/game/Vi6Game.java`, `Vi6Main.java` -- idempotent `start()`; load `Vi6Run`.
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6BotRunIT.java` -- new: short timers and forced empty KoTHs first; `bot run 4` reaches `Game ended: game=1`; a second `bot run 6` starts with no leftover bot, scoreboard entry or bot file and reaches its own `game=1`; refusals for count 5 and past the lobby; `/game lobby` twice keeps one handler; no logged error; `Vi6TestHooks.reset()` after each.

**Acceptance Criteria:**
- Given the full IT suite, when it runs, then every existing IT stays green.
- Given no `bot run`, when humans play, then nothing changes for them (no `Game ended:` line).

## Implementation Notes

- Summary line is logged one tick after the round-end event, once `Vi6PhaseInfiltration` is gone; `complete()` goes back to the lobby synchronously (`PhaseMachine.onPhaseComplete`), so this matches "the lobby is current" without missing the line when bots leave the lobby fast.
- A new bot reuses its name's offline UUID, so `Vi6BotRunIT` tells runs apart by roster `Bot` objects and checks old bot files in the command's tick.
- Matrix "Run aborted" row covered by `aResetMidGameAbortsTheRunSilently` (added in step 3). Humans-online and no-room rows are covered by reading only (no human in the harness); Story 4.6 exercises them.

## Spec Change Log

## Review Triage Log

| # | Layer | Finding | Verdict | Route / evidence |
|---|-------|---------|---------|------------------|
| 1 | blind, edge-case | Partial spawn in `bot run` clears the roster with no run-level message | low | patch: red "Run annulé" message |
| 2 | verification-gap | `Game ended:` stolen/escaped/reason only regex-checked; idle runs always give 0/0/lost-timer | medium | patch: exact line asserted in `Vi6PhaseMachineBotIT` escape scenario with a run begun |
| 3 | verification-gap, blind | No test asserts no `Game ended:` line without a run (AC) | medium | patch: negative assertion in a `Vi6PhaseMachineBotIT` round test |
| 4 | blind | `wrongCountsAreRefused` does not check the roster | low | patch: roster-empty assertion |
| 5 | verification-gap, blind | Seed reaching the brains' random source is not tested | medium | defer: replay check fits Story 4.5's soak test |
| 6 | verification-gap, blind | Humans-online, no-room and partial-spawn branches never run | medium | defer: harness has no human; spec assigns them to reading and Story 4.6 |
| 7 | blind | No operator documentation for `/bot run` | low | defer: the fit is AGENTS.md (agent-context file) |
| 8 | blind | Silent skip if infiltration is still current one tick after the event | low | rejected: only when `complete()` failed, which the phase machine already logs; a warning adds a branch |
| 9 | blind | Run marked active before `start()`, which can fail and close the game | low | rejected: a failed lobby goTo leaves the whole game closed and logged; rare |
| 10 | blind | `start()` registers the game after `close()` in its catch | low | rejected: pre-existing order; the change only adds an unregister |
| 11 | blind | `stolen` includes escaped and lost; negative scores counted | false | by design (Design Notes); scores are only 0-3 (`Vi6PhaseInMap` sets 3/2/1/0) |
| 12 | blind | `count` argument unbounded, no suggestions | low | rejected: matrix wants a red refusal message for 2 and 5, which bounds would replace with a parse error |
| 13 | blind | "Bots unavailable" row untested | low | rejected: shared `availableRoster` path, unchanged |
| 14 | blind | Second-run steps run inside the `awaitUntil` condition | low | rejected: deliberate, the old files must be checked in the command's tick (comment says so) |
| 15 | blind | `gameLobbyTwiceKeepsOneHandler` belongs in a game IT | low | rejected: placement only |
| 16 | blind | Spec and sprint status missing from the review diff | false | the review diff covers `src/`; both are committed with the change |
| 17 | verification-gap | `/bot add` partial failure now prints the error before the green lines | low | rejected: message order only, on a rare failure |
| 18 | edge-case | `bot add`/`remove` during a run leave it active with another roster | low | rejected: lines stay correct per game; operator-driven |
| 19 | edge-case | `/game lobby` mid-run adds the cut game's time to the next duration | low | rejected: operator-forced, rare; duration stays an upper bound |
| 20 | edge-case | `clearBots` NPE if `Vi6Run` is not loaded | false | `ModuleManager.get` loads the module when missing |
| 21 | edge-case | Duplicate artefact names collapse in the score map | false | `Vi6Map.getArtefacts()` is a `Map<String, Artefact>` keyed by name: names are already unique |
| 22 | edge-case | Code checks "infiltration gone", spec says "lobby current" | low | rejected: equivalent because `complete()` returns to the lobby synchronously (Implementation Notes) |

## Design Notes

`stolen` counts artefacts not safe (score < 3), `escaped` those at score 1. `duration` runs from the command (game 1) or the previous lobby return. The default seed is `System.nanoTime()`; `bot run <count> <seed>` replays. The harness has no human player, so the humans-online rows are covered by reading plus Story 4.6.

## Verification

**Commands:**
- `gradlew.bat` -- expected: Spotless applied, shadow jar built.
- `gradlew.bat build` -- expected: BUILD SUCCESSFUL.
- `gradlew.bat integrationTest` -- expected: new IT and all existing ITs pass.
