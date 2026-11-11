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

**Problem:** Phase timers are read straight from `ConfigManager` in field initializers (preparation 300 s, infiltration lost 240 s, plus capture/escape delays and end-of-game spectator time), so a test round takes real minutes and tests have no way to shorten it (FR19, AD-13).

**Approach:** Add one Vi6 point, `PhaseDurations`, through which every phase and the in-map wrapper read their durations. It returns the configured value unless a test has set an override; overrides are read when a phase (or wrapper) is constructed, so a test sets them before the phase starts and clears them when done. An integration test runs a 4-bot round on overridden timers and checks each phase ends on its overridden timer.

## Boundaries & Constraints

**Always:** with no override, every duration equals the live `config.yml` value at the time it is read (no caching of config), and `Vi6ConfigIT` stays green. The seam covers the five durations of `game.preparation` and `game.infiltration`: preparation `duration`, infiltration `lostDuration`, `delayBetweenCapture`, `delayBeforeEscape`, `endSpectatorTime`. Overrides persist until cleared, and clearing returns every duration to config.

**Never:** no bot type, import or concept in the seam (AD-13), and no brain calls it. No change to `config.yml` defaults, the Configurate records, or game rules. Mechanic timers (Majordom, asthma, suffocation) are out of scope. No hot-change of a phase that is already running. No NekotineCore change.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| No override | config default or edited `config.yml` | each getter returns the config value | N/A |
| Override set | `override(PREPARATION, 2s)` before preparation starts | preparation completes ~40 ticks after it starts | N/A |
| Override cleared | `clearOverrides()` | getters return config values again | N/A |
| Invalid override | `null`, zero or negative duration | rejected | `IllegalArgumentException`, existing value kept |

</frozen-after-approval>

## Code Map

- `src/main/java/fr/nekotine/vi6clean/configuration/{PreparationPhaseConfig,InfiltrationPhaseConfig}.java` -- source of default values; read only, don't change.
- `src/main/java/fr/nekotine/vi6clean/game/phase/Vi6PhasePreparation.java:112` -- `PREPARATION_DURATION_MAX_TICKS` field initializer; timer in `onTick` (~l.291); on timeout `handleComplete` picks a random spawn for thieves who chose none.
- `src/main/java/fr/nekotine/vi6clean/game/phase/Vi6PhaseInfiltration.java:56` -- `infiltration_lost_duration_ticks` initializer; l.148 `endSpectatorTime()` read in `checkForCompletion`; `onTick` marks entering/inside thieves `LOST` when the timer runs out.
- `src/main/java/fr/nekotine/vi6clean/wrapper/InMapPhasePlayerWrapper.java:61-65` -- `DELAY_BETWEEN_CAPTURE` / `DELAY_BEFORE_ESCAPE` initializers.
- `src/main/java/fr/nekotine/vi6clean/game/Vi6Game.java:110-113` -- phases registered by constructor reference; `PhaseMachine` builds a new instance on each `goTo`, so initializers re-read per game. Machine loops back to the lobby.
- `src/main/java/fr/nekotine/vi6clean/configuration/ConfigManager.java` -- `IPluginModule` pattern; config loads async, so read it on each call.
- `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6BotBrainIT.java` -- reuse: `run("bot add 4 any")`, `awaitPreparation()`, `@AfterEach` remove bots + `goTo(Vi6PhaseLobby)`. Check phases with `getPhase(...)`, never `isRunning()`.

## Tasks & Acceptance

**Execution:**
- [x] `src/main/java/fr/nekotine/vi6clean/game/PhaseDurations.java` -- new `IPluginModule` with a nested enum of the five durations; `Duration get(key)` (override or live config), `override(key, Duration)` (positive only), `clearOverrides()`; `unload()` clears overrides; Javadoc says it is a test seam read at phase construction -- single read point (AC 1).
- [x] `Vi6PhasePreparation.java`, `Vi6PhaseInfiltration.java`, `InMapPhasePlayerWrapper.java` -- read the five durations through `Ioc.resolve(PhaseDurations.class)`; nothing else changes.
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6PhaseDurationsIT.java` -- (a) after config load, each getter equals the matching `ConfigManager` value; override then clear restores it; invalid overrides throw. (b) `@RepeatedTest(3)`: override preparation 2 s, lost 3 s, end spectator 1 s; `bot add 4 any`; record `Bukkit.getCurrentTick()` when preparation, infiltration and the next lobby appear; assert preparation lasted 40 ticks and infiltration ended 60 + 20 ticks after it began, each within ±5 ticks, and no error logged (`LogCapture`). `@AfterEach`: clear overrides, remove bots, back to lobby.

**Acceptance Criteria:**
- Given no override, when any phase starts, then its durations equal `config.yml`, and `Vi6ConfigIT` passes unchanged.
- Given preparation and infiltration-lost overridden to a few seconds, when a 4-bot game runs, then preparation ends on its overridden timer, infiltration ends on its lost timer plus end-spectator time, and the game returns to the lobby, 3 times in a row.
- Given the change, when reviewed, then `PhaseDurations` imports nothing from `fr.nekotine.core.bot` or `fr.nekotine.vi6clean.bot`, and no file under `fr.nekotine.vi6clean.bot` references it.

## Implementation Notes

- Implemented inline (no subagent). Branch `feat/bots-3-1-phase-durations` off `bot`.
- `PhaseDurations` is not a NekotineCore module, so Core's auto-registration does not see it: `Vi6Main.onEnable` registers it in `Ioc` through `moduleManager.get(...)`, next to `ConfigManager` (file not in the Code Map). `ModuleManager.unloadAll` at disable calls its `unload()`, which clears overrides.
- `Vi6PhaseDurationsIT.roundEndsOnOverriddenTimers` clears the bots' brains once preparation starts: otherwise the lobby brains ready up again in the next lobby and start another round before the lobby can be observed. It uses `Vi6Bots.setBrain(bot, null)` as a driver only; the seam itself has no bot reference.
- Verified: `gradlew.bat` and `gradlew.bat build` green; `gradlew.bat integrationTest` 32 passed, 0 failed (incl. `Vi6ConfigIT`, 3 repetitions of the round test).
- After review patches: `integrationTest` 33 passed, 0 failed.
- `Vi6Main` uses `moduleManager.tryLoad(PhaseDurations.class)`: `ModuleManager.load` registers the instance in Ioc itself.

## Spec Change Log

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

## Design Notes

Durations stay captured at construction (the existing field initializers), so behaviour for humans is identical: an override only affects phases built after it is set. A keyed enum keeps the API to three methods that Story 3.2's reset can clear in one call, e.g.:

```java
var d = Ioc.resolve(PhaseDurations.class);
d.override(PhaseDurations.Key.PREPARATION, Duration.ofSeconds(2));
// ... run the game ...
d.clearOverrides();
```

## Verification

**Commands:**
- `gradlew.bat` -- expected: Spotless applied, shadow jar built.
- `gradlew.bat build` -- expected: green (spotlessCheck, test NO-SOURCE).
- `gradlew.bat integrationTest` -- expected: all ITs green, including `Vi6ConfigIT` and `Vi6PhaseDurationsIT` (3 repetitions).
