---
title: 'Story 2.4: Bots fill a lobby with humans'
type: 'feature'
created: '2026-11-11'
status: 'done'
route: 'dispatch'
baseline_commit: 'a015792e6b72bf7bd21197526167c6f427152b8b'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Stories 2.1-2.3 prove bots alone get through the lobby, but nothing checks a mixed lobby: 1 human plus 3 `any` bots must balance to 2v2 and advance to preparation, bots must stand idle in the round without errors, and an operator removal mid-round must be handled as a player quit (FR1, FR5, NFR11).

**Approach:** An integration test stands in for the human with a brainless bot that sends `/game team` and `/game ready` itself, adds 3 `any` bots with `/bot add 3 any`, checks 2v2 and the move to `Vi6PhasePreparation`, lets the round run a few seconds with no new error in the log, then removes the 3 bots with `/bot remove all` and checks they left the player list, the teams and the scoreboards while the stand-in stays. The real-client check runs on the dev server by the operator, following a procedure written in this spec; its result goes in the completion notes.

## Boundaries & Constraints

**Always:** bots act only through player commands; no file outside `fr.nekotine.vi6clean.bot` checks for a bot. The IT uses bots only as a driver and resets the game to the lobby with no bots after each test.

**Never:** no change to Vi6's lobby, dialog or team rules; no NekotineCore change; no tool use, shop or movement by bots (later epics).

**Decision (human-team switch):** bots do not rebalance when a human changes team after they picked; the human rebalances themselves if they want to. Bots keep picking once per lobby (Story 2.3). The dev-server procedure has the human pick a team (or keep the one they got on join) before `/bot add`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Human keeps join team | human on guard (join balance), `/bot add 3 any`, human readies | 2v2, preparation | N/A |
| Human picks thief first | human `/game team thief`, then `/bot add 3 any`, human readies | 2v2, preparation | N/A |
| Mid-round removal | preparation running, `/bot remove all` | bots gone from player list, teams, scoreboards; human stays; no error logged | N/A |

</frozen-after-approval>

## Code Map

- `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6BotBrainIT.java` -- reuse its `run`, `awaitPreparation`, `teams` helpers and `@AfterEach` reset (remove bots, `goTo(Vi6PhaseLobby)`).
- `src/integrationTest/java/fr/nekotine/vi6clean/it/LogCapture.java` -- `errors()` / `failures()` for "no exception in the log".
- `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6BotLobbyIT.java` -- how 2.1 asserts a quit cleared teams and scoreboards (Garde/Voleur teams of `Vi6Game.getScoreboard()`).
- `src/main/java/fr/nekotine/vi6clean/bot/LobbyBrain.java` -- `any` counts other players' current teams; a brainless stand-in counts by its current team. Do not change.
- `src/main/java/fr/nekotine/vi6clean/Vi6Commands.java` -- `/game team <guard|thief>`, `/game ready` (toggles).
- `src/main/java/fr/nekotine/vi6clean/game/Vi6Game.java:162` -- join balance: a lone player becomes a guard.
- `src/main/java/fr/nekotine/vi6clean/game/phase/Vi6PhaseGlobal.java:84` -- quit → `Vi6Game.removePlayer`.
- `src/main/java/fr/nekotine/vi6clean/game/phase/Vi6PhaseLobby.java:136` -- `checkForCompletion`: all ready → complete.

## Tasks & Acceptance

**Execution:**
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6BotMixedLobbyIT.java` -- new IT: spawn stand-in `Human_1` via `BotRoster.spawn` with no brain; cases from the I/O matrix (stand-in keeps guard; stand-in sends `/game team thief` first); stand-in readies last with `bot.sendCommand("game ready")`; assert 2v2 and preparation; capture the log over ~40 ticks of preparation and assert no error lines; remove the 3 bots with `/bot remove Bot_N` (the stand-in is a roster bot, so `remove all` would take it too) and assert they left the player list, teams and Garde/Voleur scoreboard teams while the stand-in is still in the game -- automated mirror of the dev-server check.
- [x] this spec -- add the dev-server procedure under Verification and, after the run, its result in Implementation Notes.

**Acceptance Criteria:**
- Given the mixed-lobby IT, when `gradlew integrationTest` runs, then every case passes and earlier ITs stay green.
- Given the dev server with 1 human, when the procedure runs, then the result (2v2, preparation, idle bots, clean log, mid-round removal) is recorded in the completion notes.

## Implementation Notes

- `Vi6BotMixedLobbyIT` covers both I/O rows (stand-in keeps guard; stand-in sends `/game team thief` first), each `@RepeatedTest(3)`. A log capture opens before `/bot add 3 any` (whose 3 reply lines are checked exactly) and asserts no error or exception through the add, the picks, the move to preparation, 40 idle ticks and the removal. The test waits until all 3 bots are ready, checks the lobby still waits for the stand-in, then the stand-in readies: preparation, 2 guards and 2 thiefs, stand-in's team unchanged. `/bot remove Bot_1..3` (each replies `1 bot(s) retiré(s)`) keeps preparation active and takes the bots off the player list, the guard/thief lists and the Garde/Voleur scoreboard teams; the stand-in stays online and in the game. `gradlew.bat integrationTest --tests Vi6BotMixedLobbyIT`: 6/6 green.
- Dev-server check (real client), 2026-11-11, jar built from `bot` with Story 2.3 (bots self-test passed at boot), commands over RCON, the human on a real client: **passed**.
  - 1 human in the lobby, auto-assigned Garde, kept that team. `/bot add 3 any` added Bot_1..3; each sent `/game team …` then `/game ready` as player commands. Teams: Garde = human + Bot_2, Voleur = Bot_1 + Bot_3 (2v2). The lobby held until the human readied.
  - The human readied: the lobby tore down and `Vi6PhaseInMap` then `Vi6PhasePreparation` set up (00:37:41). Bot_2 stood idle in the guard room with the human.
  - About 80 s of idle round: all 3 bots online and live in `/bot list`; 0 warn/error/exception lines in the log from `/bot add` on.
  - `/bot remove all` mid-preparation: `3 bot(s) retiré(s)`; each bot went through the normal quit ("lost connection: Bot removed", "left the game", roster "left"). The human saw them leave the tab list and the team colours and stayed in the round as Garde; no phase change, still 0 warn/error/exception lines.

## Spec Change Log

## Review Triage Log

Review pass 1 (2026-11-11): Blind Hunter (B), Edge Case Hunter (E), Verification Gap (V).

| # | Finding | Verdict | Evidence | Route |
|---|---------|---------|----------|-------|
| V1, B1, E3, E10 | Log capture opens after preparation starts, so the transition is never checked | medium | `new LogCapture()` sits after the `awaitUntil(Preparation)`; preparation setup errors would pass | patch |
| B2, E2, E9, V-other | "Lobby waits for the stand-in" check never verifies the bots are ready | low | Fixed 5-tick wait, no ready assertion; the check could pass for the wrong reason | patch |
| B3 | The stand-in's team is not asserted unchanged | low | Only 2/2 counts; the decision (no rebalancing around the human) is unchecked | patch |
| B4, E5 | Phase not checked around the mid-round removal | low | `Vi6Game.removePlayer` only drops the player; preparation runs on a timer, so asserting it survives is valid | patch |
| B5, E4 | `/bot remove` replies ignored; `players.get` NPEs on an unexpected name | low | Replies unasserted; NPE gives an unclear failure | patch |
| E1 | Partial `bot add` passes the line-count check | low | An error line counts as one of 3 lines | patch |
| B7 | Balancing cases run once; same-tick picks are order-sensitive | low | `Vi6BotBrainIT` repeats its balancing run 3 times for this reason | patch |
| B6 | Helpers copied from `Vi6BotBrainIT` instead of shared | low | Real drift risk, but extracting a shared helper across 3 ITs is beyond a direct fix; ITs already each carry `run` | reject |
| E8 | Matrix row says `/bot remove all`; the IT removes by name | low | Both go through the same filter (`Vi6BotCommands.java:115`, `all` or name); `all` would take the stand-in; the dev-server procedure runs `all` | reject |
| E6 | Stale players before the stand-in joins | low | `@AfterEach` removes every bot and returns to the lobby; the roster-empty check is already there | reject |
| E7 | `@AfterEach` stops if one remove throws | low | Same reset as `Vi6BotBrainIT`; `roster.remove` has not thrown in any run | reject |
| B8 | `/bot list` cannot show 2v2 (it lists roster bots only) | low | True; fix is a spec edit | reject (procedure wording corrected) |
| B9 | Manual procedure covers one I/O row | low | Fix is a spec edit; the IT covers both rows | reject |
| B10 | Spec and sprint statuses disagree; task half done | false | `in-review` vs sprint `in-progress` is the workflow's order; the open task is the dev-server run | reject |
| B11 | "Completion notes" vs "Implementation Notes" | low | Fix is a spec edit; the epic's AC wording | reject |
| B12 | Code Map omits `Vi6BotCommands.java` and `Vi6Game.java:274` | low | Fix is a spec edit | reject |

## Verification

**Commands:**
- `gradlew.bat` then `gradlew.bat build integrationTest` -- expected: green, the new IT included.

**Manual checks (dev server, real client):**
- Human joins; picks a team or keeps theirs. Operator runs `/bot add 3 any`, then checks the Garde/Voleur colours in the tab list (`/bot list` shows only the 3 bots): 2v2. Human readies: preparation starts. Wait ~1 minute: bots idle, no exception in the console. Operator runs `/bot remove all` (only bots are in the roster here): bots leave the tab list and the team scoreboards, the human stays in the round. Record the outcome here.
