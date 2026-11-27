---
title: 'Story 5.5: Vi6 bots roam between map areas for the whole round'
type: 'feature'
created: '2026-11-27'
status: 'done'
baseline_commit: 'b27a92454b4559e32f26ea11f714b76f09063d04'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** In infiltration, Vi6 bots stand idle (`PhaseBrain.IDLE`), so a bot-filled round shows nothing. Stories 5.2-5.4 provide a pathfinder, a walker and Vi6's per-role path rules, but no brain uses them (FR9, FR18, AD-12).

**Approach:** Add a `RoamingBrain` in `fr.nekotine.vi6clean.bot`. Each bot gets its own `PathWalker` over `bot.body()`, and all bots share one `AStarPathFinder`. A guard walks to random map areas under `Vi6PathRules.guard`. A thief roams the same areas, under `thiefOutside` while entering (its path goes through an entrance), then under `thiefInside(map, false)`. The brain picks a new goal on arrival or when stuck. `Vi6Bots` offers the brain; Story 5.6 wires it into `/bot run`.

## Boundaries & Constraints

**Always:**
- Decisions (auto-validated, 2026-11-27):
  - Bots roam in infiltration only, through `PhaseBrain`'s in-map slot. Preparation keeps `PreparationBrain`.
  - Areas are the standable spots of the map's artefact capture boxes, room captor trigger boxes and KoTH boxes. A box with no standable spot is skipped. The list is computed once per round.
  - An entering thief roams the same areas as an inside thief, under `thiefOutside`: every area is inside the map, so its path goes through an entrance (human decision, 2026-11-27). When it becomes `INSIDE`, it keeps its goal, searched again under `thiefInside`.
  - An inside thief never asks to leave (leaving and stealing belong to 5.6).
  - Goals are drawn from the `RandomGenerator` shared by `Vi6Bots` (seedable, as in Story 4.3). The next goal differs from the current one when there is more than one area.
  - The stuck bound is the walker's `DEFAULT_STUCK_TICKS`. After `ARRIVED` or `STUCK`, a new goal is set on the bot's next tick.
- A standable spot is a block column of the box, nearest the box centre first. Its feet block is inside the box, has a floor whose `isSolid()` is true, and has feet and head blocks that are passable and not liquid. It is computed on the main thread from the live world.
- Each tick, the brain reads state only through Vi6's public API: `Vi6Game` teams and world, `Vi6PhaseInMap#getMap`, and `InMapPhasePlayerWrapper` state. It acts only through the walker. A new round is detected by a new `InMapPhasePlayerWrapper` instance.
- When the bot is in `SPECTATOR` mode or has left the map, the brain stops its walker and idles.
- When the in-map slot stops being ticked (the phase ends, or a reset), the walker stops once: the walker writes neutral movement through `BotBrain#halt`. `halt` is a new default no-op that `PhaseBrain` calls on the brain of the slot it leaves.
- If a brain throws (walker and finder errors propagate through `tick`), `Vi6Bots` keeps its single log line with bot, team and phase, removes the brain, and writes neutral movement at `ControlPriority.BRAIN` in that same tick. The bot stays connected.

**Never:**
- No change to NekotineCore, `PathWalker`, `Vi6PathRules` or Vi6 game logic. No bot branch in game code.
- No stealing, exits, combat or tool use. No change to `PhaseBrain.standard`, `/bot run` or the soak test (Story 5.6).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Guard, infiltration | inside map | walks to area A, then on `ARRIVED` to area B ≠ A | N/A |
| Thief `ENTERING` | at its spawn | walks to an area through an entrance, becomes `INSIDE`, keeps the goal, then roams | stuck → next area |
| Walker `STUCK` | blocked | new goal next tick | N/A |
| One area only | map with one area | same goal again | N/A |
| No area | map without areas | idles, no exception | N/A |
| Left / spectator | dead, escaped, round end | walker stopped, nothing written after | N/A |
| Phase leaves | infiltration → lobby mid-walk | movement released once | N/A |
| Finder throws | injected failing finder | one error line, brain removed, forward released, bot online | logged by `Vi6Bots` |

</frozen-after-approval>

## Code Map

- `src/main/java/fr/nekotine/vi6clean/bot/BotBrain.java` -- add `default void halt(Bot bot) {}`.
- `.../bot/PhaseBrain.java` -- remember the slot it ticked last tick. When the slot changes, or none runs, call `halt` on the old slot's brain. `IDLE` stays a lambda.
- `.../bot/Vi6Bots.java:105-117` -- `onBotTick` failure path. Add a lazily built shared `AStarPathFinder(Ioc.resolve(JavaPlugin.class))` and `public RoamingBrain roamingBrain()` → `new RoamingBrain(random, finder)`.
- `.../bot/Vi6PathRules.java` -- `guard(map)`, `thiefOutside(map)`, `thiefInside(map, false)`, built once per goal.
- `NekotineCore/core/.../navigation/PathWalker.java` -- `walkTo(goal, finder, rule)`, `tick()` returns `WalkStatus`, `stop()`. Main thread only. One walker per brain, built on first tick over `bot.body()`.
- `.../game/phase/Vi6PhaseInMap.java:455` `getMap()`. `Vi6Game#getWorld()`, `getGuards()`, `getThiefs()`.
- `.../wrapper/InMapPhasePlayerWrapper.java` -- `getState()` (`ENTERING`/`INSIDE`/`LEFT`), `hasLeft()`, via `WrappingModule#getWrapperOptional`.
- Map: `Artefact#getBoundingBox()`, `RoomCaptor#getTriggerBox()`, `Koth#getBoundingBox()`. Collections may be `null`.
- `src/integrationTest/.../it/Vi6TestHooks.java` -- `spawnBots`, `putOnTeams`, `forceKoths()`, `goToInfiltration(spawn)`, `reset()`. `Vi6MapSpots.spawnWithEntranceAndExit()`. `Vi6Asserts.assertCleanLog`, `LogCapture`. `Vi6BotBrainIT.throwingBrainStopsAndTheBotStays` is the failure pattern.

## Tasks & Acceptance

**Execution:**
- [x] `.../bot/BotBrain.java`, `PhaseBrain.java` -- `halt` and its call on slot change.
- [x] `.../bot/RoamAreas.java` -- `standableSpot(World, BoundingBox)` (nullable), `areas(Vi6Map, World)` (sorted by name, so the order is deterministic) and `pick(List, current, RandomGenerator)`.
- [x] `.../bot/RoamingBrain.java` -- the brain: per-round areas and walker, role rules, goal kept on entering → inside, `goal()` and `goalsPicked()` getters for tests, and `halt`.
- [x] `.../bot/Vi6Bots.java` -- shared finder, `roamingBrain()`, and neutral movement on failure.
- [x] `src/test/java/fr/nekotine/vi6clean/bot/RoamAreasTest.java` -- `pick`: never the current goal with two or more areas; the same seed gives the same sequence; one area; empty → `null`.
- [x] `src/integrationTest/.../it/Vi6RoamingIT.java` -- (a) 2v2 on the default config timers with no KoTH: hook bots with `PhaseBrain(IDLE, IDLE, roamingBrain())`, `goToInfiltration(spawnWithEntranceAndExit())`, then sample each tick until the lobby. Both thieves become `INSIDE`. Each bot picks at least 3 goals. No bot goes more than 100 ticks without either moving 1 block or picking a new goal, until the round ends. Every bot stays online. Clean log. (b) The injected throwing finder row.

**Acceptance Criteria:**
- Given infiltration with roaming guards and thieves, when the round runs to its lost timer, then every bot keeps travelling between areas until the end, as checked by (a).
- Given a brain or walker failure, when it happens, then exactly one error line names the bot, team and phase, the bot stops moving and stays online.
- Given this story, when Vi6 `gradlew.bat build integrationTest` runs, then it is green, `Vi6SoakIT` and `Vi6PathRulesIT` included.

## Implementation Notes

## Spec Change Log

- 2026-11-27, human review after the build: entrance targeting dropped. An entering thief roams the same areas under `thiefOutside`, so its path goes through an entrance; on becoming `INSIDE` it keeps its goal. Removes `nextEntrance`/entrance cycling, which makes triage rows 4, 7 and 12 moot (the deferred-work entry of row 12 is removed). KEEP: per-role rules, fresh search on a role change, halt and failure release.

## Review Triage Log

| # | Layer | Finding | Verdict | Route | Evidence |
|---|-------|---------|---------|-------|----------|
| 1 | blind, edge-case | `setBrain`/`setIntent` drop a brain without `halt`, leaving walker keys held | medium | patch | `Body` persists the last group writes; a mid-walk swap kept forward and heading. Now the outgoing brain is halted. |
| 2 | edge-case | `halt` not called on `unload`, `onQuit`, `forgetAllBut` or failure | low | reject | A bot that quits or is removed no longer ticks. The failure path already releases movement at `BRAIN`. |
| 3 | blind, edge-case | `PhaseBrain` and `RoamingBrain` hold per-bot state; sharing an instance breaks it | low | patch | Javadoc now says one instance per bot. Every caller builds one per bot. |
| 4 | blind | After a one-tick idle, an entering thief skips an entrance that never failed | low | patch | `nextEntrance` advanced on every call. It now advances only after `STUCK` or `ARRIVED`. |
| 5 | blind | After an idle, a bot may re-pick the area it just reached | low | reject | Only happens after a rare one-tick idle, and costs one repeated walk. The fix needs extra state. |
| 6 | edge-case | On a role change the walker keeps the old path and rule when the new goal is within 1 block | low | patch | The walker is now stopped on a role change before the first goal of the new role. |
| 7 | edge-case | `ARRIVED` at an entrance a tick before `INSIDE` redirects the thief | false | reject | The goal is a spot inside the trigger box, so the move into the box made the thief `INSIDE` before arrival. |
| 8 | blind, edge-case | Main-thread scan of large boxes, repeated per bot | low | reject | Scans stop at the first standable column near the centre, and boxes are room-sized. No spike was seen in the 2v2 IT. |
| 9 | edge-case | `release` neutralises movement only, not aim or hands | false | reject | Hands are one-shots. A held aim moves nothing. |
| 10 | blind, edge-case, verification | The round IT passes for a bot that is stuck all round | medium | patch | `goalsPicked` reset the stillness anchor. Arrivals at goals are now counted per bot and asserted. |
| 11 | verification, blind | Spectator/left idle branch is never observed (the lobby halt masks it) | medium | patch | Matrix row "Left / spectator". The sampler now fails if forward or a goal persists over 2 samples while the bot is a spectator in infiltration. |
| 12 | verification, blind | Entering-thief cycling to the next entrance is untested | medium (unverified) | defer | Needs a fixture with a blocked nearest entrance. Logged in deferred-work. |
| 13 | blind | `standableSpot`/`areas` have no direct test | low | reject | Exercised by the round IT. The arrival check (#10) now fails if spots are unreachable. |
| 14 | blind | "No area" and in-brain "one area" rows untested | low | reject | `pick` covers the empty and one-area cases. The brain idles on `null` (code read). |
| 15 | blind | Roaming thieves walk into artefact capture boxes and may steal | false | reject | Intended game behaviour for a thief in a zone. "No stealing" scopes brain logic (5.6), and the round still ends on its timer. |
| 16 | blind | Phase-leave IT has no log check | low | patch | Wrapped in `LogCapture`/`assertCleanLog`. |
| 17 | blind | `ROUND_TIMEOUT_TICKS` hard-coded | low | reject | Default timers are stable. A change makes the test fail loudly, not silently. |
| 18 | blind | `unload` keeps the shared finder | low | patch | Set to `null` in `unload`. |
| 19 | edge-case | Walker reused across rounds | false | reject | `idle()` stops it on every new round wrapper. |
| 20 | blind | `PhaseBrain` halt order (preparation to in-map) untested | low | reject | Covered by the phase-leave IT for the in-map slot. Preparation brains have no `halt`. |

## Design Notes

```java
if (status == ARRIVED || status == STUCK || goal == null) {
    goal = RoamAreas.pick(areas, goal, random);
    if (goal != null) walker.walkTo(goal, finder, rule);   // rule from current role
}
status = walker.tick();
```
Guards cross closed forcefields by rule. The field opens within 1 block, before the 40-tick stuck delay. No test places a field (deferred from 5.4).

## Verification

**Commands:**
- `gradlew.bat` then `gradlew.bat build integrationTest` -- expected: green, `RoamAreasTest`, `Vi6RoamingIT`, `Vi6SoakIT` pass (bare `gradlew` deploys to the dev server, which is fine).
