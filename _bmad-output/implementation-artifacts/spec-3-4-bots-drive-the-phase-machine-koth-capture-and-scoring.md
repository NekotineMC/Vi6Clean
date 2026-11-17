---
title: 'Story 3.4: Bots drive the phase machine, KoTH capture and scoring'
type: 'feature'
created: '2026-11-17'
status: 'done'
baseline_commit: '7241b61786e703b32594836f7202e40ed405bbc4'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Phase transitions, KoTH capture, artefact theft, escape and the end of a round have no regression test driven by real client inputs (FR5, FR19, NFR10).

**Approach:** One integration test class, `Vi6PhaseMachineBotIT`, with three `@RepeatedTest(3)` tests on hook-built games and short timers: a full round from the lobby ended by the lost timer; a thief bot capturing a forced KoTH; a thief bot stealing an artefact and escaping, with a second thief escaping to end the round. Bots act only through `Bot` controls. Test code, plus a Vi6 fix only if a test exposes a bug.

**Decision (result in the log):** `Vi6PhaseInfiltration` logs one INFO line when a round ends: why it ended (lost timer, or every thief left) and each artefact's state (safe, stolen, escaped, lost). Story 4.4 extends it with winner and duration. `LogCapture` also records INFO messages so tests can assert the line.

## Boundaries & Constraints

**Always:** zones are entered by moving: `Vi6TestHooks.place` the bot on a standable spot inside the box, then a real step (`setForward` for a few ticks) fires the `PlayerMoveEvent` Vi6 listens to; a thief enters the map the same way, through an entrance trigger box. Every test starts and ends with `Vi6TestHooks.reset()`; KoTHs are forced (`forceKoths(...)`, or none) so nothing random affects results; timers shortened through `ConfigManager` setters; phases checked with `getPhase(...)`; no error logged (`LogCapture`).

**Never:** no fabricated events, no reflection into private fields, no direct calls to wrapper or phase methods to cause what is under test (`thiefEnterInside`, `capture`, `thiefLeaveMap`, `complete`...), no change to capture amounts. No NekotineCore change.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Full round | 2 guards + 2 thieves via hooks, no KoTH, prep 2 s, lost 3 s, end-spectator 0.5 s; each bot sends `/game ready` | lobby → preparation → infiltration (in-map present) → lobby; thieves never enter; round result in the log | no ERROR logged |
| KoTH | 1 guard + 1 thief, one KoTH forced with `regen`; thief enters, steps into the KoTH box, stays | KoTH owning team goes GUARD → THIEF within capture amount (200 ticks) + margin | N/A |
| Steal | 1 guard + 2 thieves, no KoTH, lost 30 s, capture/escape delays 0.5 s; thief A enters and stands in an artefact box, no guard inside | `ArtefactStealEvent` once for A and that artefact; thief board score 2, guard board entry in `guardUnknown`; thief boss bar progress falls back near 0 | N/A |
| Escape | after the steal, A steps into an exit; then thief B (entered) steps into an exit | after A: thief board score 1, guard board entry in `guardEscaped`, phase still infiltration; after B: round ends, back to lobby | N/A |

</frozen-after-approval>

## Code Map

- `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6TestHooks.java` -- reuse `reset`, `spawnBots`, `putOnTeams`, `goToInfiltration`, `forceKoths`, `place`, `currentMap`, `thiefSpawn`. No game logic added here.
- `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6PhaseDurationsIT.java` -- `awaitPhase` (records `Bukkit.getCurrentTick()`), short-timer setters, `LogCapture` pattern. `PortableTeleporterBotIT` -- tick-based waiting, `@AfterEach` reset.
- `src/main/java/fr/nekotine/vi6clean/game/phase/Vi6PhaseInMap.java` -- `onPlayerMove` (move events only, a teleport does not count): thief ENTERING + dest in an entrance trigger box → `thiefEnterInside`; inside + `canLeaveMap` + dest in an exit → `thiefLeaveMap(ALIVE)`; inside + `!canLeaveMap` cancels moves into entrance blocking boxes and exits; fills artefact and KoTH zone sets. `onTick` ticks artefacts and KoTHs. Scoreboard objectives `thiefArtefactListing` (safe 3, stolen 2, escaped 1, lost 0) and `guardArtefactListing` with teams `guardSafe`/`guardUnknown`/`guardStolen`/`guardEscaped` on `Vi6Game.getScoreboard()`.
- `src/main/java/fr/nekotine/vi6clean/wrapper/InMapPhasePlayerWrapper.java` -- entering schedules `canLeaveMap` after `delay-before-escape` and `canCaptureArtefact` after `delay-between-capture` (read when the wrapper is built, so set them before `goToInfiltration`).
- `src/main/java/fr/nekotine/vi6clean/map/artefact/Artefact.java` -- capture +SCALE per tick per thief that can capture, any guard inside cancels, 200 needed; then fires `ArtefactStealEvent`.
- `src/main/java/fr/nekotine/vi6clean/map/koth/Koth.java` -- default owner GUARD, 200 needed, only players whose wrapper `isInside()` count, so the capturer is an entered thief; `getOwningTeam()`. Map content is reloaded from file each game, no carry-over.
- `src/main/java/fr/nekotine/vi6clean/game/phase/Vi6PhaseInfiltration.java` -- lost timer `stealDurationTicks`, reset by `onArtefactSteal`, shown as boss bar progress each second (`Player.activeBossBars()`); `checkForCompletion` ends the round when every thief has left.
- `src/integrationTest/java/fr/nekotine/vi6clean/it/LogCapture.java` -- records ERROR+ only.
- `Bot.sendCommand("game ready")` toggles lobby readiness (as `LobbyBrain` does).
- Test map `solar_industries` (`test-bundle/.../SolarIndustries/Vi6Map.yml`): entrance trigger boxes are 1 block tall at floor level, blocking boxes just above.

## Tasks & Acceptance

**Execution:**
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6PhaseMachineBotIT.java` -- the three tests of the matrix. Helpers: `standableSpot(BoundingBox, excluded block)` scanning block-centre columns from the box centre outward (ray down from `maxY`, solid top within `[minY, maxY)`, feet and head passable), failing with the box in the message; `stepInto(bot, box)` = place + forward 3 ticks + release + settle; `enterMap(thief)` through the entrance of the spawn's name; zones chosen first by name (KoTH and artefact boxes containing no guard spawn). Steal event recorded by a listener registered and unregistered in `try/finally`. Durations measured with `Bukkit.getCurrentTick()`.
- [x] `src/main/java/fr/nekotine/vi6clean/game/phase/Vi6PhaseInfiltration.java` -- in `checkForCompletion`, when completion starts, keep the end reason; in the completion task, right before `complete()`, log one INFO line through a `NekotineLogger` (`Vi6PhaseInMap` has one): end reason and each artefact's `name=state`, the state read from the thief objective's score (3 safe, 2 stolen, 1 escaped, 0 lost) -- the round's result, reused by Story 4.4.
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/LogCapture.java` -- also record formatted messages of every level (e.g. `messages()`), keeping `failures()` unchanged.
- [x] Full-round and escape tests assert the result line once (reason, every artefact listed; escape test: the stolen artefact `escaped`).

**Acceptance Criteria:**
- Given `Vi6PhaseMachineBotIT`, when `gradlew integrationTest` runs, then each test passes 3 times and every other IT stays green.
- Given the test classes, when run in either order, then results are the same.

## Implementation Notes

## Spec Change Log

- Review 1 (blind hunter, edge-case hunter): the result line was logged when completion starts, before the lost-timer loop marks the other thieves `LOST` and before a dead thief's delayed `thiefObjectiveLost`, so lost artefacts could read `stolen`. Amended the `Vi6PhaseInfiltration` task: keep the reason at completion start, log in the completion task before `complete()` (still "when a round ends", per the intent). Patched in place, no re-derivation; everything else KEEP as built.

## Review Triage Log

| # | Source | Finding | Verdict | Evidence | Route |
|---|--------|---------|---------|----------|-------|
| 1 | BH, ECH | Result line written during the first thief's `LOST` leave of the lost-timer loop; later thieves' artefacts logged `stolen` | medium | `onTick` loops `thiefLeaveMap(LOST)`, each ends in `checkForCompletion`, the first logs; Story 4.4 reuses the line | patch |
| 2 | BH, ECH | Dead thief (3 s Deadringer delay) logged `stolen` if the round ends inside the delay | medium | `DEAD` sets `LEFT` at once, defers `thiefObjectiveLost`; same root cause as 1, fixed by logging after end-spectator time (5 s default) | patch (with 1) |
| 3 | VG, BH | No test asserts `stolen` or `lost` in the result line | medium | pre-verified gap; needs a thief death or a thief inside at timer end | defer (Story 4.4) |
| 4 | BH | `thiefScore` NPEs without a message if the objective is missing | low | direct correction, `assertNotNull` | patch |
| 5 | BH, ECH | Reason `all-thieves-left` also covers rounds where every thief died | low | intent names exactly two reasons (lost timer, every thief left); per-thief outcome is Story 4.4's winner work; enum adds complexity | reject |
| 6 | BH | Score-to-state mapping split across classes; test reads objective by literal name; `unknown(n)` default | low | developer-only, enum refactor exceeds a direct correction; test reading the scoreboard is the outside view | reject |
| 7 | BH | "Thieves never enter" check is trivially true and may skip | low | it documents the matrix row; the phase is still infiltration 30 ticks into a 60-tick timer | reject |
| 8 | BH | Boss bar found by the `Volez` text | low | brittle only to a text change, fails loudly; fix needs new public surface | reject |
| 9 | BH, ECH | `progressBefore > 0.25` margin is thin | false | tick-based: ≥ 190 ticks of a 600-tick timer minus ≤ 20 ticks bar lag gives ≥ 0.28, independent of machine speed | reject |
| 10 | BH, ECH | `stepInto` with no or little room walks into a wall or out of the box | low | every caller asserts containment or `isInside` right after, so it fails loudly; 9/9 green on the test map | reject |
| 11 | BH | KoTH `regen` effect not asserted | false | matrix row only requires the owner change | reject |
| 12 | BH | KoTH `stepStart` read before place and step loosens the lower bound by ~8 ticks | low | upper bound is the regression signal; capture cannot precede entry | reject |
| 13 | BH | `LogCapture.messages()` unbounded, prefix could match another logger | low | captures only for one test's duration; no other logger emits `Round ended: ` | reject |
| 14 | BH | Log message built by concatenation; format duplicated in test | low | once per round; the test asserts the exact format on purpose | reject |
| 15 | VG | Same-tick timer and last leave logs `lost-timer` | false | when the timer fires, `onTick` marks every remaining thief `LOST`, so `lost-timer` is the true cause | reject |
| 16 | VG | `integrationTest` not part of `build` | false | pre-existing, documented in AGENTS.md | reject |

## Design Notes

"No further hook calls" in AC 1 is read as: after the hooks set the bots, teams, timers and an empty KoTH override, only the bots act (each sends `/game ready`); preparation ends on its timer and thieves keep the random spawn Vi6 picks, which does not affect a round no thief enters.

"Lost timer restarts" is observed through the thief's boss bar, the only outside view of `stealDurationTicks`: above 0.25 just before the steal (about 10 s of a 30 s timer), below 0.1 within 30 ticks after it.

AC 4's "game ends when every thief has left" needs two thieves so the first escape is shown not to end the round.

## Verification

**Commands:**
- `gradlew.bat` -- expected: Spotless applied, shadow jar built.
- `gradlew.bat build` -- expected: green.
- `gradlew.bat integrationTest` -- expected: all ITs green, `Vi6PhaseMachineBotIT` 9 runs.
