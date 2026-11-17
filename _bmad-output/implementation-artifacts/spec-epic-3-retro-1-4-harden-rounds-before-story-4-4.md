---
title: 'Epic 3 retro items 1–4: harden rounds before Story 4.4'
type: 'bugfix'
created: '2026-11-17'
status: 'done'
baseline_commit: '9a0a5d2179cdd9bb69785ec6632765f93efa6e47'
route: 'dispatch'
review_loop_iteration: 1
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-retro-2026-11-17.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 4.4 will run rounds back to back on short timers. Epic 3 left four ways for those rounds to go wrong (retro F1, F3–F6):
- A test KoTH override or shortened durations silently carry into later rounds, and a map change turns the override into a start failure.
- A thief's 3 s death-delay task outlives its round, and on short timers the result line reports a dead thief's artefact as `stolen`.
- An exception while logging the result stops the round from ending.
- Phase durations accept 0, negative, NaN, infinite and overflowing values.

**Approach:**
- Clear the KoTH override when the map changes, and warn whenever a map phase starts with test overrides active.
- Own and cancel the death-delay task, and end infiltration only after any pending death delay.
- Guard the result log.
- Validate durations in the setters and on load.
- Correct the Story 3.1 triage log, which describes validation the rework deleted.

## Boundaries & Constraints

**Always:**
- With `config.yml` defaults and no override, round behaviour and timings are unchanged.
- A valid duration lies between 1 tick (50 ms) and `Integer.MAX_VALUE` ticks, inclusive, and is finite. The rule is the same for all five durations: preparation, lost, delay between captures, delay before escape, end spectator.
- Log text may be French or English.
- No Vi6 game-logic class imports `fr.nekotine.core.bot`.

**Never:**
- No NekotineCore change. Retro F2, `PhaseMachine` advancing its index before `goTo`, stays deferred.
- None of: F7 (result-line contract), F9, F10, or the deferred retro items 5–6.
- Don't change the 3 s death delay itself, or when a dead thief counts as left.

**Decisions:**
- An invalid duration in `config.yml` falls back to that key's default, with a warning naming the key and the bad value. The rest of the file loads as written. (User, 2026-11-17)
- The spec is kept whole despite being above the size target. (User, 2026-11-17)

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|----------|--------------|---------------------------|
| Setter, valid | `lostDuration(Duration.ofMillis(50))`, or `ofMillis(2750)` | Stored. The getter returns the same value. |
| Setter, invalid | 0, negative, 49 ms, or more than `Integer.MAX_VALUE` ticks | `IllegalArgumentException` naming the field. The old value is kept. |
| Load, invalid | `config.yml` has `lost-duration-seconds: 0`, `-5`, `.nan`, `.inf` or `0.01` | That key falls back to its default. A warning names the key and the bad value. Other keys load as written. |
| Map change | Override set, then `setMapName` with another map | Override cleared, with an info log. |
| Same map | Override set, then `setMapName` with the current map | Override kept. |
| Override active | A map phase starts with forced KoTHs, or durations differing from the last load | One WARN line naming what is active. |
| Thief dies, last one out | Thief carrying an artefact dies; end spectator time is 0.5 s | The round ends at least 60 ticks after the death. The result shows `lost` for that artefact. |
| Round torn down early | Thief dies, then `goTo(Vi6PhaseLobby)` within 3 s | The death task is cancelled. Nothing runs afterwards and nothing is logged as an error. |
| Result log throws | `logRoundResult` throws | A warning is logged and `complete()` still runs. |

</frozen-after-approval>

## Code Map

- `src/main/java/fr/nekotine/vi6clean/configuration/InfiltrationPhaseConfig.java`, `PreparationPhaseConfig.java`: duration fields are stored as `double` seconds. Setters take a `Duration`; getters use `Math.round(seconds*1000)` ms.
- `configuration/ConfigManager.java`: `tryLoad()`, and `setupAsync()`, which may replace the config with `new Vi6Config()`. Both places assign `config`, so the load-time checks and the "last loaded" snapshot hook in there.
- `game/Vi6Game.java`: `setMapName` (around line 226) and `get/setForcedKoths` (around line 342).
- `game/phase/Vi6PhaseInMap.java`: `globalSetup` (around line 156) is where the override warning goes. The constructor's `checkForcedKoths` stays as it is.
- `wrapper/InMapPhasePlayerWrapper.java`:
  - `thiefLeaveMap`, `case DEAD` (around lines 274–291): the unstored `BukkitRunnable`.
  - `tearDown` (around line 125): already cancels the escape and capture tasks. It also runs per player mid-round: a quit runs `Vi6PhaseGlobal.onPlayerLeft` → `Vi6Game.removePlayer` → `Vi6PhaseInMap.itemTearDown` → `tearDown`, and the player leaves `getThiefs()` before that.
- `game/phase/Vi6PhaseInfiltration.java`, `checkForCompletion` (around lines 140–175): completion task scheduled after `endSpectatorTicks`, then `logRoundResult`.
- `bot/Vi6GameReset.java`: already clears the override and calls `tryLoad()`, so it needs no change.
- Durations become ticks through Paper's `Tick.tick().fromDuration`, which rounds down and throws on `int` overflow.
- Tests to extend:
  - `src/integrationTest/java/.../it/Vi6PhaseMachineBotIT.java`: steal pattern at lines 186–280, plus `LogCapture` and `roundResult`.
  - `Vi6TestHooksIT`, `Vi6ConfigIT`.
  - `test-bundle` has 3 maps (Market, SlimeFactory, SolarIndustries).
- `src/test/java` holds plain JUnit (no server, no Ioc). Configurate and the Paper API are on `testCompileClasspath`.

## Tasks & Acceptance

**Execution:**
- [x] `configuration/` (both phase configs, plus a shared helper if useful): setters reject invalid durations. Add a load-time check that resets each bad key to its default and logs one warning per bad key. The warning gives both bounds in seconds (0.05 s and `Integer.MAX_VALUE` ticks expressed in seconds).
- [x] `ConfigManager.java`:
  - Run the load check wherever `config` is assigned from a file.
  - Keep a snapshot of the five durations as last loaded or defaulted.
  - Expose a description of which durations differ from that snapshot, empty when none do.
- [x] `Vi6Game.java`: `setMapName` clears the override (with an info log) only when the name differs from the current map.
- [x] `Vi6PhaseInMap.java`: `globalSetup` logs one WARN when forced KoTHs are set or durations differ from the snapshot.
- [x] `Vi6PhaseInfiltration.java`:
  - Own the pending death delays: a method that schedules a death-delay task and tracks it with its due tick until it runs, and an accessor for the longest delay left (0 when none).
  - `globalTearDown` cancels every pending death task.
  - Schedule completion after the larger of `endSpectatorTicks` and the longest pending death delay plus 1 tick, so the death tasks run first.
  - Run `logRoundResult` guarded, through a package-private static seam, so that `complete()` runs whatever the log step throws, `Error`s included (e.g. `finally`). Log a warning for a caught exception.
- [x] `InMapPhasePlayerWrapper.java`: the DEAD case schedules its task through the infiltration phase. `tearDown` does not cancel it, so a dead thief who quits during the delay still has his stolen artefacts marked lost, and the round still waits for it.
- [x] `src/test/java/fr/nekotine/vi6clean/configuration/PhaseDurationValidationTest.java` (new): plain JUnit covering the matrix's setter and load rows. Assert each warning's bad value by exact text (e.g. `": 0.0 ("`), not a substring the bounds also contain.
- [x] `src/test/java/fr/nekotine/vi6clean/game/phase/RoundCompletionTest.java` (new): plain JUnit over the guarded-log seam: completion runs when the log step throws a `RuntimeException` or an `Error`, and with no warning on the normal path.
- [x] Integration tests:
  - `Vi6PhaseMachineBotIT`: a thief steals, then is killed with `setHealth(0)` as the last thief out. Melee is deferred to Epic 6, and the death goes through the real `PlayerDeathEvent` handler. Assert the result says `lost` and is logged at least 60 ticks after the death.
  - `Vi6PhaseMachineBotIT`: thief B waits inside until it may leave, thief A steals and is killed, and B exits within the death delay. Assert A's artefact reads `lost`, logged at least 60 ticks after the death. This must fail with completion scheduled after `endSpectatorTicks` only.
  - `Vi6PhaseMachineBotIT`: thief A steals and is killed while thief B is still outside, then the game goes to the lobby within the delay. Assert the phase reports no pending death, and that after the delay has passed no death broadcast, error or exception was logged. Because A carried an artefact, a stale task would touch the torn-down phase, so this must fail if the cancel in `globalTearDown` is removed.
  - `Vi6PhaseMachineBotIT`: thief A steals and is killed, then A's bot is removed (a quit) within the delay while thief B is still outside. Assert A's artefact still reads lost on the thieves' check-list after the delay. Then B exits, and the result line shows `lost`.
  - `Vi6TestHooksIT`: test override clearing on map change and keeping on the same map, and the WARN line with an override active.
  - `Vi6ConfigIT`: an invalid `config.yml` duration falls back to its default, with its warning, while a valid key in the same file loads as written.
- [x] `_bmad-output/implementation-artifacts/spec-3-1-*.md`: mark Review Triage Log rows 1, 2, 4, 8, 9, 10 and 15 as superseded by the rework. Leave the rows' original text alone, and point to this spec where it reinstates the fix.
- [x] `AGENTS.md`: the plain test suite is no longer empty, so update the Tests bullet.
- [ ] `sprint-status.yaml`: set retro action items 1–4 to `done` once the work is merged.

**Acceptance Criteria:**
- Given the jar defaults, when a round runs, then no override WARN is logged and every existing integration test passes unchanged.
- Given `gradlew build`, then the new plain tests run and pass. They no longer report NO-SOURCE.

## Implementation Notes

- New main classes `configuration/DurationRules` (the 1 tick to `Integer.MAX_VALUE` ticks rule, setter checks, load-time reset) and `configuration/DurationSnapshot` (durations as last loaded). `ConfigManager.config` is now `volatile`, because the async setup task assigns it. `tryLoad` falls back to defaults if the file's root node is empty.
- `Vi6PhaseInfiltration.logThenComplete(log, complete, warn)` is a package-private static seam, so plain JUnit can cover the "result log throws" row (`RoundCompletionTest`).
- `LogCapture` also records each line's tick and level.
- The first death IT (`lastThiefOutDiesWithAStolenArtefact`) passes on the old code too: a last-thief death already waited for the death task. `otherThiefEscapesDuringTheDeathDelay` covers the real bug (another thief's exit starts completion during the delay). It failed 3/3 with completion scheduled after `endSpectatorTicks` only: twice the result read `stolen`, and once the round had already ended, so the test's check on thief B threw.
- Story 3.1 triage note: rows 1, 2 and 8 are reinstated here (8 as the map-phase WARN). Rows 4, 9, 10 and 15 are superseded, not reinstated. Row 4's NPE risk in `Vi6Main` remains.
- The sprint-status task waits for the merge, as earlier stories did with a separate "mark done" commit after their PR.
- (Loop 0; its code was reverted in loop 1, see Spec Change Log.) Verification: `gradlew.bat build` passed with 100 plain tests. `gradlew.bat integrationTest` passed with 71 tests and 0 failures. The dev server was not exercised.

- Loop 1 final verification (after the pass-2 patch): `gradlew.bat spotlessApply build integrationTest` BUILD SUCCESSFUL. 62 plain tests and 76 integration tests passed with 0 failures and 0 skipped. A mutation run showed `earlyLobbyCancelsAPendingDeathDelay` fails 3/3 without the cancel in `globalTearDown`. The dev server was not exercised.

## Spec Change Log

- **Loop 1** (review, 2026-11-17).
  - **Trigger:** blind-hunter and edge-case findings. A dead thief who quits during the 3 s death delay is torn down per player (`onPlayerLeft` → `removePlayer` → `itemTearDown` → `tearDown`). The loop-0 wrapper cancelled the death task there, so his artefacts never turned lost, and since he had already left `getThiefs()` the completion no longer waited for him. Before this change the task still ran.
  - **Amended:**
    - Code Map: noted the per-player teardown path.
    - Tasks: pending death delays move from the wrapper to `Vi6PhaseInfiltration`, which tracks them and cancels them only in `globalTearDown`. The wrapper's `tearDown` no longer touches the task.
    - Folded in the review patches: an early-lobby test whose stale task would be observable; exact warning text in tests; bounds in seconds; `complete()` runs even on an `Error`; plus a quit test.
    - Design Notes rewritten to match.
  - **Known-bad state avoided:**
    - Cancelling the death task on any wrapper teardown, which drops a quitting dead thief's lost artefacts.
    - Computing pending delays only over `game.getThiefs()`.
  - **KEEP** (worked in loop 0; re-derive the same way):
    - `DurationRules`: package-private, with `isValidMillis`, `isValidSeconds`, `checkedSeconds` (null and overflow-safe `toMillis`) and `orDefault`. The setters throw an `IllegalArgumentException` naming the field and keep the old value. Each config class has a package-private `resetInvalidDurations(path, warnings)` that uses a fresh default instance.
    - `DurationSnapshot`: a record of the five durations with `describeChanges`.
    - `ConfigManager`:
      - A private `use(...)` sets the snapshot and the config.
      - `config` is volatile.
      - `tryLoad` uses `configNode.get(Vi6Config.class, new Vi6Config())`.
      - `describeDurationChanges()` returns an empty string when nothing changed.
    - `Vi6Game.setMapName` clears the override only when the name changes, with an INFO line, placed after the world check.
    - `Vi6PhaseInMap.warnIfOverridden`: one WARN, `Map phase started with test overrides active: ...`.
    - `LogCapture.lines()` gives each line's tick and level.
    - These ITs: `lastThiefOutDiesWithAStolenArtefact`, `otherThiefEscapesDuringTheDeathDelay`, `mapChangeClearsTheKothOverride` (restores the original map), `mapPhaseWarnsOnlyWithATestOverride`, and `Vi6ConfigIT.invalidDurationFallsBackToItsDefault`.
    - Do not re-edit the Story 3.1 spec's "Superseded by the rework" note. It stays as written.

## Review Triage Log

Pass 1 (loop 0). Layers: blind hunter (B), edge-case hunter (E), verification gap (VG).

| # | Source | Finding | Verdict | Route |
|---|--------|---------|---------|-------|
| 1 | B4, E1 | A dead thief who quits during the death delay: per-player `tearDown` cancels the task, so his artefacts never turn lost, and completion no longer waits (he has left `getThiefs()`). | medium: traced `onPlayerLeft` → `removePlayer` → `itemTearDown` → `tearDown`. The old code still ran the task. | bad_spec → loop 1 |
| 2 | VG1, B11 | `earlyLobbyCancelsAPendingDeathDelay`: thief A carries nothing, so removing `cancel()` still passes. | medium (filed by the verification-gap layer) | patch, folded into loop 1's tasks |
| 3 | B1 | Warning-value asserts use `contains("0.0")`, which the "0.05" bound always satisfies. | low: the assert can't fail on a wrong value | patch, folded into loop 1's tasks |
| 4 | B2 | Warning mixes units: "0.05" (seconds) and "2147483647 ticks". | low: confusing text in a server log | patch, folded into loop 1's tasks |
| 5 | B10, E3 | `logThenComplete` catches only `RuntimeException`; an `Error` skips `complete()`. | low: the spec's row says the round still ends | patch, folded into loop 1's tasks |
| 6 | B3 | A thief dying after completion has started is not waited for. | false: completion starts only once every thief has left (spectator) or, on the lost timer, every thief inside is forced out LOST. No thief carrying an artefact can die afterwards. | reject |
| 7 | B5, E2 | `thiefLeaveMap(DEAD)` twice orphans the first task. | low: needs a LEFT spectator thief to die again; unlikely in play, and the fix adds a guard | reject |
| 8 | B6, E5 | Null `game`/`preparation`/`infiltration` section: `DurationSnapshot.of` throws an NPE that escapes `tryLoad`. | maybe-false: depends on whether Configurate keeps field defaults for an explicit YAML null | defer (medium, unverified) |
| 9 | B7, E4 | `use()` writes the snapshot and the config separately; a main-thread read in between could warn wrongly. | low: one async load at boot, before any round; the fix adds a holder | reject |
| 10 | B8 | WARN logged after `resolveForcedKoths`, so a bad override fails without it. | false: the constructor's `checkForcedKoths` throws first, naming the forced KoTH, so `globalSetup` never resolves a bad override | reject |
| 11 | B9 | Setters truncate sub-millisecond precision. | low: no caller passes sub-ms durations; config is in seconds | reject |
| 12 | B12 | The spec's Code Map is stale about `src/test/java`. | fix edits this spec | reject |
| 13 | B13 | The `AGENTS.md` Tests bullet says "no Bukkit", but `RoundCompletionTest` loads Bukkit types. | low | defer (agent-context file) |
| 14 | B14 | The Story 3.1 row 4 NPE risk in `Vi6Main` has no `deferred-work.md` entry. | low, pre-existing | defer |
| 15 | B15 | `LogCapture` keeps two parallel queues. | low: no harm named | reject |
| 16 | B16 | The map-change test doesn't start a round on the other map. | low: the matrix row (override cleared) is covered | reject |
| 17 | E6 | A non-numeric duration in `config.yml` resets the whole file to defaults. | medium, pre-existing: a Configurate type error did this before the change too | defer |
| 18 | E7 | An empty override list (no KoTHs) is cleared on a map change. | false: the frozen matrix says an override is cleared on another map; an empty list is an override | reject |

Pass 2 (loop 1).

| # | Source | Finding | Verdict | Route |
|---|--------|---------|---------|-------|
| 19 | B, VG-other, E | `earlyLobbyCancelsAPendingDeathDelay` asserts no "est mort avec" log line, but `game.sendMessage` reaches only player audiences, so the assertion can never fail. The cancel stays guarded by the `failures()` check (mutation run: 3/3 fail without `cancel()`). | low: dead assertion | patch |
| 20 | B, E | When there is no infiltration phase, the DEAD fallback schedules an untracked task. | low, pre-existing root: reachable only through a stale `InfiltrationPhasePlayerWrapper`, because `Vi6PhaseInfiltration.itemTearDown` calls `removeWrapper(null, ...)`, which removes nothing. The fallback keeps the old behaviour on that path. | defer |
| 21 | B, E5 | Null config sections: `DurationSnapshot.of` NPEs, and `setupAsync`'s catch then says defaults are loaded without loading them. | carried: row 8, code reads as described | defer (already deferred, no new entry) |
| 22 | VG1 | No test runs the completion task through the guarded-log seam; reverting the call site keeps every test green. | medium (filed by the verification-gap layer) | defer, as filed: needs a hook to make `logRoundResult` throw in a real round |
| 23 | VG2 | No test checks that `override-config: true` resets the duration snapshot at boot; a regression would give false override WARNs on production rounds. | medium (filed by the verification-gap layer) | defer, as filed: needs a boot config with non-default durations |
| 24 | B | `deferred-work.md` entry and triage row 13 (the `AGENTS.md` Tests wording) are stale now that this change rewrote the bullet. | fix edits this build's records | reject; reported in the PR instead |
| 25 | B | No verification is recorded for loop 1. | fix edits this spec | reject; recorded at completion |
| 26 | B | Durations are formatted two ways in logs (`240.0s` vs `0.05 s`). | low: cosmetic, and the fix needs a shared formatter and test updates | reject |
| 27 | B | The NPE filter in `deadThiefWhoQuitsStillLosesHisArtefacts` is brittle; the `Artefact.inside` quit bug could be fixed here. | low: the filter is narrow to one message, and the Artefact bug is pre-existing and already deferred | reject |
| 28 | B | `otherLoadableMap` swallows every `RuntimeException` from `setMapName`. | low: test-helper robustness | reject |
| 29 | B, E4 | `logThenComplete` loses the log step's `Error` if `complete` also throws. | low: needs two failures at once, and the second one is logged anyway | reject |
| 30 | B | `scheduleDeathDelay` is public and unguarded after teardown; a due-this-tick task gives `delay = max(endSpectatorTicks, 1)`. | false: its only caller runs while the phase is set up, and a task due this tick runs before the completion task, which is scheduled later | reject |
| 31 | E3 | `mapPhaseWarnsOnlyWithATestOverride` hard-codes the loaded lost duration (240 s). | low: the test bundle runs `override-config: true`, so it always holds the defaults | reject |
| 32 | E5, E6 | The upper bound accepts up to 49 ms past `Integer.MAX_VALUE` ticks, and the message rounds it. | low: those values still floor to `Integer.MAX_VALUE` ticks, so nothing breaks | reject |

## Design Notes

`Vi6PhaseInfiltration` owns every pending death delay: each task and the tick it is due. The phase, not the thief list, is the source, so a thief who quits mid-delay still counts. Completion is scheduled after `max(endSpectatorTicks, dueTick - now + 1)`. A task removes itself when it runs. Only the phase's `globalTearDown` cancels the ones still pending, so the round is over before any is dropped.

The death task already calls `checkForCompletion`, which returns early once completion has started, so the order stays safe.

## Verification

**Commands:**
- `gradlew.bat`, then `gradlew.bat build`: expected BUILD SUCCESSFUL, with Spotless clean and the plain tests green.
- `gradlew.bat integrationTest`: expected BUILD SUCCESSFUL with 0 failures. The new tests are `@RepeatedTest(3)` where they drive rounds.
