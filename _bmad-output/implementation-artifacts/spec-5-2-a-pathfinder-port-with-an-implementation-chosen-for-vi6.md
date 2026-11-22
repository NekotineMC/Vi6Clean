---
title: 'Story 5.2: A pathfinder port, with an implementation chosen for Vi6'
type: 'feature'
created: '2026-11-22'
status: 'done'
baseline_commit: 'cb6de485f558d05b1ef99d6bcba28367636828ad'
nekotinecore_baseline_commit: 'ed2b5ce6b5cff2b7eaa1690499341d9365fdd198'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
  - '{project-root}/NekotineCore/AGENTS.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Walkers (Story 5.3) and Vi6's roaming brains (5.5) need paths that respect game rules (5.4), but `core` has no pathfinder, and AD-7 requires one port whose algorithm can be swapped without touching callers (FR10).

**Approach:** Add a pathfinder port in `core` (`fr.nekotine.core.navigation`): `find(from, to, rule)` completes Paper's `Pathfinder.PathResult`, the caller's `PathRule` decides passability and extra cost per search, and a sub-interface exposes per-point move hints. Ship our own A* as Vi6's implementation: it snapshots the chunks it needs on the main thread, searches off-thread, and completes on the main thread.

## Boundaries & Constraints

**Always:**
- Port and implementation use Java, Bukkit and Paper API only (`core` has no paperweight); no new dependency.
- `find` is called on the main thread (`IllegalStateException` otherwise); from and to in different worlds or either null → `IllegalArgumentException`. It returns a `CompletableFuture<PathResult>` that completes on the main thread.
- The A* reads the world only through `ChunkSnapshot`s taken inside `find`, over the from/to box plus a margin, clamped to world height; unloaded chunks are never loaded and count as impassable. The caller's rule is called only off-thread, only with that snapshot.
- The implementation owns physical walkability for a player-sized body: a solid floor below, two non-solid blocks (or a wooden door) for feet and head; moves are 8-way walk (diagonals only when both sides are clear), a 1-block step up, and drops up to 3 blocks. The rule only removes nodes and adds cost on top.
- Points are feet block positions (integer coordinates, like Paper's mob paths), start first. An unreachable goal returns the path to the explored node closest to the goal, with `canReachFinalPoint() == false`. Results are immutable; `getNextPointIndex()` is 0 (walkers keep their own cursor, AD-6).
- Same inputs and world give the same path (deterministic tie-breaks, NFR10). A search has a node budget; running out counts as unreachable.
- NekotineCore changes go on a branch from NekotineCore `bot`, PR into `bot`; the Vi6 PR (into Vi6 `bot`) bumps the gitlink.

**Never:**
- No walker, no Vi6 rule or brain (Stories 5.3-5.5); no Vi6 game-code change.
- No Pathetic or other pathfinding dependency, no per-map graph, no vanilla mob pathfinder adapter.
- No path caching, chunk loading, swimming, ladders, or jump-gaps.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Around walls | wall between from and to, gap at one end | reachable path through the gap, consecutive points adjacent | N/A |
| Step, drop, door | goal behind a 1-block step, a 2-block drop and a closed wooden door | reachable; hints `STEP_UP`, `DROP`, `DOOR` on those points | N/A |
| Rule forbids region | rule rejects the gap's blocks, a second gap exists | path reaches the goal with no point in the region | N/A |
| Unreachable | goal enclosed | `canReachFinalPoint()` false, final point is the closest reached | N/A |
| World edits after `find` | wall placed the same tick, after the call | path ignores the edit (snapshot) | N/A |
| Off main thread | `find` from an async task | rejected | `IllegalStateException` |
| Bad input | null point, different worlds | rejected at call | `IllegalArgumentException` |

</frozen-after-approval>

## Code Map

- `NekotineCore/core/src/main/java/fr/nekotine/core/navigation/` -- Story 5.1's port (`Body`, `BodyControls`, `ControlArbiter`); new types sit beside it, same Javadoc style (English).
- Paper `com.destroystokyo.paper.entity.Pathfinder.PathResult` -- `getPoints()`, `getNextPointIndex()`, `getNextPoint()`, `getFinalPoint()`, `canReachFinalPoint()`; implement it directly.
- `NekotineCore/core/build.gradle.kts` -- JUnit 5 + `paper-api` on the test classpath, no server: unit tests must not call `Material`/`BlockData` methods that need a server, so cell classification is injectable.
- `NekotineCore/bots-test-host/src/integrationTest/java/fr/nekotine/botstesthost/it/BotMovementIT.java` -- `Structure` (stone fill at `FLOOR_Y`, `clear()`), `spawnLocation`; reuse for the test structure (Story 5.3 reuses it too). The host plugin instance comes from `Bukkit.getPluginManager().getPlugin("BotsTestHost")`.
- `NekotineCore/AGENTS.md` -- states every `core` test is commented out; stale since 5.1 (deferred agent-context fix, do not touch here).

## Tasks & Acceptance

**Execution:**
- [x] `core/.../navigation/BlockView.java` -- read-only `getBlockData(x, y, z)` over world coordinates; what a rule sees -- AD-7 snapshot-only access.
- [x] `core/.../navigation/PathRule.java` -- `boolean allows(BlockView, x, y, z)` for a feet block, `default double cost(...)` (extra, >= 0), `PathRule.ANY` -- caller rule per search.
- [x] `core/.../navigation/PathMove.java` + `HintedPathResult.java` -- enum `START, WALK, STEP_UP, DROP, DOOR`; `HintedPathResult extends PathResult` with `PathMove getMove(int index)` -- AD-6 optional hints.
- [x] `core/.../navigation/PathFinder.java` -- `CompletableFuture<PathResult> find(Location from, Location to, PathRule rule)`, Javadoc with the thread and result rules above -- the port.
- [x] `core/.../navigation/astar/` -- `AStarPathFinder` (plugin, margin, node budget; snapshots, async search via the Bukkit scheduler, main-thread completion), a package-private pure search over a cell classifier, the snapshot `BlockView`, and an immutable hinted result -- Vi6's implementation.
- [x] `core/src/test/java/.../navigation/astar/AStarSearchTest.java` -- grid-based tests: walls, step, drop, door, forbidden region, unreachable closest point, budget, determinism.
- [x] `bots-test-host/src/integrationTest/java/.../it/PathFinderIT.java` -- the matrix on a real structure (walls, 1-block step, drop, door); main-thread completion; snapshot isolation; errors.
- [x] `NekotineCore/README.md` -- inventory entry for the pathfinder (French, like the rest).
- [x] `NekotineCore` gitlink in Vi6 -- bump to the NekotineCore commit.

**Acceptance Criteria:**
- Given the port types, when `core` builds, then they import only Java, Bukkit and Paper types.
- Given NekotineCore with this story, when Vi6 builds, then it compiles unchanged and `gradlew.bat integrationTest` (Epic 4 soak included) passes.
- Given the A* search, when the rule is called, then it is off the main thread and receives the snapshot view, never a live `World`.

## Implementation Notes

- Pathfinder chosen for Vi6: own grid A* (`fr.nekotine.core.navigation.astar.AStarPathFinder`); reasons in Design Notes (path quality: vanilla-like walking model with steps, drops up to 3 and doors; speed: off-thread search on a chunk snapshot, primitive layout after Pathetic's engine; licence: no dependency, so no notice to ship). Pathetic was checked and does run on 26.2 (pure Java 8, fastutil only); it was not chosen because it replaces only the search loop.
- Walking model choices beyond the spec: fences, walls and fence gates block and are not floors; liquids, waterlogged blocks and hazards (fire, magma, cactus, campfires, cobweb, powder snow, berry bush, wither rose, pointed dripstone) block; carpets are open; other partial blocks (slabs, stairs, snow layers) count as full blocks. Diagonals are flat, need both sides clear and allowed by the rule, and never touch a door. Costs: walk 1, diagonal sqrt 2, step 2, door 2, drop 1 + 0.5 per block.
- The rule is also asked about blocks the body passes through (diagonal sides, a drop's edge and fall blocks), never about the start. A start or goal inside a floor is lifted one block when feet and head fit; an airborne one is lowered to a floor within 3 blocks.
- The snapshot box is from/to widened by the margin (default 16), and in y by at least 4 below and 2 above. `find` throws `IllegalStateException` on a disabled plugin; a failed search is logged once at WARNING and completes the future exceptionally; if the plugin is disabled mid-search the future may never complete (documented).
- NekotineCore commits on `feat/bots-5-2-pathfinder`: 578833f (implementation), 1806aa7 (review fixes).
- Final verification (2026-11-23): NekotineCore `gradlew.bat spotlessApply build integrationTest` green (`AStarSearchTest` 17/17, `PathFinderIT` 9/9); Vi6 `gradlew.bat spotlessApply build integrationTest` green (`Vi6SoakIT` 3/3), then bare `gradlew.bat` deployed the jar to the dev server.

## Spec Change Log

## Review Triage Log

Pass 1 (blind, edge-case, verification-gap layers).

| # | Finding | Verdict | Evidence | Route |
|---|---------|---------|----------|-------|
| 1 | A drop never asks the rule about the edge column or the cells it falls through (edge-case) | medium | `expand` checks only passability before `relax` at the landing; a Story 5.4 blocking box over the edge would be crossed | patch |
| 2 | An airborne goal (or start) is never lowered to the floor, so the goal is unreachable and the budget burns (edge-case; blind) | medium | the goal test needs an exact y; `liftOutOfFloor` only lifts, and checks feet but not head | patch |
| 3 | With a margin of 0 the floor and head cells fall outside the snapshot (edge-case) | low | `SnapshotView.take` box is from/to y ± margin; the constructor allows 0; direct correction | patch |
| 4 | Solid hazards (magma, cactus, campfires) are floors and wither rose / pointed dripstone are open, while the Javadoc says hazards block (edge-case; blind) | low | `classify` checks `isSolid()` before the hazard switch; direct correction | patch |
| 5 | Carpets count as full blocks, so a 2-high doorway with a carpet threshold is impassable (blind) | medium | carpets are `isSolid()`; a body fits over a 1/16 carpet in vanilla; factory maps may carpet doorways | patch |
| 6 | Disable before the async task runs leaves the future pending; the Javadoc claims exceptional completion (edge-case; blind) | low | Bukkit cancels a disabled plugin's queued tasks; tracking futures adds state, so fix the doc only | patch (doc) |
| 7 | `find` on a disabled plugin throws `IllegalPluginAccessException`, outside the port's `@throws` (edge-case; blind) | low | `runTaskAsynchronously` throws it; convert to the port's `IllegalStateException` | patch |
| 8 | Failed searches are not logged, so a rule bug disappears behind `thenAccept` (blind) | low | `catch (Throwable)` only completes the future; one log line | patch |
| 9 | The rule is asked about blocks that are not feet positions of the path (diagonal sides) (blind) | low | the `PathRule` Javadoc names only feet positions; asking about blocks the body passes is right, the doc is incomplete | patch (doc) |
| 10 | `classify` untested beyond stone, air and oak door (verification-gap) | medium | pre-verified: no test reaches `SnapshotView.classify` with fences, walls, liquids, waterlogged or hazards | patch |
| 11 | Exceptional completion of a failed off-thread search untested (verification-gap) | medium | pre-verified: no IT uses a throwing or bad-cost rule | patch |
| 12 | Unloaded chunks untested (verification-gap) | medium | pre-verified: every IT runs in loaded chunks; height clamp part is low (Vi6 maps sit well inside height) and rejected | patch |
| 13 | Rule-forbidden diagonal side untested (verification-gap) | medium | pre-verified: both rule tests detour through a far gap, no diagonal straddles the region | patch |
| 14 | No diagonal into or out of a door untested (verification-gap) | medium | pre-verified: every door sits in a 1-wide corridor | patch |
| 15 | "Different worlds" check falls back to the null-world case on a one-world server (verification-gap; blind) | low | `orElse(noWorld)`; the IT server loads the nether and end, so assert a second world exists instead | patch |
| 16 | Negative coordinates never reach `key()` in tests (blind) | low | packing is correct by reading (x sign-extended in the top 26 bits, z and y masked), but a one-line test is cheap | patch |
| 17 | Test comment in `noDiagonalPastACorner` does not match the code (blind) | low | comment says (0,0) to (1,2), code starts at (0,1,1) | patch |
| 18 | Unbounded from/to box snapshots many chunks on the main thread; full-height copies (edge-case; blind) | low | only loaded chunks are copied and Vi6 callers path inside one map; a cap adds a contract rule and a branch | reject |
| 19 | NaN coordinates overflow the box math (edge-case) | false | `getBlockX()` of NaN floors to 0; no overflow, no negative array | reject |
| 20 | A step or drop into a door is hinted `STEP_UP`/`DROP`, not `DOOR` (edge-case; blind) | low | one hint per point loses one fact either way; walkers must infer doors from the world (AD-6) | reject |
| 21 | Searches cannot be cancelled (blind) | low | each search is capped by its node budget; cancellation needs a new flag through the search | reject |
| 22 | Gitlink bump and spec not in the diff (edge-case; blind) | false | the gitlink points at the new commit in the working tree and is committed with the Vi6 PR, with the spec | reject |
| 23 | Sprint status says in-progress during review (blind) | false | the workflow moves it at presentation; not a defect of the change | reject |

## Design Notes

**Choice for Vi6: own A*.** Pathetic 5.5.2 (`api` + `engine`, MIT, Java 8, no Bukkit or NMS, one runtime dependency, fastutil) runs on 26.2, but it gives only the search loop: we would still write the snapshot, the walking model (floor, headroom, step, drop, door) as its validators, the hints and a `PathResult` adapter, and it would add a shaded dependency, its MIT notice, a lock entry and its own executor. The port keeps a Pathetic adapter possible later. The human chose own A* (2026-11-22) and named Pathetic's engine as the reference for speed: packed `long` coordinate keys mapped to dense `int` ids, primitive cost arrays, an indexed min-heap with decrease-key; no boxed nodes. Paper's mob pathfinder needs a mob and cannot take a caller rule. A per-map graph needs per-map precomputation and goes stale when gates or doors change. A grid A* over a snapshot of a Vi6-sized map (a few hundred blocks, about 50 chunks) runs in milliseconds off-thread, so 6 bots re-pathing every few seconds costs only the main-thread snapshot.

`find` returns a future because AD-7 requires off-thread searches to complete on the main thread; a synchronous implementation completes it before returning.

```java
PathFinder finder = new AStarPathFinder(plugin);
finder.find(bot.getLocation(), goal, (view, x, y, z) -> !forbidden.contains(x, y, z))
      .thenAccept(path -> walker.follow(path)); // runs on the main thread
```

## Verification

**Commands:**
- `NekotineCore: gradlew.bat spotlessApply build` -- expected: green, `AStarSearchTest` runs.
- `NekotineCore: gradlew.bat integrationTest` -- expected: green, `PathFinderIT` included.
- `Vi6Clean: gradlew.bat` then `gradlew.bat build integrationTest` -- expected: green; bare `gradlew` deploys to the dev server's `plugins/update` and restarts it, which the human accepts.
