---
title: 'Story 3.3: Bots drive a representative tool, the Portable Teleporter'
type: 'feature'
created: '2026-11-16'
status: 'done'
route: 'dispatch'
baseline_commit: '34370c3e0e716a779ba73aee4442fd5c3dd92fa3'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** No Vi6 tool is tested end to end with a bot, so the path test hooks → real bot inputs → tool handler → observable effect is unproven before per-tool tests are written (FR7, NFR10).

**Approach:** One integration test class drives the Portable Teleporter with a guard bot in a hook-built infiltration: place a pad, pick it up, place two pads and teleport between them, and observe the glow packets the tool sends to the bot. Test code only, unless the test exposes a Vi6 bug.

## Boundaries & Constraints

**Always:** the bot acts only through `Bot` controls (`setSlot`, `setLook`, `setSneak`, movement keys, `startBreaking`/`leftClick`); the tool comes from `Vi6TestHooks.giveTool`; every test starts and ends with `Vi6TestHooks.reset()`; phases checked with `getPhase(...)`; no error or exception logged (`LogCapture`).

**Never:** no fabricated Bukkit events, no reflection into `PortableTeleporter`'s private fields, no direct position or slot writes on the bot beyond the `place` hook. No NekotineCore change. No tests for other tools.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Place | guard holds the tool on solid floor, sneaking, looks down, left-clicks | one pad (`BlockDisplay` of a structure block) at its feet; name shows `[2/3]` | N/A |
| Pick up | sneaking, looking at that pad, left-clicks | pad entity gone; name back to `[3/3]` | N/A |
| Teleport | pads A and B, bot on B looks at A, not sneaking, left-clicks, holds forward and jump | bot stays put for the 40-tick channel, then is at A; ack accepted (position kept, still online, roster state not failed) | N/A |
| Packets | tool wielded with a pad placed | the bot receives `ENTITY_METADATA` for the pad entity (glow) | no exception logged |

</frozen-after-approval>

## Code Map

- `src/main/java/fr/nekotine/vi6clean/tool/personal/portable_teleporter/PortableTeleporterHandler.java` -- under test, do not change unless a bug is found. `onPlayerInterract`: needs `HIT_ANY` (`LEFT_CLICK_AIR`/`LEFT_CLICK_BLOCK`) with the main hand. Sneaking: picks up `tool.aimed` if any, else places a pad at the feet (needs the block 0.1 below solid, < 3 pads). Not sneaking: teleports to `aimed` (no cooldown). `aimed` is recomputed each tick in `onTick` by ray from the eye against each pad's box (pad location ±0.75 horizontally, ±0.5 vertically), only while the tool is in a hand; glow packets go out the same tick via `EntityGlowModule.glowEntityFor` (ProtocolLib `ENTITY_METADATA` + team packet). Teleport: 40-tick channel (`teleportation_delay: 2.0`), gravity/speed/jump modifiers ×0, `onPlayerMove` resets moves, then `owner.teleport(pad location)`.
- `src/main/resources/tools/portable_teleporter.yml` -- 3 charges, 2 s delay, 8 s cooldown.
- `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6TestHooks.java` -- reuse `reset`, `spawnBots`, `putOnTeams`, `goToInfiltration`, `giveTool`, `place`, `guardSpawns`. `giveTool` adds the stack with `addItem` (first free slot).
- `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6TestHooksIT.java` -- patterns: `LogCapture`, `assertNear`, `@AfterEach` reset, `PaperServer.onMain/awaitTicks`.
- NekotineCore `bots/.../Bot.java` -- controls apply at the bot's next tick; `leftClick` fires `LEFT_CLICK_AIR` only if nothing is hit; `startBreaking(block, face)` fires `LEFT_CLICK_BLOCK`. `BotState` for "not failed".
- Guards are in ADVENTURE; `test-bundle` has `spawn-protection=0`. Infiltration lost duration defaults to 240 s, so a 1v1 round does not end during the test.

## Tasks & Acceptance

**Execution:**
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/PortableTeleporterBotIT.java` -- `@RepeatedTest(3)` tests, each: `reset()`, 2 bots, 1 guard + 1 thief, `goToInfiltration(null)`, `giveTool(guard, "portable_teleporter")`, `bot.setSlot(<slot holding it>)`. (a) place then pick up, asserting pad entity and the `[n/3]` name after each; (b) place A, `place` the bot on an open floor spot 3 blocks away, place B, aim at A, click, assert held during the channel, then at A after the ack and still connected; a ProtocolLib sending `PacketAdapter` (registered in a `try/finally`) records `ENTITY_METADATA` for the bot and the pad entity id. `@AfterEach` reset. Helpers: find the tool's slot by `TOOL_ID_KEY`, find pads as `BlockDisplay` entities with structure-block data, aim helper computing yaw/pitch from eye to a point, read the stack name as plain text.

**Acceptance Criteria:**
- Given `PortableTeleporterBotIT`, when `gradlew integrationTest` runs, then each test passes 3 times and every other IT stays green.
- Given the test classes, when run in either order, then results are the same (reset before and after each test).

## Implementation Notes

- Branch `feat/bots-3-3-portable-teleporter` off `bot`. No Vi6 main-source change: the test found no bug in the tool.
- Tick-sensitive loops read `Bukkit.getCurrentTick()`: an `awaitTicks(1)` + `onMain` round takes about 3 server ticks, so counting loop iterations misread the channel length.
- The held-in-place sampling stops 5 ticks before the channel can end and releases the keys there.
- Seen while debugging, outside this story: `TickElapsedEvent` had 6 `SharedActionBar` listeners in one run and 8 in the next (one per game, never removed?). Logged in `deferred-work.md`.

## Review Triage Log

| # | Layer | Finding | Verdict | Route / evidence |
|---|-------|---------|---------|------------------|
| 1 | verif-gap, blind | Post-teleport cleanup untested: speed/gravity/jump modifiers removed, 8 s cooldown set | medium | patch: dropping `removeModifier` or `setCooldown` in `onTick` leaves every assertion green |
| 2 | blind | "Held in place" is vacuous if forward/jump never move the bot | medium | patch: same fix as 1, the bot must move with forward held after arrival, proving the controls work |
| 3 | verif-gap | Comment "assertion errors inside onMain only reach the log" is wrong | low | patch: `PaperServer.onMain` rethrows the task's failure (`PaperServer.java:134-153`); delete the comment |
| 4 | verif-gap | Glow bit may come from `EntityGlowModule`'s listener rewriting any metadata | low | reject: either way the module registered the pad for the bot and the packet reached it, which is what the spec asks |
| 5 | blind | No check that the thief gets no glow | low | reject: outside the story's packet AC, extra recorder per test |
| 6 | blind | AQUA aimed highlight untested | low | reject: the teleport to A only happens if `aimed` is A |
| 7 | blind | Glow failure message dumps every metadata packet | low | patch: print only the pad ids' entries |
| 8 | blind, edge | `pads()` counts any structure-block display in the world | low | reject: reset removes pads (`onToolCleanup`), the map has none; a snapshot diff adds code for an unmet state |
| 9 | blind, edge | Channel length, charges and material hard-coded from the yml | low | reject: test-bundle runs with the jar defaults; reading the config needs new accessors |
| 10 | blind | `clickTick` is one tick before the click lands | low | reject: margins absorb it; only a failure message would be off by one |
| 11 | blind | `assertTrue(!x)` instead of `assertFalse(x)` | low | patch: direct correction |
| 12 | blind | No line-of-sight check from B to A | low | reject: the pad ray ignores blocks by design; adds a ray-trace for no tested behaviour |
| 13 | blind | Spec and sprint status missing from the diff | false | withheld from the review layers on purpose |
| 14 | edge | 1e-6 drift threshold stricter than the handler's 1e-5 clamp | low | patch: use 1e-5, matching `onPlayerMove` |
| 15 | edge | Cleanup `onMain` in `finally` can mask the primary failure | low | reject: needs a main-thread timeout during a failing test |
| 16 | edge | `a.getBlock().getRelative(DOWN)` is wrong on a slab floor | low | patch: use `a.clone().subtract(0, 0.1, 0).getBlock()`, as `clickFloor` does |
| 17 | edge | `displayName()` null gives an NPE | false | `onAttachedToPlayer` and every edit set the name |

## Design Notes

Placing and picking up are both sneak + left-click (the tool's lore says so); a plain left-click teleports. The epic AC's "left-clicks" for placing is read as that real gesture.

Clicks use `startBreaking` on the block under the target pad (face UP): it fires `LEFT_CLICK_BLOCK` whatever the map has within reach, whereas `leftClick` fires nothing if the swing hits a block. The handler cancels the event, so no block breaks. Pads 3 blocks apart keep the target within block reach and keep the ray toward A out of B's box.

"Held in place" is asserted as the bot's position not changing while forward and jump are held during the channel; the attribute modifiers and `onPlayerMove` both contribute, the test does not separate them.

Packet delivery is observed with a ProtocolLib sending listener filtered on the bot as receiver, plus no logged exception; that is the outbound route a ProtocolLib send takes to the bot's connection, without changing NekotineCore.

## Verification

**Commands:**
- `gradlew.bat` -- expected: Spotless applied, shadow jar built.
- `gradlew.bat build` -- expected: green.
- `gradlew.bat integrationTest` -- expected: all ITs green, `PortableTeleporterBotIT` 6 runs.
