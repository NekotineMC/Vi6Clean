---
title: 'Story 3.2: Vi6 test hooks set a game up and reset it between tests'
type: 'feature'
created: '2026-11-11'
status: 'done'
route: 'dispatch'
baseline_commit: '3a1f4e6b0ce71db13d243070bbe8895f03002c4d'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Each Vi6 integration test hand-rolls its teardown, reaches a phase only by readying bots, cannot give a tool without the shop menu, and gets random KoTHs, so tests are slow to write and can leak into each other (FR19, NFR10).

**Approach:** A public test-support class in `src/integrationTest/java` with hooks: put players on teams, go to preparation or infiltration with the state that phase needs, give a tool as its registered stack, teleport a player, force the KoTHs. The reset logic lives in main source (`fr.nekotine.vi6clean.bot`) so Story 4.4's run command reuses it, and the test reset hook only calls it. Main source gains only the seams the hooks need: a forced-KoTH override and a getter for a KoTH's effect.

## Boundaries & Constraints

**Always:** hooks run on the main thread and check phases with `getPhase(...)`, never `isRunning()`. Bots are removed only through `BotRoster.remove` (its default `BotWipe.ALL` deletes their files). With no KoTH override, KoTH selection behaves as today. Hooks touch only Vi6 types and the bot API (AD-13).

**Never:** no fabricated Bukkit events and no direct slot or position writes on bots beyond Bukkit `teleport` (which the bot acks). No bot type in `Vi6Game` or the phases. No NekotineCore change. No `/game` command for the hooks.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Forced KoTHs | override `[(koth, effect)…]` | exactly those KoTHs spawn with those effects, ignoring limit and probability; empty list spawns none | unknown KoTH or effect name throws `IllegalArgumentException` (logged by the phase machine) |
| Reset during end-spectator time | round ended, `complete()` pending | game stays in the lobby afterwards | pending task cancelled on infiltration teardown |
| Reset from any phase | bots on teams, overrides set | lobby, no bots, empty teams, no bot score entries, config reloaded, KoTH override null, bot files gone | N/A |

</frozen-after-approval>

## Code Map

- `src/main/java/fr/nekotine/vi6clean/game/Vi6Game.java` -- teams via `addPlayerInGuards/addPlayerInThiefs`, phase machine, `getScoreboard()`. Add the KoTH override (`record ForcedKoth(String koth, String effect)`, `@Nullable List<ForcedKoth>` get/set).
- `src/main/java/fr/nekotine/vi6clean/game/phase/Vi6PhaseInMap.java` -- `globalSetup` KoTH block (shuffle + `new Random()` roll); also fix `effect.getCode() == "light"` to `equals`.
- `src/main/java/fr/nekotine/vi6clean/map/koth/Koth.java` -- add `@Nullable AbstractKothEffect getEffect()` (null unless set up).
- `src/main/java/fr/nekotine/vi6clean/game/phase/Vi6PhaseInfiltration.java` -- keep the `BukkitTask` from `runTaskLater` in `checkForCompletion`; cancel it in `globalTearDown`.
- `src/main/java/fr/nekotine/vi6clean/bot/Vi6Bots.java` -- `forgetAllBut(...)`, `setBrain(bot, null)`.
- `PhaseMachine.goTo` (NekotineCore) -- from the lobby, `goTo(Vi6PhaseInfiltration, Map<Player,ThiefSpawn>)` builds `Vi6PhaseInMap` (map loaded, tool handlers started) then infiltration (thieves `spawnInMap` ENTERING); guards are NOT spawned (that is preparation's job) → hook calls `getGuards().spawnInMap()`.
- `ToolHandler.makeNewTool()` + `attachToPlayer(tool, player)` -- registered stack without the shop; handlers via `ToolHandlerContainer.getHandlers()`, matched by `getToolCode()`.
- `src/integrationTest/java/fr/nekotine/vi6clean/it/{Vi6PhaseDurationsIT,LogCapture}.java` -- test patterns. Bot files: see `savedFiles` in NekotineCore `BotTickIT`.

## Tasks & Acceptance

**Execution:**
- [x] `Vi6Game.java`, `Vi6PhaseInMap.java`, `Koth.java` -- forced-KoTH override and effect getter -- deterministic KoTH tests.
- [x] `Vi6PhaseInfiltration.java` -- cancel the pending completion task on teardown -- a reset must not be undone by it.
- [x] `src/main/java/fr/nekotine/vi6clean/bot/Vi6GameReset.java` -- `static void reset()`: main thread; remove every roster bot (if roster available), `forgetAllBut(List.of())`, `goTo(Vi6PhaseLobby)`, `setForcedKoths(null)`, `ConfigManager.tryLoad()`, reset game-scoreboard scores of removed bot names -- shared with Story 4.4.
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6TestHooks.java` -- static hooks: `putOnTeams(List<Player> guards, List<Player> thieves)`; `goToPreparation()`; `goToInfiltration(@Nullable String thiefSpawn)` (null = first spawn by name; also spawns guards in map); `giveTool(Player, String toolCode)` returning the `Tool`; `place(Player, Location)`; `forceKoths(ForcedKoth...)`; `reset()` (calls `Vi6GameReset.reset()`, then waits 2 ticks); `spawnBots(int)` via roster with brains cleared.
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6TestHooksIT.java` -- two `@RepeatedTest(3)` tests, each starting with a `reset()` and asserting a clean state: (a) 2 bots, 1v1, forced KoTHs, `goToInfiltration`: assert map loaded, thief ENTERING at chosen spawn, guard INSIDE, exactly the forced KoTHs have effects, tool stack in inventory, `place` lands the bot (position after acks); (b) bots + overrides + infiltration then `reset()`: assert the full reset state from the matrix including bot files deleted. No error logged (`LogCapture`).

**Acceptance Criteria:**
- Given the two hook tests, when run in either order, 3 times each, then all pass.
- Given `Vi6PhaseDurationsIT` and the other ITs, when `integrationTest` runs, then they stay green.

## Implementation Notes

- Branch `feat/bots-3-2-test-hooks` off `bot`.
- The forced-KoTH check runs in `Vi6PhaseInMap`'s constructor (inside `super(...)`, before the field initializers register scoreboard teams): a check in `globalSetup` ran after the lobby's teardown and left `PhaseMachine` wedged on a torn-down lobby. `PhaseMachine` builds the in-map phase by reflection, so the `IllegalArgumentException` reaches the caller wrapped in `RuntimeException(InvocationTargetException)` and is not logged by the machine.
- `Vi6TestHooksIT` has a third test, `forcedKothsAreCheckedAndCanBeEmpty`, for the matrix's error and empty-list cases, and an `@AfterEach` reset so later IT classes start clean (`Vi6BotBrainIT` failed once without it).
- `Koth.clean()` now also clears `isEnabled`, `effect` and `model` so `getEffect()` returns null after a round.

## Review Triage Log

| # | Layer | Finding | Verdict | Route / evidence |
|---|-------|---------|---------|------------------|
| 1 | blind | Diff omits spec and sprint status | false | spec withheld from the blind layer on purpose |
| 2 | blind, edge | `tryLoad()` is a no-op without `config.yml`, so reset keeps in-memory durations | low | reject: `ConfigManager` setup writes the file; fix adds a branch for a state not met in use |
| 3 | blind | "waits for the ack" Javadocs, but fixed `ACK_TICKS` | low | patch: Javadoc reworded |
| 4 | blind | `assertNear` tolerance applies to Y only | low | patch: renamed `yTolerance` |
| 5 | blind, verif-gap | Reset test never proves the round end was pending | medium | patch: asserts thief `hasLeft()` and infiltration still current before reset |
| 6 | blind, verif-gap | `goToPreparation`, named thief spawn, unknown spawn name untested | medium | patch: new repeated test |
| 7 | blind, verif-gap | "Humans stay in the game" never tested | low | reject: the harness has no real human, a stand-in is a bot and is removed |
| 8 | blind | Map content loaded twice; constructor ordering unguarded | low | reject: comment at the `super(...)` call states the ordering |
| 9 | blind | Forced/random spawn code duplicated, `"light"` literal | low | reject: refactor only |
| 10 | blind | `ForcedKoth` doc lists codes, no null check | low | reject: null fails in the constructor check with an exception |
| 11 | blind | `Vi6GameReset` names a run command that doesn't exist | low | patch: Javadoc names Story 4.4 |
| 12 | blind, edge | `spawnBots` reuses `Hook_N` names | low | reject: roster throws a clear "already online" error; reset precedes each test |
| 13 | blind, edge | `Koth.clean()` state if `effect.clean()` throws | low | reject: unlikely, would need try/finally |
| 14 | verif-gap | Random KoTH roll (now in `else`) untested | medium | defer: needs probabilities pinned to 1 |
| 15 | edge | Reset can't recover a machine wedged by a failed setup | medium | defer: NekotineCore `PhaseMachine` |
| 16 | edge | Hooks' "did not start" check never fires (phase registered before setup) | medium | defer: same root cause as 15 |
| 17 | edge | `goTo(Lobby)` throwing skips the rest of reset | low | reject: setup exceptions are caught by the machine |
| 18 | edge | Roster unavailable → bots silently kept | false | no bot can exist without the roster |
| 19 | edge | `goToInfiltration` after preparation respawns guards | low | reject: hook documents going from the lobby |
| 20 | edge | `goToInfiltration` while in infiltration is a no-op | low | reject: misuse, same as 19 |
| 21 | edge | Unloadable map → NPE in `currentMap` | false | `Vi6Game.setMapName` refuses maps that don't load |
| 22 | edge | Full inventory drops the tool stack | low | reject: test bots start empty |
| 23 | edge | `giveTool` outside a map phase gives an inert tool | low | reject: same as the shop, which only exists in a map phase |
| 24 | edge | Missing map metadata gives NPE, not IAE | false | `setMapName` validates metadata |
| 25 | edge | Spec says two tests, three exist | low | reject: fix edits the spec; noted in Implementation Notes |
| 26 | edge | `Koth.clean()` change outside stated scope | low | reject: needed for `getEffect()` to be accurate; noted above |

## Design Notes

Hooks go straight to infiltration rather than through preparation, so no random rune is bought and no shop opens; guards get no sword or gun unless a test gives them. Reset removes bots before `goTo(Vi6PhaseLobby)`, as the existing ITs do (a bot removed mid-round is a plain quit).

## Verification

**Commands:**
- `gradlew.bat` -- expected: Spotless applied, shadow jar built.
- `gradlew.bat build` -- expected: green.
- `gradlew.bat integrationTest` -- expected: all ITs green, including `Vi6TestHooksIT` (6 runs).
