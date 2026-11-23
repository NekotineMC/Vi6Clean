---
title: 'Story 5.3: A vanilla-like walker follows any PathResult'
type: 'feature'
created: '2026-11-23'
status: 'done'
baseline_commit: '565bd3b0e4bf105bd5601eede6df71c85c17bbba'
nekotinecore_baseline_commit: 'ad6fd70369294e04f5657692139ece277be9001c'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
  - '{project-root}/NekotineCore/AGENTS.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 5.2 finds paths, but nothing drives a body along one: Vi6's roaming brains (5.5) and Epic 6's melee controller need a walker that gets a body over steps, drops and doors, says when it arrived or got stuck, and follows a goal that moves (AD-6, AD-8).

**Approach:** Add `PathWalker` in `core` (`fr.nekotine.core.navigation`): the caller ticks it from the body's tick event; it follows any `PathResult` with its own cursor, writing movement, aim and (for doors) hands at `ControlPriority.WALKER`. A goal mode (`walkTo(goal, finder, rule)`) re-paths through the 5.2 `PathFinder` when the caller moves the goal and swaps paths without a neutral tick.

## Boundaries & Constraints

**Always:**
- Java, Bukkit and Paper API only; the walker touches the body only through `Body` and `body.controls(WALKER)`, and reads the world only on the main thread.
- Main thread only (`IllegalStateException` otherwise). `follow(null)`, an empty path or a path in another world than the body → `IllegalArgumentException`.
- The path is read-only: points are copied once; `getNextPointIndex()` is ignored; the walker keeps its own cursor.
- Moves are inferred without hints: step up from point y, drop from point y, door from the world (a closed wooden door in the next point's feet or head block is right-clicked through `useItemOn`, at most once per 10 ticks). `HintedPathResult` hints are used only to confirm the same moves.
- `tick()` returns a `WalkStatus` (`IDLE`, `WALKING`, `ARRIVED`, `STUCK`). Each tick spent `WALKING` writes the whole movement group (heading, forward, jump, the other keys released) and a look toward the next point. On `ARRIVED`, `STUCK` or `stop()`, it writes neutral movement once (all keys released, heading `null`), then writes nothing until it gets a new path or goal.
- `ARRIVED`: the body stands within 0.5 blocks horizontally of the final point's block centre, at its level, and the path reaches its goal. If `canReachFinalPoint()` is false, reaching the end reports `STUCK`.
- `STUCK`: no new point reached and no 0.1-block gain toward the current point for `stuckTicks` ticks (default 40, constructor-settable).
- Moving goal: calling `walkTo` again while walking only moves the goal. A new search starts when the goal is 1 block or more from the last searched goal and no search is in flight. Movement goes on along the old path until the new one lands. The swap happens at the walker's next tick, never in the future's callback. The cursor resumes at the first point after the one nearest the body. No neutral tick and no reset of the stuck timer to `IDLE`.
- Same structure and inputs give the same walk: same visited feet blocks, same status (NFR10).
- Exceptions from `tick()` propagate to the caller (Story 5.5's brain guard logs them with game context).
- NekotineCore changes go on a branch from NekotineCore `bot`, PR into `bot`; the Vi6 PR (into Vi6 `bot`) bumps the gitlink.

**Never:**
- No Vi6 change beyond the gitlink, no rule or brain (5.4-5.5), no melee (Epic 6).
- No sprinting, swimming, ladders, door closing, path smoothing or own physics; never teleport or set velocity.
- No own `TickElapsedEvent` listener or scheduler task for driving the body.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 5.2 course | `AStarPathFinder` path over two steps, a 2-block drop, a closed oak door | `ARRIVED`; door opened by the bot; no `PlayerFailMoveEvent` | N/A |
| Plain path | same course, path wrapped to hide hints | `ARRIVED` | N/A |
| Vanilla path | a zombie's `getPathfinder().findPath` around a wall with a step | `ARRIVED` | N/A |
| Blocked | wall placed across the corridor after planning | `STUCK` within `stuckTicks` + 10; bot's keys released and it stays put | N/A |
| Unreachable | path with `canReachFinalPoint()` false | walks to its end, `STUCK` | N/A |
| Moving goal | `walkTo` A, goal moved to B mid-walk | reaches B, never `IDLE`/`STUCK`/`ARRIVED` or forward released before | N/A |
| Bad call | null, empty, other-world path; off main thread | rejected | `IllegalArgumentException` / `IllegalStateException` |

</frozen-after-approval>

## Code Map

- `NekotineCore/core/src/main/java/fr/nekotine/core/navigation/` -- `Body` (location, velocity, on-ground, look, `controls(priority)`), `BodyControls`, `ControlPriority.WALKER`, `PathFinder`, `PathRule`, `HintedPathResult`/`PathMove`; the walker sits beside them, same English Javadoc style. `Body` Javadoc says: write the whole group state every driving tick.
- `core/.../navigation/astar/AStarPathFinder` -- the finder used in tests and in goal mode; results complete on the main thread; points are feet block positions, start first.
- `NekotineCore/bots-test-host/src/integrationTest/java/fr/nekotine/botstesthost/it/PathFinderIT.java` -- `course(Structure)` (sealed corridor: steps at dz -5 and -2, drop at 1, door at 4, from dz -9 to 8) and `wallWithGaps`; make them package-visible and reuse.
- `.../it/BotMovementIT.java` -- `Structure`, `FLOOR_Y`, `structure()`, `clear()`, `MoveEvents` (records `PlayerFailMoveEvent` reasons in `fails`); `BotLifecycleIT.roster()/register()/cleanUp()`; `BotControlPortIT` -- pattern for a per-bot `BotTickEvent` listener and `bot.body()`.
- `bots/.../BotRoster.java` -- logs each bot `PlayerFailMoveEvent` as a warning; the IT asserts `MoveEvents.fails` is empty.
- `NekotineCore/README.md` -- module inventory (French); add the walker beside the pathfinder entry.

## Tasks & Acceptance

**Execution:**
- [x] `core/.../navigation/WalkStatus.java` -- enum `IDLE, WALKING, ARRIVED, STUCK` with Javadoc -- the report.
- [x] `core/.../navigation/PathWalker.java` -- `PathWalker(Body)`, `PathWalker(Body, int stuckTicks)`, `follow(PathResult)`, `walkTo(Location, PathFinder, PathRule)`, `stop()`, `tick()`, `getStatus()`, `getPath()` -- the walker, per the constraints above.
- [x] `core/src/test/java/.../navigation/PathWalkerTest.java` -- unit tests on a fake `Body` (records writes) and fake paths: cursor advance, step-up jump, arrival, stuck timer, neutral writes, unreachable → `STUCK`, seamless path swap, argument errors. The world is read only for doors, so keep door reads behind a package-private seam the test can stub.
- [x] `bots-test-host/.../it/PathWalkerIT.java` -- the matrix on a real bot; the course walk runs 3 times in one test and compares visited feet blocks and statuses.
- [x] `bots-test-host/.../it/PathFinderIT.java` -- `course`, `wallWithGaps` package-visible.
- [x] `NekotineCore/README.md` -- inventory entry for the walker (French).
- [x] `NekotineCore` gitlink in Vi6 -- bump to the NekotineCore commit.

**Acceptance Criteria:**
- Given the walker types, when `core` builds, then they import only Java, Bukkit and Paper types.
- Given the walker following a path, when a `BRAIN` writer sets aim the same tick, then the brain's aim applies and the walker's movement still does (priority, not ownership).
- Given NekotineCore with this story, when Vi6 builds, then it compiles unchanged and `gradlew.bat integrationTest` (Epic 4 soak included) passes.

## Implementation Notes

- Intermediate points count as reached when the body's feet block is the point's block (on the ground for a drop point); the walker also skips one point when the body is already in the next one's block (passed between two ticks).
- Seams for `PathWalkerTest`: a package-private constructor takes the main-thread check and a `Doors` reader (closed wooden door at a position); public constructors use `Bukkit::isPrimaryThread` and the live world.
- Hints are not read: geometry and the world give the same moves.
- While waiting for the first path of a goal, `WALKING` ticks write released keys and no look (there is no point yet); they count toward `stuckTicks`. A failed search keeps the current path, or is `STUCK` when there is none. In goal mode, the end of a path is neither `ARRIVED` nor `STUCK` while the goal is 1 block or more from the goal that path was searched for.
- On a path swap the stuck timer keeps counting; only the reference distance resets to the new current point.
- `PathWalkerIT` vanilla row: the bot spawns before the zombie (an unaware, non-burning adult) so a player keeps its chunk ticking; the zombie plans from the ground and is removed before the walk.
- NekotineCore commits on `feat/bots-5-3-walker`: f2c04e8 (implementation), f41e766 (review fixes).
- Final verification (2026-11-23): NekotineCore `gradlew.bat spotlessApply build integrationTest` green (`PathWalkerTest` 23/23, `PathWalkerIT` 7/7, `PathFinderIT` 9/9); Vi6 bare `gradlew.bat` (deployed to the dev server), then `gradlew.bat build integrationTest` green (`Vi6SoakIT` 3/3).

## Spec Change Log

## Review Triage Log

Pass 1 (blind, edge-case, verification-gap layers).

| # | Finding | Verdict | Evidence | Route |
|---|---------|---------|----------|-------|
| 1 | `arrived()` ignores on-ground, so a path ending in a drop reports `ARRIVED` mid-air (blind; edge-case) | medium | `arrived` checks level and radius only; a falling body has its feet in the final block for a tick before landing, and momentum can carry it past 0.5 | patch |
| 2 | Waiting for a goal's first path is never shown to end in `STUCK` or to write neutral keys with no look (verification-gap; blind) | medium | pre-verified: no test ticks a pending search `stuckTicks` times | patch |
| 3 | Nothing pins that a path swap keeps the stuck timer running (verification-gap) | medium | pre-verified: adding `stall = 0` to `adopt` passes every test | patch |
| 4 | The body changing world mid-walk never reaches `STUCK` in a test (verification-gap; blind) | low | pre-verified; the fix is one cheap test | patch |
| 5 | Skip-ahead branch (`cursor + 2`) never runs in a test (blind) | low | `cursorAdvancesAndTheBodyArrives` moves one point at a time; one cheap test | patch |
| 6 | Class Javadoc says every method throws off the main thread; `getStatus`/`getPath` do not (blind) | low | direct doc correction | patch |
| 7 | Zombie leaks if `awaitUntil` times out or `findPath` throws (edge-case) | low | removed only on the success path; move removal to `finally` | patch |
| 8 | A failed search is never retried (`searchedGoal` kept), so the old path's end can only go `STUCK`; a failed first search ignores a goal moved in flight (edge-case; blind; verification-gap) | low | real, but searches fail only on a finder bug or a disabled plugin (`AStarPathFinder` logs them); `STUCK` hands the decision back to the caller, as the spec intends; a retry adds branches and could spin on a persistent failure | reject |
| 9 | A slow search (over `stuckTicks`) reports `STUCK`, first path or re-path (blind; edge-case; verification-gap) | low | the spec counts waiting toward `stuckTicks`; A* over a Vi6-sized snapshot runs in milliseconds (5.2), so 2 s is far off; exempting waits adds state | reject |
| 10 | Search errors are not logged or exposed (blind) | low | `AStarPathFinder` logs each failure once (5.2); exposing errors adds public surface | reject |
| 11 | The future callback writes non-volatile fields from any thread (blind) | false | the `PathFinder` port requires completion on the main thread (`PathFinder.java:47`) | reject |
| 12 | Frozen intent says hints "confirm" moves; code never reads them (blind) | false | "used only to confirm" limits hint use, it does not require it; AD-6 says walkers must work without | reject |
| 13 | Door click has no range check or aim (blind) | low | every A* and vanilla path has one point per block, so the door is 1-2 blocks away; the course IT opens the door; sparse custom paths are not a current caller | reject |
| 14 | Moving-goal IT does not assert the old path stays in use (blind) | low | `aMovedGoalSwapsPathsAtTheNextTickWithoutANeutralTick` asserts the old path is followed while the search is in flight | reject |
| 15 | `badCallsAreRejected` fails on a one-world server (blind) | false | the IT server loads the nether and end; 5.2 settled asserting it (5.2 triage #15) | reject |
| 16 | Story status is `in-progress` during review (blind) | false | the workflow moves it; not a defect of the change | reject |
| 17 | Gitlink jump `1806aa7` → `f2c04e8` may carry unrelated commits (blind) | false | `1806aa7..f2c04e8` holds only the content-free PR #13 merge `ad6fd70` (empty diff) and this story's commit | reject |
| 18 | Waiting at the old path's end for a newer goal, the body oscillates around the point (edge-case) | low | lasts until the new path lands, a tick or two with A*; the swap then moves on | reject |
| 19 | Fresh `walkTo` whose `find` throws leaves the walker `WALKING` with no path (edge-case) | low | the caller gets the exception, and `find` throws only off the main thread or on a disabled plugin; the walker then reports `STUCK` | reject |
| 20 | On a swap, a later point across a wall can be the nearest, skipping the detour (edge-case) | false | a goal search starts at the body's block, so point 0 or 1 is always nearest after a tick or two of movement; for `follow`, the frozen spec sets the nearest-point rule | reject |
| 21 | A body knocked 2+ points ahead is driven back to passed points (edge-case) | low | it walks back at most a point and then advances again; it is not stuck; a scan window adds a branch with no current caller (knockback arrives with Epic 6) | reject |
| 22 | Stall carried from the wait into the first path can fire `STUCK` on its first tick (verification-gap other) | low | same root as #9: needs a search near `stuckTicks` long | reject |

## Design Notes

Doors need a hands write, which the story's "movement and aim" leaves out: a closed door blocks a player, and `PathMove.DOOR` already says the body may have to open it. Opening uses the walker priority's hands, so a melee or brain hands write that tick wins and the walker retries 10 ticks later.

Point targets are block centres (x + 0.5, z + 0.5). A step is jumped when the next point is one block up and the body is on the ground within 1.2 blocks horizontally. A drop is walked off with no special input, and the cursor waits for the body to land at the lower level.

```java
var walker = new PathWalker(bot.body());
walker.walkTo(goal, finder, rule);          // from a brain
// in the bot's BotTickEvent listener:
switch (walker.tick()) { case ARRIVED, STUCK -> brain.pickNextGoal(); default -> {} }
```

## Verification

**Commands:**
- `NekotineCore: gradlew.bat spotlessApply build` -- expected: green, `PathWalkerTest` runs.
- `NekotineCore: gradlew.bat integrationTest` -- expected: green, `PathWalkerIT` and `PathFinderIT` included.
- `Vi6Clean: gradlew.bat` then `gradlew.bat build integrationTest` -- expected: green; bare `gradlew` deploys to the dev server, which the human accepts.
