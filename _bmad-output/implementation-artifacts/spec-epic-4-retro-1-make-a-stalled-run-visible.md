---
title: 'Epic 4 retro item 1: a player leaving no longer stalls a run'
type: 'feature'
created: '2026-11-22'
status: 'done'
route: 'oneshot'
review_loop_iteration: 1
baseline_commit: 'd129d43652ca113a39660e83da923251a000866c'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-retro-2026-11-22.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** An unattended `/bot run` hangs in the lobby forever when a player leaves while everyone left is already ready: nothing re-runs the completion check (Epic 4 retro F1, action item `epic-4-retro-item-7-before-epic-5-make-a-stalled-run-visible`).

**Approach:** A player leaving during the lobby or preparation re-runs that phase's completion check one tick later, only if that phase is still the active one and the game still has a player. `Vi6GameReset.clearBots()` leaves every remaining player unready in the lobby, so removing bots (including the `/bot run` rollback) never starts a game for the humans left.

**Decision (user, 2026-11-22):** no stall warning. The first plan's `Vi6Run` watchdog was dropped after review showed the window "lobby delay + preparation + lost timer + margin" is not a bound: the lost timer restarts at each steal, so healthy games would warn. The other causes of a stall (a bot whose brain threw, a human who never readies) stay silent; the retro's open question 1 stays open.

</frozen-after-approval>

## Implementation Notes

- Pass 1 (dispatch route) built the watchdog too; its code is kept in a local stash (`epic-4-retro-1 pass-1 implementation`) and is not part of this change.
- `Vi6PhaseGlobal.onPlayerLeft`: after `removePlayer`, if the lobby or preparation is active, a task one tick later calls that phase's `checkForCompletion()` when the same phase instance is still active (`PhaseMachine` makes a new instance each time a phase starts) and the game still has a player. One tick later so the leave's own tick, and `clearBots()`'s `goTo(lobby)`, settle first.
- `Vi6GameReset.clearBots()`: after `goTo(Vi6PhaseLobby)`, every remaining player's `LobbyPhasePlayerWrapper` is set unready. `PhaseMachine.goTo` returns early when the phase is already current, so without this a human who was ready before `/bot run` stayed ready, and the bots' quits completed the lobby for them alone if no bot was re-added (the rollback path).
- Tests: new `Vi6LeaveCompletionIT` (lobby unblocked on the next tick and not in the leave's tick, preparation unblocked, emptied lobby stays). The `clearBots()` unready step has no IT: the harness cannot put a non-bot player online, and `clearBots()` removes every bot.

## Spec Change Log

- Pass 1 review → user decision: removed the watchdog, the `bots.run-stall-margin-seconds` key and their tests (triage row 1). Amended the Approach so `clearBots()` unreadies the lobby (row 2; the old Code Map forbade touching `clearBots`). KEEP: the deferred check in `Vi6PhaseGlobal.onPlayerLeft` with both guards and the phase-instance identity check, and the ITs `aLeaveUnblocksTheLobby`, `aLeaveUnblocksPreparation`, `anEmptiedLobbyStays`. Known-bad state avoided: false `Run stalled` WARNs in games with steals; a solo game after a rollback.

## Review Triage Log

Pass 1 (blind B, edge-case E, verification-gap VG; diff `d129d43..` working tree):

| # | Finding | Verdict | Evidence | Route |
|---|---|---|---|---|
| 1 | B1, E1, E2, E7: the window is not the longest a game can take | medium | `Vi6PhaseInfiltration.onArtefactSteal` sets `stealDurationTicks = 0` (line 263), so infiltration lasts up to (steals+1) x lost timer; completion waits `max(endSpectator, pendingDeathDelay+1)` (line 204). One steal after 60 s on the real config gives a false WARN | intent_gap (the formula is in the frozen Approach) |
| 2 | Reviewer: `clearBots()` in the lobby with a ready human and no respawn (the `/bot run` rollback) starts a solo game | medium | `PhaseMachine.goTo` returns early when the phase is already current (`PhaseMachine.java:65-67`), so the lobby instance and the human's ready flag survive, and the deferred check completes it; the matrix row says no game starts | bad_spec (the Code Map said not to touch `clearBots`) |
| 3 | B2, B3, VG1: `runsLoop`'s no-stall check uses the 60 s margin, so the window restart is never exercised | medium | 2 short games take about 13 s, while the first window is about 66 s | patch (moot: loopback) |
| 4 | B6, E4: `windowTicks / 20` truncates 7.5 s to `7s` | low | integer division | patch (moot: loopback) |
| 5 | B4, VG2: `phase=preparation` branch of the WARN untested | low | only the lobby line is asserted | defer |
| 6 | B5: infiltration stall prints `unready=[]` | low | true; adding detail adds branches | reject |
| 7 | B7: identity-skip on reset untested | low | covered once finding 2 is fixed with its own test | reject |
| 8 | B8: a quit by a player not in the game reschedules the check | low | the list is unchanged, so the check only completes an all-ready lobby that would already have completed | reject |
| 9 | B9: spec missing from the diff; no tracking update | false | the spec is passed to the edge-case layer by design; tracking is updated at the end of the workflow | reject |
| 10 | E3: a skipped `Game ended` leaves the old window running | low | it happens only if infiltration is still active a tick after the round end, which is itself a stall worth the WARN | reject |
| 11 | E5: `describeStall` throwing loses the WARN | low | the scheduler still logs the exception with a trace | reject |
| 12 | E6: the only thief or only guard leaving starts a one-team game | low | the ready path never checked team composition either; a no-thief infiltration ends at once and the run continues | reject |


Pass 2 (oneshot, blind reviewer only, on the reduced change):

| # | Finding | Verdict | Evidence | Route |
|---|---|---|---|---|
| 1 | The phase-instance check in `onPlayerLeft` is untested (pass-1 row 7's reason no longer holds) | low | true: no test ended the phase in the leave's tick | patch: `aLeaveChecksOnlyThePhaseItLeft`, which fails with the check removed (verified) |
| 2 | `aLeaveUnblocksPreparation` does not assert preparation survives the leave's tick | low | true | patch: assertion added |
| 3 | `clearBots()`'s unready step has no test | low | true; testing needs a new public lobby method or a non-bot player | defer |
| 4 | The unready loop NPEs if `goTo(lobby)` failed | low | a failed lobby setup is already logged as an error by `PhaseMachine`; the NPE only adds to it | reject |
| 5 | Nothing logs a leave-triggered completion; the spec slug still says "visible" | low | the phase setup lines already log the transition; the slug stays so the retro reference holds | reject |
| 6 | No team check before a leave-triggered start | low | same as pass-1 row 12 | reject |
| 7 | An emptied preparation runs out its timer | false | unchanged by this change: before it, the same timer ran out | reject |
| 8 | `anEmptiedLobbyStays`'s ready flag is irrelevant | low | test clarity only | reject |
| 9 | Each test resets twice | false | the same before/after pattern as `Vi6BotRunIT` | reject |
| 10 | Javadoc wording of `onPlayerLeft` and `clearBots()` | low | direct correction | patch |
