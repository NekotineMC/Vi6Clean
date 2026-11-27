---
title: 'Story 5.4: Vi6 passability rules respect entrances, exits and gates'
type: 'feature'
created: '2026-11-23'
status: 'done'
baseline_commit: 'fa17f2389b3b4a0224aa80e1c45b38e6720e7776'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
  - '{project-root}/NekotineCore/AGENTS.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The 5.2 pathfinder routes anywhere a body physically fits, but Vi6 rules differ by role: `Vi6PhaseInMap.onPlayerMove` cancels an inside player's move into an entrance blocking box or an exit unless that player may leave, and a guard's forcefield (gate filled with glass) opens for guards walking up to it. Story 5.5's roaming brains need paths that keep these rules (FR9, AD-7).

**Approach:** Add `Vi6PathRules` in `fr.nekotine.vi6clean.bot`: static factories building a `PathRule` from the loaded `Vi6Map` for a guard, a thief not yet inside, and a thief inside (leaving or not). To let guards route through closed forcefields, add a generic, Vi6-agnostic per-block override to NekotineCore's `PathRule`: `passage(view, x, y, z)` returns a `Passage` (`DEFAULT`, `OPEN`, `FLOOR`, `BLOCKED`) that replaces what the world says for that block, for this search and body only; `AStarPathFinder` honours it. Runtime open/close needs no shared state: each search takes a fresh snapshot and its caller builds the rule from current game state.

## Boundaries & Constraints

**Always:**
- Decisions (human, 2026-11-23): a thief not yet inside may pass through every entrance, no selected one; a thief never routes through a closed forcefield, a guard can; both route through an opened forcefield. Block overrides live in the per-search rule (per body), not in an editable shared snapshot or graph.
- Guard: forbid every entrance blocking box and every exit; return `OPEN` for every block in a gate box whose block is the forcefield block (`ForcefieldHandler` fills a placed field with `GRAY_STAINED_GLASS`; expose that material as one shared constant).
- Thief inside, not leaving: forbid every entrance blocking box and every exit. Thief inside and leaving: forbid nothing. Thief not yet inside: forbid nothing (entrances and exits do nothing to an outside thief). Thieves override nothing; a closed field is glass, so the finder already refuses it, and an opened field is air. Whether a thief may leave yet is the caller's call.
- A feet block `(x, y, z)` is refused when its cell `[x, x+1] × [y, y+1] × [z, z+1]` strictly overlaps a forbidden box (`BoundingBox#overlaps`). A gate cell is any block whose unit cell strictly overlaps a gate box. Rules add no cost.
- Factories run on the main thread and copy the boxes they need; the rule never holds the `Vi6Map` (its boxes are mutable: `ForcefieldHandler` expands gate boxes in place), reads blocks only through the `BlockView`, and is pure. A `null` map collection (e.g. `getGates()`) counts as empty; `null` arguments → `NullPointerException`.
- `Passage` (core, public enum in `fr.nekotine.core.navigation`) and `PathRule#passage` (default `DEFAULT`): asked for any block the search reads that the view covers (never where `getBlockData` is `null`), whatever the world holds there; `OPEN` = air the body walks through, `FLOOR` = solid, can be stood on, `BLOCKED` = never passable (like a liquid or fence), `DEFAULT` = the finder's own reading. It applies to feet, head, floor and between-point blocks alike; `allows` and `cost` still run on top. Same purity and thread rules as `allows`. A pathfinder that cannot honour overrides must say so in its Javadoc. Javadoc in the existing English style; `AStarSearch` keeps its determinism.
- NekotineCore changes go on a branch from NekotineCore `bot`, PR into `bot`; the Vi6 PR (into Vi6 `bot`) bumps the gitlink. New Vi6 ITs go in a new class; shared spot helpers move out of `Vi6PhaseMachineBotIT` instead of growing it.

**Never:**
- No Vi6 term in NekotineCore; no change to game logic beyond the material constant (no bot branch in Vi6 game code).
- No brain, walker change or goal picking (5.5); no registered `PathFinder` instance in Vi6 yet; room captors play no part.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Guard | cell overlapping an exit or a blocking box; cell in a trigger box | refused; allowed | N/A |
| Thief inside | exit cell, `leaving` false / true | refused / allowed | N/A |
| Thief outside | any entrance or exit cell | allowed | N/A |
| Closed field | gate glass between start and goal | guard path crosses it; thief path does not reach the goal | N/A |
| Opened field | same gate as air | both reach the goal | N/A |
| Glass outside a gate, or non-glass in a gate | guard rule | `DEFAULT` | N/A |
| Core override | `OPEN` on a wall, `FLOOR` over a gap, `BLOCKED` on air, `DEFAULT` | path crosses the wall, walks over the gap, avoids the air cell, unchanged | N/A |
| Touching face / mutation | box `maxX == x`; map box changed after build | allowed; rule unchanged | N/A |

</frozen-after-approval>

## Code Map

- `src/main/java/fr/nekotine/vi6clean/map/Vi6Map.java` -- `getEntrances()` (`Entrance#getEntranceTriggerBox`, `#getBlockingBox`), `getExits()`, `getGates()` (may be `null`), all `BoundingBox`.
- `src/main/java/fr/nekotine/vi6clean/game/phase/Vi6PhaseInMap.java:462-520` -- the human rules mirrored.
- `src/main/java/fr/nekotine/vi6clean/tool/personal/forcefield/ForcefieldHandler.java` -- `placeField`/`closeField` fill `GRAY_STAINED_GLASS`, `removeField`/`openField` fill air; a field opens while a guard is within 1 block of the gate box.
- `NekotineCore/core/.../navigation/PathRule.java` -- add `passage`; new `Passage.java` beside it. `.../astar/AStarSearch.java` -- reads cells only through `grid.cell(...)` (`settle`, `standable`, `clear`, neighbour moves); wrap the grid once in the constructor so a non-`DEFAULT` override maps to `Cell.OPEN`/`SOLID`/`BLOCKED` (package-private `Cell` stays internal), cached per search. `.../astar/SnapshotView` -- `getBlockData` is `null` outside the snapshot (do not ask the rule there). Tests: `core/src/test/.../astar/AStarSearchTest.java` (fake grid and view).
- `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6PhaseMachineBotIT.java:525-650` -- `enterMap`, `stepInto`, `standableSpot`, `passable`, `roomiestYaw`, `spawnWithEntranceAndExit`: move to a package-visible helper class used by both ITs.
- `.../it/Vi6TestHooks.java` -- `spawnBots`, `putOnTeams`, `goToInfiltration(spawn)`, `currentMap()`, `reset()`, `guardSpawns()`. `src/test/java` -- plain JUnit, no server.

## Tasks & Acceptance

**Execution:**
- [x] `NekotineCore/core/.../navigation/PathRule.java`, `.../astar/AStarSearch.java`, `AStarSearchTest.java` and new `Passage.java` -- the override and its use; tests: the core-override matrix row, default rule unchanged, not asked outside the view.
- [x] `src/main/java/fr/nekotine/vi6clean/bot/Vi6PathRules.java` -- `guard(Vi6Map)`, `thiefOutside(Vi6Map)`, `thiefInside(Vi6Map, boolean leaving)`, plus a package-private factory over plain box lists.
- [x] `ForcefieldHandler.java` -- public field-material constant, used by its fills and by the rule.
- [x] `src/test/java/fr/nekotine/vi6clean/bot/Vi6PathRulesTest.java` -- the matrix on hand-built boxes; a fake `BlockData` (e.g. a `java.lang.reflect.Proxy` answering `getMaterial`) for the gate rows.
- [x] `src/integrationTest/.../it/Vi6MapSpots.java` -- moved helpers; `Vi6PhaseMachineBotIT` calls them.
- [x] `src/integrationTest/.../it/Vi6PathRulesIT.java` -- in infiltration with a guard bot and a thief bot at a spawn with a same-named entrance and exit: guard path to a standable spot in an exit is not reached and no point's cell overlaps an exit or blocking box, while `PathRule.ANY` reaches it; inside thief (after `enterMap`) to its exit: not reached with `leaving` false, reached with true; a gate between two standable cells filled with the field material then air (restored after): guard crosses it closed, thief only open.
- [x] `NekotineCore/README.md` -- mention `PathRule#passage` in the pathfinder entry (French). `NekotineCore` gitlink -- bump.

**Acceptance Criteria:**
- Given `Vi6PathRules`, when reviewed, then it reads map data only through `Vi6Map`'s public getters, and NekotineCore names no Vi6 concept (AD-7).
- Given this story, when NekotineCore `gradlew.bat build integrationTest` and Vi6 `gradlew.bat build integrationTest` run, then both are green, `PathFinderIT`, `PathWalkerIT` and the Epic 4 soak included.

## Implementation Notes

- `Vi6PathRules.of(forbidden, gates, field)` takes the field material: `ForcefieldHandler`'s superclass resolves `Ioc` in its static initializer, so the plain unit test cannot load the constant; the public factories pass `ForcefieldHandler.FIELD_MATERIAL`.
- The test map's `Hangar` entrance has an empty blocking box, so the IT checks the first entrance by name whose blocking box holds blocks.

## Spec Change Log

- 2026-11-23, human renegotiation after approval: `PathRule#opens` (open-only hook) replaced by `PathRule#passage` returning `Passage` (`DEFAULT`, `OPEN`, `FLOOR`, `BLOCKED`), so a rule can open and close blocks, not only open them. Per-search, per-body override chosen over an editable shared snapshot or graph, because guards and thieves need different answers in the same area. KEEP: the Vi6 role rules and the forcefield decisions are unchanged.

## Review Triage Log

| # | Layer | Finding | Verdict | Route | Evidence |
|---|-------|---------|---------|-------|----------|
| 1 | verification-gap, blind | No IT asserts which roles refuse an entrance blocking box; the trigger-box check could skip silently | medium | patch | Fixed: the IT now checks every cell of the first non-empty blocking box against all four rules (the test spawn's own box is empty) and requires a trigger cell outside forbidden boxes. |
| 2 | edge-case | A thief may be routed on top of a closed field, then fall when it opens | low | reject | Intent says thieves override nothing; gates span floor to ceiling on the test maps, so there is no headroom on top. |
| 3 | edge-case | Null blocking box NPEs the IT helper | false | reject | `Entrance#getBlockingBox` defaults to `new BoundingBox()` and is never null. |
| 4 | edge-case | Empty `PathResult` makes `subList(1, 0)` throw | false | reject | Every `AStarPath` holds at least its start. |
| 5 | edge-case | Unreachable could come from node budget, not the rule | false | reject | The `ANY` search from the same start reaches the same goal within budget; the goal cell itself is forbidden. |
| 6 | edge-case, blind | Gate IT depends on map geometry (detour within margin, exact width compare, middle column) | low | reject | Deterministic on the test bundle and passing; gate boxes come from the block-box command (integers). |
| 7 | edge-case | Unused `InMapPhasePlayerWrapper` import left in `Vi6PhaseMachineBotIT` | low | patch | Removed. |
| 8 | blind | `PassageGrid` reads each block up to three times and wraps `PathRule.ANY` too | low | reject | Each block is cached once per search; walker ITs and searches stay green; a skip path adds a branch for no measured cost. |
| 9 | blind | An `OPEN` override on a door drops the `DOOR` hint; a crossed field is hinted `WALK` | false | reject | Spec defines `OPEN` as air; the walker needs no hint since the field opens within 1 block. |
| 10 | blind | No test has a walking guard cross a real placed field | maybe-false (medium if true) | defer | Walker and brains are 5.5 scope; deferred. |
| 11 | blind | Guard rule opens any gray stained glass in a gate, active field or not | low | reject | Spec decision: material in a gate box; only the handler fills that material there. |
| 12 | blind | `BukkitUtil.fillBoundingBoxWith` footprint differs from strict overlap on fractional/negative boxes | low | reject | Pre-existing helper; gate boxes are integer block boxes. |
| 13 | blind | Unit test copies `GRAY_STAINED_GLASS`, nothing ties it to the constant | low | patch | The gate IT now asserts the constant equals the copy. |
| 14 | blind | `thiefOutside(map)` ignores its map | low | patch | Javadoc now says the map is unused and why it is taken. |
| 15 | blind | Missing blank line after the `Vi6MapSpots` constructor | low | patch | Added. |

## Design Notes

`passage` is generic: a game door that opens for one body as it comes near (Vi6 forcefields for guards), a corridor closed at head height (`allows` sees only feet blocks), a temporary bridge. It is per search and per body, so two roles searching the same area in one tick get different answers with no shared state to keep in sync. A precomputed per-map graph stays a separate `PathFinder` implementation (AD-7). The walker needs no change: the field opens when the guard is within 1 block, before its 40-tick stuck timer.

```java
var rule = !wrapper.isInside() ? Vi6PathRules.thiefOutside(map) : Vi6PathRules.thiefInside(map, wantsToLeave);
finder.find(bot.player().getLocation(), goal, rule);
```

## Verification

**Commands:**
- `NekotineCore: gradlew.bat spotlessApply build integrationTest` -- expected: green, `AStarSearchTest` includes the `passage` cases.
- `Vi6Clean: gradlew.bat` then `gradlew.bat build integrationTest` -- expected: green, `Vi6PathRulesTest`, `Vi6PathRulesIT`, `Vi6SoakIT` pass (bare `gradlew` deploys to the dev server).
