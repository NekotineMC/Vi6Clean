---
title: 'Story 4.3: Bot brains get through preparation and stay idle in-map'
type: 'feature'
created: '2026-11-18'
status: 'done'
baseline_commit: '3734998963b66d241dcc5e044d368834e023bd4c'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A bot only has a lobby brain, so in a bot-only game no thief picks an entrance and nobody readies up: preparation always waits out its timer (FR20, FR18 brain side).

**Approach:** Give each bot a per-phase brain: the existing lobby brain in the lobby, a new preparation brain that (thieves) picks an entrance from `Vi6Commands.entranceNames()` and sends `/game entrance <name>`, then (everyone) sends `/game prepready`, once per preparation, and an idle brain in-map and in infiltration. Random choices come from one seedable random source held by `Vi6Bots`, which tests pin.

## Boundaries & Constraints

**Always:** Brains read state only through Vi6's public API and act only through `bot.sendCommand`; no bot check in game logic. Each phase's brain is a separate `BotBrain` so Epics 5/6 can swap the in-map one. Brain exceptions keep going through `Vi6Bots.onBotTick` (log once with name, team, phase; stop the brain).

**Never:** Change `/game ready`, `/game entrance`, `/game prepready`, phase logic or timers. Call test hooks from main code. Add the run command or reset-per-run seeding (Story 4.4). Send input in in-map/infiltration.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Thief in preparation | Thief bot, preparation starts | Sends `game entrance <picked>` then `game prepready`, once | N/A |
| Guard in preparation | Guard bot | Sends `game prepready` once | N/A |
| No entrance | Map has no thief spawn | Thief sends only `game prepready`; random fallback applies | N/A |
| 2v2 bots | Preparation with default timer | Infiltration within a few seconds; each thief at its picked entrance | N/A |
| Same seed | Pinned seed, 3 runs | Same entrances each run | N/A |
| In-map/infiltration | Any bot | No command, no movement: bots stay where spawned | N/A |
| Brain throws | Preparation brain throws | Logged once with name, team, `Vi6PhasePreparation`; brain stopped; bot stays | as Story 2.3 |

</frozen-after-approval>

## Code Map

- `src/main/java/fr/nekotine/vi6clean/bot/LobbyBrain.java` -- pattern to copy: act once per wrapper instance (`actedIn`), reset when the wrapper is gone. Unchanged; it already idles outside the lobby.
- `src/main/java/fr/nekotine/vi6clean/bot/Vi6Bots.java` -- `setIntent` installs `new LobbyBrain(intent)`: install the per-phase brain instead. `pickedTeam(Player)` matches `instanceof LobbyBrain`: also look inside the per-phase brain. `onBotTick` already isolates failures. Add the seedable `Random`.
- `src/main/java/fr/nekotine/vi6clean/Vi6Commands.java` -- `entranceNames()` (public, sorted, empty outside preparation) is the brain's read API. Do not change.
- `PhaseMachine.getPhase(Vi6PhasePreparation.class)` / `Vi6PhaseInMap.class` (parent of preparation and infiltration) -- phase dispatch. `WrappingModule.getWrapperOptional(p, PreparationPhasePlayerWrapper.class)` -- once-per-preparation identity. `Vi6Game.getThiefs()` -- team read.
- `Bot.sendCommand` queues packets in order; both commands of one tick land in order on the next apply step.
- `ThiefTeam.spawnInMap` teleports a thief to `ThiefSpawn.getSpawnPoint()` at infiltration start.
- `src/integrationTest/.../Vi6TestHooks.java` -- `spawnBots` (brainless), `putOnTeams`, `goToPreparation`, `reset`, `thiefSpawn(name)`.
- `src/integrationTest/.../Vi6BotBrainIT.java` -- `awaitPreparation()` polls for preparation, which now lasts ~2 ticks with brains: await `Vi6PhaseInMap` instead.
- `src/integrationTest/.../Vi6PhaseDurationsIT.java` -- measures preparation length with `bot add 4 any`; brains would end it early: right after the add, give each bot a lobby-only brain (`new LobbyBrain(intent)`).
- `Vi6BotMixedLobbyIT` -- unaffected (the brainless stand-in never readies, so preparation holds).

## Tasks & Acceptance

**Execution:**
- [x] `src/main/java/fr/nekotine/vi6clean/bot/PreparationBrain.java` -- new: once per preparation wrapper, thief picks `names.get(random.nextInt(size))` if any and sends `game entrance`, then sends `game prepready`; records the picked name (public getter) -- FR20.
- [x] `src/main/java/fr/nekotine/vi6clean/bot/PhaseBrain.java` -- new: holds lobby, preparation and in-map `BotBrain`s; ticks the lobby brain always (it self-idles), then preparation brain if preparation runs, else in-map brain if in-map runs; `PhaseBrain.standard(intent, random)` with `BotBrain IDLE = _ -> {}` -- swappable per phase.
- [x] `src/main/java/fr/nekotine/vi6clean/bot/Vi6Bots.java` -- `setIntent` installs `PhaseBrain.standard`; `pickedTeam` handles it; add `random()` and `seedRandom(long)`; add public `getBrain(Bot)`.
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6BotPreparationIT.java` -- new: 2 guard + 2 thief brained bots (`spawnBots`, `putOnTeams`, `setIntent`, pinned seed, `goToPreparation`) cover the matrix: infiltration within 100 ticks with preparation's configured duration well above; each thief within 1 block of its picked spawn point; positions unchanged (x/z) over 40 idle ticks; `@RepeatedTest(3)` same picks per seed; throwing preparation brain logs once with team and phase; `LogCapture` clean; `reset()` after each.
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6BotBrainIT.java`, `Vi6PhaseDurationsIT.java` -- adapt as in the Code Map.

**Acceptance Criteria:**
- Given 2 guard and 2 thief bots with brains, when preparation starts, then infiltration runs within 5 s and the full IT suite stays green.
- Given no bot in the game, when a human plays, then nothing changes for them.

## Implementation Notes

- Brains go in after `goToPreparation` in `Vi6BotPreparationIT`: with all four bots on teams, a lobby brain would ready the lobby and race the hook.
- `PreparationBrain` takes an optional entrance supplier (default `Vi6Commands::entranceNames`) so the no-entrance row is testable on the test map, which has entrances.
- `PhaseBrain` ticks the lobby brain in every phase (it resets itself outside the lobby), so a stale lobby pick never leaks into the next lobby's balancing.
- Seed 43 picks `Couloir` and `Hangar` on the test map.

## Spec Change Log

## Review Triage Log

| # | Layer | Finding | Verdict | Route / evidence |
|---|-------|---------|---------|------------------|
| 1 | blind, verification-gap, edge-case | `Vi6BotBrainIT.awaitInMap` ends at preparation start (`Vi6PhaseInMap` is its parent), so no test covers a second preparation with the same brains; comment wrong | medium | patch: await `Vi6PhaseInfiltration` |
| 2 | verification-gap | Once-per-preparation guard not pinned: toggling every tick could still pass | medium | patch: assert the no-entrance thief stays ready across waits of different parity |
| 3 | blind | Throwing-brain test ignores unrelated errors | low | patch: assert the brain line is the only error |
| 4 | blind | `Vi6Bots.random()` unused, leaks the shared seeded source | low | patch: deleted |
| 5 | blind, edge-case | Brain sends toggling `prepready` / `entrance` without checking current state; a brain installed on an already-ready bot un-readies it | low | rejected: every preparation builds fresh unready wrappers and `setIntent` only runs at bot add; fix adds a guard branch |
| 6 | blind | Same-seed check vacuous with a one-entrance map; static `firstPicks` never cleared | low | rejected: the test map has 2+ entrances (picks `Couloir`, `Hangar`); JUnit runs the class once per JVM |
| 7 | blind | No-entrance test does not observe the random fallback | low | rejected: the fallback is unchanged game code; the test proves its precondition (no selected spawn) |
| 8 | blind | Guard non-pick checked only through the brain's field | false | `/game entrance` refuses guards (`Vi6Commands.selectEntrance`), so a guard pick cannot change game state |
| 9 | blind | In-map "no command" not tested by a spy | low | rejected: the in-map brain is the literal no-op `IDLE`; a spy adds test machinery |
| 10 | blind, edge-case | `Vi6PhaseDurationsIT` uses a bare `LobbyBrain`; null intent would NPE | false | lobby-only behavior is identical to `PhaseBrain(lobby, IDLE, IDLE)`; `/bot add` always calls `setIntent` |
| 11 | blind | Spec and sprint status missing from the diff | false | the review diff covers `src/`; both are committed with the change |
| 12 | blind | `PhaseBrain` always ticks the lobby brain | false | deliberate and stated in its Javadoc (see Implementation Notes) |
| 13 | edge-case | `pickedEntrance()` may report a pick whose command never landed | false | documented in its Javadoc; the test checks the thief's actual position |
| 14 | edge-case | Two thieves on one entrance could push each other and flake the idle check | false | the pinned seed picks two different entrances |

## Design Notes

The seed lives on `Vi6Bots` (a long-lived module); Story 4.4 reseeds it at each run start. One shared `Random` stays deterministic because bots tick in roster order.

## Verification

**Commands:**
- `gradlew.bat` -- expected: Spotless applied, shadow jar built.
- `gradlew.bat build` -- expected: BUILD SUCCESSFUL.
- `gradlew.bat integrationTest` -- expected: new IT and all existing ITs pass.
