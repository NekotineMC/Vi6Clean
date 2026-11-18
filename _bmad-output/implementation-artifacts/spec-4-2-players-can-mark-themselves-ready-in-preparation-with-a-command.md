---
title: 'Story 4.2: Players can mark themselves ready in preparation with a command'
type: 'feature'
created: '2026-11-18'
status: 'done'
baseline_commit: 'b44f2d42b21e43fda0dabf6ac1eac4b9c5d8bce6'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A player can end preparation early only through the "Prêt" toggle in the shop inventory menu, which bots cannot click (inventory-click is deferred), so a bot-only game always waits out the preparation timer (FR20).

**Approach:** Add `/game prepready` (name decided by the user on 2026-11-18), which any player in the game runs during preparation to toggle their `PreparationPhasePlayerWrapper` ready state through `setReadyForNextPhase`, the setter the "Prêt" toggle uses, so `checkForCompletion()` runs on ready and preparation ends as soon as everyone is ready. It toggles like `/game ready` (decided by the user on 2026-11-18): a second run un-readies.

## Boundaries & Constraints

**Always:** A player command usable by humans and bots, with no bot check; guards and thieves alike. The ready state goes through `setReadyForNextPhase`, never the field. Refusals send a red message and change nothing. The "Prêt" items on the shop page and the runes page show the command's state, including when the menu is open.

**Never:** Change `/game ready` (stays lobby-only, decided 2026-11-05), `/game entrance`, the preparation timer, or `checkForCompletion`. Add no brain (Story 4.3). No test hook call from main code.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Ready, others not | Player in game, not ready, preparation active | Player ready; preparation keeps running; green confirmation "Prêt" | N/A |
| Un-ready | Player already ready | Player not ready; preparation keeps running; confirmation "En attente" | N/A |
| Last one ready | Every other player already ready | Preparation completes at once; infiltration starts | N/A |
| Guard or thief | Either team | Same behavior | N/A |
| Wrong phase | Lobby, in-map/infiltration, or player not in the game | Nothing changes | Red message: only during preparation |
| Menu open | Player has the shop open when the command runs | "Prêt" item redraws to the new state | N/A |

</frozen-after-approval>

## Code Map

- `src/main/java/fr/nekotine/vi6clean/Vi6Commands.java` -- `/game` tree (`lobby`, `team`, `ready`, `entrance`); add the subcommand here with `executesPlayer`, refusal style as `selectEntrance`. `/game ready` uses `LobbyPhasePlayerWrapper` and toggles: do not touch.
- `src/main/java/fr/nekotine/vi6clean/wrapper/PreparationPhasePlayerWrapper.java` -- `setReadyForNextPhase(boolean)` sets the field and, when true, calls `Vi6PhasePreparation.checkForCompletion()`. The two "Prêt" `BooleanInputMenuItem`s (`readyItem`, `runesReadyItem`) are constructor locals that only redraw when clicked; keep them as fields and call `askRedraw()` on both in `setReadyForNextPhase` (harmless double redraw on a click).
- `NekotineCore/core/.../inventory/menu/element/BooleanInputMenuItem.java` -- reads state through the supplier; `askRedraw()` refreshes it. Do not change.
- `src/main/java/fr/nekotine/vi6clean/game/phase/Vi6PhasePreparation.java` -- `checkForCompletion()` completes when every game player's wrapper is ready; `globalTearDown` closes inventories. Do not change.
- `PhaseMachine.getPhase(Vi6PhasePreparation.class)` -- `null` unless preparation runs; `WrappingModule.getWrapperOptional(p, PreparationPhasePlayerWrapper.class)` -- empty outside the game. Same wrong-phase check as `selectEntrance`.
- `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6SpawnSelectIT.java` -- template for the new IT: `setUp` (guard, thief, outsider), `send` with `COMMAND_TICKS`, `LogCapture` + `assertClean`, `Vi6TestHooks.reset()` after each test.

## Tasks & Acceptance

**Execution:**
- [x] `src/main/java/fr/nekotine/vi6clean/wrapper/PreparationPhasePlayerWrapper.java` -- keep both "Prêt" items as fields and redraw them in `setReadyForNextPhase` -- so the menu shows a command-set state.
- [x] `src/main/java/fr/nekotine/vi6clean/Vi6Commands.java` -- add the subcommand: refuse unless preparation runs and the player has a `PreparationPhasePlayerWrapper`; otherwise toggle through `setReadyForNextPhase(!isReadyForNextPhase())` and confirm the new state (green "Prêt" / red "En attente", as the menu items).
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6PrepReadyIT.java` -- with a guard, a thief and an outsider bot, cover each matrix row through `bot.sendCommand` (menu row: assert both items' state via the wrapper's supplier, not a real open inventory), plus `/game ready` in preparation leaving the ready state unchanged; `reset()` after each test.

**Acceptance Criteria:**
- Given preparation with the guard ready, when the thief runs the command, then infiltration is running within the command wait, well before the preparation timer.
- Given `/game ready` run in preparation, when it is processed, then the preparation ready state is unchanged.
- Given the "Prêt" menu toggle and no command use, when a player clicks it, then it behaves as before.

## Implementation Notes

- `PreparationPhasePlayerWrapper.getReadyItems()` is public so `Vi6PrepReadyIT` (another package) can assert both "Prêt" items read and draw the command state.
- `/game prepready` sends its confirmation before calling the setter, because the last ready player ends preparation inside `setReadyForNextPhase`.
- The menu redraw is checked through the items' state and `draw()`, not an open inventory; an open menu is the manual check.

## Spec Change Log

## Review Triage Log

| # | Layer | Finding | Verdict | Route / evidence |
|---|-------|---------|---------|------------------|
| 1 | verification-gap, edge-case, blind | Menu test reads `getValue()`/`draw()`, which read the supplier live, so deleting the `askRedraw()` calls keeps it green | medium | patch: `MenuInventory.askRedraw` → `redraw()` fills the backing inventory, empty until then; assert the shop menu's `getInventory()` contents instead |
| 2 | edge-case, blind | `getReadyItems()` throws on a wrapper built with a null player, and is test-only public API | low | patch: deleted; the test reads `getMenu()`, already public |
| 3 | verification-gap, edge-case, blind | Confirmation and refusal chat messages never asserted; infiltration refusal test only fails on a logged exception | medium | defer: bots expose no received chat (same gap as Story 4.1, deferred-work.md) |
| 4 | edge-case, blind | Lobby refusal test checks fresh preparation wrappers, so it cannot fail | low | patch: also assert the `LobbyPhasePlayerWrapper` ready state is unchanged right after the lobby run |
| 5 | blind | `aSecondRunUnReadies` never checks the state between the two runs | low | patch: assert ready after the first run |
| 6 | edge-case | Setter guards `readyItem` only before redrawing `runesReadyItem` | false | the constructor's only early return precedes both assignments, so both are null or both set |
| 7 | edge-case | If `complete()` throws, the player already got "Prêt" | low | rejected: needs a broken map (no thief spawns), and the command then fails loudly anyway; the order is deliberate (Implementation Notes) |
| 8 | blind | AC3 click path untested; a click now redraws twice, the second after completion | low | rejected: click code unchanged apart from one extra redraw before completion; the post-completion redraw from `toggleValue` predates this change |
| 9 | blind | Review diff omits the spec and sprint status | false | the workflow reviews source changes; the spec is the claims file |
| 10 | blind | No verification evidence | false | `build/test-results/integrationTest/TEST-...Vi6PrepReadyIT.xml`: 9 tests, 0 failures; all other IT suites green |

## Verification

**Commands:**
- `gradlew.bat` -- expected: Spotless applied, shadow jar built.
- `gradlew.bat build` -- expected: BUILD SUCCESSFUL.
- `gradlew.bat integrationTest` -- expected: the new IT and existing ITs pass.

**Manual checks (if no CLI):**
- On the dev server in preparation, open the shop, run the command: the "Prêt" item turns green; with everyone ready, preparation ends.
