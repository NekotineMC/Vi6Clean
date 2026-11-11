---
title: 'Story 3.1: Phase durations can be shortened without changing the game for humans'
type: 'feature'
created: '2026-11-11'
status: 'done'
route: 'dispatch'
baseline_commit: '8c0aea01a695ed77e5604d077e80e183e1d86b12'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Phase timers are read from `ConfigManager` in field initializers (preparation 300 s, infiltration lost 240 s, plus capture/escape delays and end-of-game spectator time), so a test round takes real minutes (FR19, AD-13).

**Approach:** The config is the seam. Phases are rebuilt on every `goTo` and read the live config when they are built, so a test shortens a round by setting the durations through the existing Configurate setters before the phase starts, and restores them with `ConfigManager.tryLoad()` (nothing saves the in-memory config). An integration test runs a 4-bot round on shortened timers and checks each phase ends on its shortened timer.

**Decision (human, 2026-11-11):** no separate `PhaseDurations` module: phase durations are normal configuration. The config records store the five phase durations as fractional seconds (`double`) so tests can use sub-second values; the records still convert to and from `Duration` in their getters and setters, and the YAML keys stay the same.

## Boundaries & Constraints

**Always:** with no change, every duration equals `config.yml`, and `Vi6ConfigIT` stays green. The five durations of `game.preparation` and `game.infiltration` (`duration`, `lostDuration`, `delayBetweenCapture`, `delayBeforeEscape`, `endSpectatorTime`) are read when their phase or wrapper is built.

**Never:** no bot type or concept in main source for this, and no brain changes durations (AD-13). No change to default values or YAML key names. Mechanic timers (Majordom, asthma, suffocation) are out of scope. No NekotineCore change.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Default config | unchanged `config.yml` | phases use the configured durations | N/A |
| Fractional value | `duration-seconds: 1.5` in `config.yml`, or setter with 2750 ms | getter returns 1500 ms / 2750 ms | N/A |
| Shortened in memory | setters before the round, then `tryLoad()` | phases end on the shortened timers; reload restores the file values | N/A |

</frozen-after-approval>

## Code Map

- `src/main/java/fr/nekotine/vi6clean/configuration/{PreparationPhaseConfig,InfiltrationPhaseConfig}.java` -- the five `*Seconds` fields become `double`; getters `Duration.ofMillis(Math.round(s * 1000))`, setters `toMillis() / 1000.0`.
- `src/main/java/fr/nekotine/vi6clean/game/phase/Vi6PhaseInfiltration.java` -- `endSpectatorTime()` was read when the round ended; now captured in a field initializer like the lost timer.
- `src/main/java/fr/nekotine/vi6clean/game/phase/Vi6PhasePreparation.java`, `wrapper/InMapPhasePlayerWrapper.java` -- unchanged: already read config in field initializers.
- `src/main/java/fr/nekotine/vi6clean/configuration/ConfigManager.java` -- `tryLoad()` re-reads `config.yml`; `save()` is called by nothing outside it.

## Tasks & Acceptance

**Execution:**
- [x] Config records -- store the five durations as `double` seconds.
- [x] `Vi6PhaseInfiltration.java` -- capture end spectator time at construction.
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6PhaseDurationsIT.java` -- (a) fractional seconds read from `config.yml` and kept by the setter; (b) `@RepeatedTest(3)`: preparation 2 s, lost 3 s, end spectator 0.5 s via setters; `bot add 4 any`; asserts 2v2, preparation 40 ticks and infiltration 60 + 10 ticks (±5), no error logged. `@AfterEach`: `tryLoad()`, remove bots, back to lobby.

**Acceptance Criteria:**
- Given an unchanged `config.yml`, when phases start, then they use its durations, and `Vi6ConfigIT` passes unchanged.
- Given durations shortened in memory, when a 4-bot game runs, then each phase ends on its shortened timer and the game returns to the lobby, 3 times in a row.

## Implementation Notes

- Branch `feat/bots-3-1-phase-durations` off `bot`, PR NekotineMC/Vi6Clean#90.
- First version added a `PhaseDurations` module (commit 8ef196b); reworked on the human's call (see Spec Change Log).
- `Vi6PhaseDurationsIT.roundEndsOnShortenedTimers` clears the bots' brains once preparation starts: otherwise they ready up in the next lobby and start another round before the lobby can be observed. `bot add 2 thief` + `bot add 2 guard` does not work: the thieves ready up and start preparation alone.
- Verified after rework: `gradlew.bat` and `gradlew.bat build` green; `gradlew.bat integrationTest` 30 passed, 0 failed (incl. `Vi6ConfigIT`, 3 repetitions of the round test).

## Spec Change Log

- 2026-11-11, human review of PR #90: the epic assumed the config was immutable, but the records have setters and nothing saves the in-memory config. Amended: dropped the `PhaseDurations` module and its call-site changes; tests set durations through the config setters and restore with `ConfigManager.tryLoad()`; the five durations are stored as fractional seconds. Avoids a second source of truth for durations. KEEP: end spectator time captured at phase construction; round test asserting 2v2 and clearing brains before the next lobby.

## Review Triage Log

| # | Layer | Finding | Verdict | Route / evidence |
|---|-------|---------|---------|------------------|
| 1 | blind, edge | Positive override under one tick becomes 0 ticks (preparation bossbar `progress(n/0)` throws, thieves lost at once) | medium | patch: `override` rejects under `Tick.of(1)` |
| 2 | blind, edge | Huge override overflows `toIntExact` in a phase initializer | low | patch: rejected above `Integer.MAX_VALUE` ticks; both cases added to `invalidOverridesAreRejected` |
| 3 | edge | `END_SPECTATOR_TIME` read at completion, not construction, contradicting the Javadoc | medium | patch: captured in `endSpectatorTicks` field initializer |
| 4 | edge | `registerSingleton(moduleManager.get(...))` in `Vi6Main` would NPE if load failed | low | patch: `ModuleManager.load` already registers in Ioc; replaced with `tryLoad` |
| 5 | blind | New imports out of alphabetical order (Spotless does not sort) | low | patch |
| 6 | blind | Matrix row "edited config.yml" untested (no proof of live read) | medium | patch: `unoverriddenDurationsFollowAnEditedConfig` |
| 7 | blind | Round test relies on team balancing it never asserts | low | patch: asserts 2v2 at preparation; `bot add 2 thief` + `2 guard` was tried and fails (thieves ready and start preparation alone), so `4 any` stays |
| 8 | blind | Overrides leave no trace if forgotten | low | patch: each override logs a warning |
| 9 | blind | No thread contract; "thiefs" wording | low | patch: Javadoc says main thread only, "thieves" |
| 10 | edge | `get(null)` NPE without message | low | patch: `Objects.requireNonNull(key, "key")` |
| 11 | verif-gap, blind | Capture/escape delay overrides never exercised through `InMapPhasePlayerWrapper` | medium | defer: needs a thief inside the map (Stories 3.2/3.4) |
| 12 | blind, edge | Config loads async; early reads get `new Vi6Config()` defaults; IT waits a fixed 40 ticks | low | reject: pre-existing, same as the old direct `ConfigManager` reads; `getConfig()` is never null |
| 13 | blind, edge | 50 ms polling skews measured ticks against ±5 tolerance | low | reject: both endpoints share the poll bias; 6/6 repetitions passed across two runs |
| 14 | blind | Sprint status `in-progress` vs spec `in-review` | false | step-05 syncs the tracker at completion |
| 15 | blind | Code Map omits `Vi6Main`; AC3 not enforced by a test | low | reject: fix edits this spec / AC3 checked by grep (no bot import in `PhaseDurations`, no reference from `vi6clean.bot`) |
| 16 | verif-gap other | No test runs a phase with no override | false | `get()` falls through to config whenever no override is set; covered by defaults + edited-config tests |

## Verification

**Commands:**
- `gradlew.bat` -- expected: Spotless applied, shadow jar built.
- `gradlew.bat build` -- expected: green (spotlessCheck, test NO-SOURCE).
- `gradlew.bat integrationTest` -- expected: all ITs green, including `Vi6ConfigIT` and `Vi6PhaseDurationsIT` (3 repetitions).
