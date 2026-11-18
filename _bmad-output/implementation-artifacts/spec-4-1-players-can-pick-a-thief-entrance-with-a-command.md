---
title: 'Story 4.1: Players can pick a thief entrance with a command'
type: 'feature'
created: '2026-11-17'
status: 'done'
baseline_commit: 'dd545570631da74d62e89b8f3ba63cb5c170cee6'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A thief can choose an entrance in preparation only by clicking a minimap ArmorStand, which bots cannot do (no entity interaction), so a bot-only game cannot pick entrances (FR20).

**Approach:** Add `/game entrance <name>` (name decided by the user on 2026-11-17), a subcommand that a thief player runs with an entrance name during preparation. It sets the selected spawn through the same `PreparationPhasePlayerWrapper.setSelectedSpawn` the ArmorStand click uses, and suggests the current map's entrance names.

## Boundaries & Constraints

**Always:** The command is a player command usable by humans and bots alike, with no bot check. A valid selection goes through `setSelectedSpawn`, so the confirmation message is the click's own. Refusals send a red message to the player and change nothing. Names match the current map's `getThiefSpawns()` keys exactly.

**Never:** Change the ArmorStand click, the random-spawn fallback in `handleComplete`, or `/game ready`. Do not add the preparation-ready command (Story 4.2) or any brain (Story 4.3). No test hook call from main code.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Valid pick | Thief in game, preparation active, existing name | Selected spawn = that `ThiefSpawn`; "Entrée X sélectionnée" | N/A |
| Re-pick | Thief already selected A, picks B | Selected spawn = B | N/A |
| Guard | Guard in preparation | Refused, selection stays `null` | Red message: only thieves pick an entrance |
| Wrong phase | Lobby, in-map or infiltration, or player not in the game | Refused, nothing changes | Red message: only during preparation |
| Unknown name | Thief in preparation, name not in the map | Refused, previous selection kept | Red message naming the input and listing the entrances |
| No pick | Thief never picks | Random spawn at preparation end, as today | N/A |

</frozen-after-approval>

## Code Map

- `src/main/java/fr/nekotine/vi6clean/Vi6Commands.java` -- `/game` command tree (`lobby`, `team`, `ready`); add the new subcommand here, CommandAPI style as `ready`, `executesPlayer`.
- `src/main/java/fr/nekotine/vi6clean/wrapper/PreparationPhasePlayerWrapper.java` -- `setSelectedSpawn(ThiefSpawn)` sets the spawn and sends the confirmation; reuse, do not change.
- `src/main/java/fr/nekotine/vi6clean/game/phase/Vi6PhasePreparation.java` -- `onPlayerInteractAtEntity` (click path), `handleComplete` + `randomSpawn` (fallback); `getParent().getMap().getThiefSpawns()` gives the current map's `Map<String, ThiefSpawn>`. Do not change.
- `src/main/java/fr/nekotine/vi6clean/wrapper/PlayerWrapper.java` -- `isThief()` for the team check.
- `NekotineCore/core/.../game/phase/PhaseMachine.java` -- `getPhase(type)` returns the phase only while it runs, `null` otherwise: the phase check.
- `WrappingModule.getWrapperOptional(p, PreparationPhasePlayerWrapper.class)` -- empty for a player outside the game.
- `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6TestHooks.java` -- `spawnBots`, `putOnTeams`, `goToPreparation`, `thiefSpawn`, `reset`; `Vi6PhaseMachineBotIT` shows `bot.sendCommand("game ready")`.

## Tasks & Acceptance

**Execution:**
- [x] `src/main/java/fr/nekotine/vi6clean/Vi6Commands.java` -- add the subcommand with one `GreedyStringArgument` (accented or spaced names) whose suggestions are the running preparation's map entrance names (empty outside preparation), sorted. Checks in order: preparation running and player has a `PreparationPhasePlayerWrapper`; player is a thief; name is a key of `getThiefSpawns()`; then `setSelectedSpawn`. Expose the name lookup as a public static method so the suggestion source is testable.
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6SpawnSelectIT.java` -- with 1 guard bot and 1 thief bot in preparation, cover each matrix row through `bot.sendCommand`, asserting the wrapper's selected spawn on the main thread; assert the suggestion method returns the map's names in preparation and nothing in the lobby; `Vi6TestHooks.reset()` after each test.

**Acceptance Criteria:**
- Given a thief in preparation, when the command runs with one of the map's entrance names, then the selected spawn equals what clicking that entrance's ArmorStand sets, and the player gets the same message.
- Given the command, when a player types its argument, then the current map's entrance names are suggested.
- Given the existing ArmorStand click and no command use, when preparation ends, then click selection and random fallback behave as before.

## Implementation Notes

- `Vi6Commands.entranceNames()` is the public suggestion source; `selectEntrance` treats "no preparation running" and "player has no preparation wrapper" as one wrong-phase refusal, as the matrix groups them.
- `Vi6SpawnSelectIT` checks tab completion through `Bukkit.getCommandMap().tabComplete`, so the `replaceSuggestions` wiring is covered, not only the helper.

## Spec Change Log

## Review Triage Log

| # | Layer | Finding | Verdict | Route / evidence |
|---|-------|---------|---------|------------------|
| 1 | verification-gap, blind, edge-case | Refusal and confirmation messages are never asserted; the outsider and infiltration checks cannot fail because of the command | medium | defer: bots expose no received chat; needs a capture hook in NekotineCore `bots` or the harness (deferred-work.md) |
| 2 | verification-gap | Tab completion wiring untested, only `entranceNames()` | low | patch: `suggestionsAreTheMapsEntrancesInPreparation` now asserts `Bukkit.getCommandMap().tabComplete(thief, "game entrance ")` |
| 3 | blind | Wrong-phase test packs lobby, outsider and infiltration, with a redundant `reset()` | low | patch: split into three tests, reset dropped; the outsider test also asserts the thief's selection stays null |
| 4 | blind | Re-pick and suggestion tests skip `LogCapture` | low | patch: both wrapped with `assertClean` |
| 5 | edge-case, blind | Case or trailing-space mismatch refused while suggestions filter case-insensitively | low | rejected: the frozen intent says names match `getThiefSpawns()` keys exactly |
| 6 | edge-case | Map with no thief spawns shows an empty entrance list | low | rejected: unlikely (`randomSpawn` already fails on such a map) and the fix adds a branch |
| 7 | edge-case | Fixed 5-tick wait could flake on a slow host | false | waits count server ticks, not wall time, and a bot one-shot queued off its tick is sent at its next tick |
| 8 | blind | An outsider in preparation gets the "only during preparation" message | false | the matrix's wrong-phase row lists "player not in the game" with that message |
| 9 | blind | No test uses a name with spaces or accents | low | rejected: depends on the test map; the unknown-name test sends "Pas une entrée" through the greedy argument without error |
| 10 | blind | No test covers the ArmorStand click path (AC3) | low | rejected: the click handler and fallback are unchanged in the diff; the fallback has its own test |
| 11 | blind | `setUp` spawns the outsider in every test | low | rejected: harmless extra bot join, removing it adds a parameter |
| 12 | blind | Review diff omits the planning artifacts | false | the workflow reviews source changes; the spec is the claims file, not review content |

## Verification

**Commands:**
- `gradlew.bat` -- expected: Spotless applied, shadow jar built.
- `gradlew.bat build` -- expected: BUILD SUCCESSFUL (spotlessCheck + plain tests).
- `gradlew.bat integrationTest` -- expected: `Vi6SpawnSelectIT` and the existing ITs pass.

**Manual checks (if no CLI):**
- On the dev server in preparation, as a thief, tab-complete the argument and pick an entrance; as a guard, get refused.
