---
title: 'Story 1.7: Bots use their hands, send commands and respawn'
type: 'feature'
created: '2026-11-10'
status: 'done'
route: 'dispatch'
baseline_commit: '1d508b2b2f7c233c8bb7d54d5cd1c4039eb65049'
baseline_commit_nekotinecore: '3d6dc9896c32f4807ede1a88ee93f1caf9feea32'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/NekotineCore/AGENTS.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A bot can walk and look but cannot pick a hotbar slot, click, attack, interact, swap hands, run a command or come back after death, so no host handler for those ever sees a bot act (FR8 hands/other, FR15).

**Approach:** Add hand, command and respawn controls to `Bot`. The slot persists; every other control is a one-shot queued in call order and written as the real serverbound packet a client sends, in the apply step after the move packet. The bot answers each server respawn with a player-loaded ack.

## Boundaries & Constraints

**Always:**
- Public controls on `Bot`, main thread only (`IllegalStateException` off it), Bukkit types only:
  - `setSlot(int)` / `getSlot()`, 0–8 (`IllegalArgumentException` otherwise). Sent as `ServerboundSetCarriedItemPacket` when it differs from the last slot sent. A `ClientboundSetHeldSlotPacket` (plugin `setHeldItemSlot`, cancelled `PlayerItemHeldEvent`) becomes both the control and the last sent slot at the bot's next tick, before its `BotTickEvent`, as a client adopts it.
  - `useItem(EquipmentSlot hand)` → `ServerboundUseItemPacket(hand, seq, lookYaw, lookPitch)`.
  - `useItemOn(Block, BlockFace, EquipmentSlot hand)` → `ServerboundUseItemOnPacket` with a `BlockHitResult` at the centre of that face.
  - `leftClick()` → `ServerboundSwingPacket(MAIN_HAND)` only (server ray trace fires `LEFT_CLICK_AIR` when it hits nothing).
  - `startBreaking(Block, BlockFace)` → `ServerboundPlayerActionPacket(START_DESTROY_BLOCK, pos, dir, seq)` then a main-hand swing.
  - `attack(Entity)` → `ServerboundAttackPacket(id)` then a main-hand swing.
  - `interact(Entity, EquipmentSlot hand)` → `ServerboundInteractPacket(id, hand, target bounding-box centre minus target position, sneak held)`.
  - `swapHands()` → `ServerboundPlayerActionPacket(SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN)`.
  - `sendCommand(String)` → `ServerboundChatCommandPacket` with one leading `/` stripped; null or blank → `IllegalArgumentException`.
  - `respawn()` → `ServerboundClientCommandPacket(PERFORM_RESPAWN)`; the server ignores it for a living bot.
- `hand` is `HAND` or `OFF_HAND`; a block or entity in another world than the bot → `IllegalArgumentException`. No reach check in the library: the server's own checks decide.
- `seq` is a per-bot counter, pre-incremented from 0, as the client's prediction sequence.
- Apply step order: inputs, physics, move (1.6), then the slot, then the one-shot queue in call order. One-shots set inside the bot's `BotTickEvent` apply that tick, elsewhere at the bot's next tick; each is sent exactly once. Writes stop as soon as the connection closes.
- `BotConnection.send` records each `ClientboundRespawnPacket`; the next `sendAcks` sends `ServerboundPlayerLoadedPacket` after the teleport ack, and resets the last sent input to `Input.EMPTY` so held keys are resent, as a fresh client player does.
- `fail()` drops queued one-shots. `BotSelfTest` covers every new NMS reference automatically and must still pass.
- `BotsTestHost` registers one Brigadier command through `LifecycleEvents.COMMANDS` that records its sender and argument where tests can read them.
- **Decision (user, 2026-11-10): the full spec is kept** above the 1600-token target.
- **Decision (agent, 2026-11-10):** right-click controls send no swing packet (`handleAnimate` would fire `LEFT_CLICK_AIR` on a right click).
- Branches: NekotineCore `feat/bots-1-7-hands` from `origin/bot`, PR to `bot`; Vi6Clean from local `bot`, PR to `bot`, with the gitlink.

**Never:**
- No dialog, inventory-click or drop control (AD-4); no release-use-item, continued/stopped breaking or signed commands (logged in `deferred-work.md`).
- No fabricated Bukkit events, no direct `setSelectedSlot`/`attack`/`respawn` calls on the server player.
- No Paper or dev-bundle upgrade; no lockfile changes.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Slot | `setSlot(3)` | one carried-item packet, `PlayerItemHeldEvent` 0→3, slot 3 stays; no packet next ticks | N/A |
| Server slot | plugin `setHeldItemSlot(5)` | `getSlot()` = 5 next tick, no packet sent | N/A |
| Right-click air | holding an item, look up | `PlayerInteractEvent` `RIGHT_CLICK_AIR` with that item | N/A |
| Right-click block | `useItemOn` on stone in reach | `RIGHT_CLICK_BLOCK`, `getClickedBlock()` = that block | N/A |
| Left-click | `leftClick()` / `startBreaking` on stone in reach | `LEFT_CLICK_AIR` / `LEFT_CLICK_BLOCK` with that block | N/A |
| Attack | NoAI pig 2 blocks away | `EntityDamageByEntityEvent`, damager = bot | N/A |
| Interact / swap | pig in reach / items in both hands | `PlayerInteractEntityEvent` / `PlayerSwapHandItemsEvent`, hands swapped | N/A |
| Command | `sendCommand("/bottest hi")` | `PlayerCommandPreprocessEvent` `/bottest hi`; host command runs with the bot as sender | N/A |
| Respawn | bot at 0 health, `respawn()` | `PlayerRespawnEvent`, alive, `PlayerClientLoadedWorldEvent` not a timeout | N/A |
| Bad args | slot 9, other-world block, blank command, off thread | nothing queued | `IllegalArgumentException` / `IllegalStateException` |

</frozen-after-approval>

## Code Map

- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/Bot.java` -- controls + Javadoc (persistence, one-shot order, same-tick rule).
- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotBody.java` -- slot fields, one-shot queue (`List<Packet<?>>` built at call time, or `Runnable`s), `seq`; `apply()` appends slot + queue after the move, re-checking `connection.isConnected()` between writes; `sendAcks()` adopts the held slot and answers respawns; `fail()` clears the queue. Bukkit→NMS: `CraftEntity#getHandle().getId()`, `CraftBlock`/`BlockPos.containing`, `CraftBlock.blockFaceToNotch(face)` for `Direction`, `CraftEquipmentSlot.getHand(slot)` for `InteractionHand`.
- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotConnection.java` -- `record()` cases `ClientboundSetHeldSlotPacket` (AtomicInteger, -1 = none) and `ClientboundRespawnPacket` (AtomicBoolean); takers like `takeTeleportId`.
- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/BotRoster.java` -- Javadoc tick order gains "slot, hands, commands, respawn".
- Server reference (read-only), sources in `~/.gradle/caches/paperweight-userdev/v2/work/applyDevBundlePatches_*/output.jar`: `ServerGamePacketListenerImpl#handleSetCarriedItem` (no event when slot unchanged; kicks outside 0–8), `#handleUseItem` (uses packet rotation; fires `RIGHT_CLICK_AIR`/`_BLOCK`), `#handleUseItemOn` (hit location within 1.0000001 of block centre), `#handlePlayerAction`, `#handleAttack`, `#handleInteract` (kicks on self-interaction), `#handleAnimate`, `#handleChatCommand` (runs via `server.execute`, so the command lands after the write), `#handleClientCommand` (`PlayerList.respawn` reuses the same `ServerPlayer`; `restartClientLoadTimerAfterRespawn`), `#hasClientLoaded` (false between death and the loaded ack, so hand packets are ignored then).
- `NekotineCore/bots-test-host/src/main/java/fr/nekotine/botstesthost/BotsTestHost.java` -- the `bottest <word>` command (Paper `Commands.literal(...).then(argument(word))`), recording into a static thread-safe queue.
- `NekotineCore/bots-test-host/src/integrationTest/java/fr/nekotine/botstesthost/it/BotHandsIT.java` (new) -- reuse `BotLifecycleIT.roster()`, `spawnLocation()`, `cleanUp`, `register(Listener|PacketAdapter)`, `LogCapture`, and the stone-platform setup of `BotMovementIT`; items via `player().getInventory()`.
- `NekotineCore/README.md` -- one line on the new controls.

## Tasks & Acceptance

**Execution:**
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotConnection.java` -- held-slot and respawn recording.
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/Bot.java` -- controls.
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotBody.java` -- controls, queue, apply, acks, fail.
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/BotRoster.java` -- Javadoc.
- [x] `NekotineCore/bots-test-host/src/main/java/fr/nekotine/botstesthost/BotsTestHost.java` -- test command.
- [x] `NekotineCore/bots-test-host/src/integrationTest/java/fr/nekotine/botstesthost/it/BotHandsIT.java` -- every matrix row, plus ProtocolLib receiving adapters proving one packet per one-shot and per slot change, and a same-tick case (one-shot set in `BotTickEvent` reaches the server that tick).
- [x] `NekotineCore/README.md` -- controls line.
- [x] `_bmad-output/implementation-artifacts/deferred-work.md` -- release-use-item, block-breaking progress, signed commands.
- [x] Vi6 gitlink -- commit the new NekotineCore pointer in the Vi6Clean PR.

**Acceptance Criteria:**
- Given `bots-test-host`, when a test spawns a bot holding a diamond, makes it `useItem(HAND)` and removes it, then the host's `PlayerInteractEvent` listener saw the diamond and the bot is no longer online (FR15).
- Given NekotineCore and Vi6Clean on the new pointer, when `gradlew build` and `gradlew integrationTest` run in both (Vi6Clean also bare `gradlew -PVi6CleanJarOutputPath=build/dist`), then all pass, the self-test pass line appears, and `BotLifecycleIT`, `BotTickIT`, `BotSelfTestIT`, `BotMovementIT` stay green.

## Implementation Notes

- One-shots are queued as packet suppliers, built at send time: `useItem` carries the look sent with that tick's move, `interact` the sneak key sent with that tick's input, and `seq` increments in send order. The queue is snapshotted before writing, so a one-shot queued by a listener of an event a one-shot fires goes out next tick (still exactly once).
- `handleChatCommand` runs its `server.execute` inline when the write happens on the main thread (`BlockableEventLoop.execute` only schedules off-thread), so the command runs during the bot's apply step, not after it; the Code Map line saying otherwise is wrong but harmless. Javadoc says the server fires `PlayerCommandPreprocessEvent`, then runs the command.
- Hand checks: a `BlockFace` other than the six cube faces is an `IllegalArgumentException`, like a hand other than `HAND`/`OFF_HAND`; null block/entity is a `NullPointerException`. `"/"` alone is not blank and is sent as an empty command (the server answers "unknown command").
- `BotMovementIT`'s `Structure`, `FLOOR_Y`, `structure()`, `build()` and `clear()` became package-private so `BotHandsIT` reuses the platform.
- Verified: NekotineCore `gradlew build integrationTest` green (59 tests, `BotHandsIT` 13/13, self-test passes with 260 accessors in 6 classes); no "moved wrongly", "invalid carried item" or kick line for `Bot_Hands` in the server log. Vi6Clean bare `gradlew -PVi6CleanJarOutputPath=build/dist`, `gradlew build`, `gradlew integrationTest` green. No lockfile changes.
- Review pass 1 fixes: NekotineCore `2b4262b` (empty commands refused after stripping `/`, one-shots dropped at once on a failed or removed bot, README line, 4 new `BotHandsIT` tests, exact counts); Vi6Clean gitlink `b74708b`. An entity interact fires only `PlayerInteractAtEntityEvent` on this Paper build. Re-verified: NekotineCore `build integrationTest` green (63 tests, `BotHandsIT` 17/17, self-test 260 accessors); Vi6Clean bare `gradlew`, `build`, `integrationTest` (10 tests) green. No lockfile changes.
- The implementation subagent pushed both branches and opened NekotineMC/NekotineCore#11 and NekotineMC/Vi6Clean#85 before review (outside the workflow); the review fix commits are not pushed yet.

## Spec Change Log

## Review Triage Log

Review pass 1 (2026-11-10). Layers: Blind Hunter (B), Edge Case Hunter (E), Verification Gap (G).

| # | Finding | Verdict | Evidence | Route |
|---|---------|---------|----------|-------|
| B1 / E11 | README still says hands, commands and respawn come with the next story | low | `NekotineCore/README.md:90` keeps the old blockquote under the new line | patch |
| B4 / E3 | `"/"` or `"/  "` passes the blank check and is sent as an empty command | low | `sendCommand` checks `isBlank()` before stripping the slash | patch |
| E7 | One-shots called on a failed or removed bot pile up forever, holding their targets | low | `fail()` clears once; a failed bot only runs `tickFailed()`, so later calls are never drained; a brain acting every tick leaks | patch |
| G1 | `useItem` look never shown to reach the server (`RIGHT_CLICK_BLOCK` via look untested) | low | Gap as filed | patch |
| G2 / B9a | `interact` sneak flag never exercised with sneak held | low | Gap as filed | patch |
| G3 / B6 | Held keys resent after respawn untested | low | Gap as filed | patch |
| G4 | Dropping the rest of the queue when a one-shot's listener kicks the bot is untested | low | Gap as filed | patch |
| B8 | Interact and start-breaking tests assert "not empty" instead of exact counts | low | `interactRightClicksThePig`, `startBreakingLeftClicksTheBlock` | patch |
| B10 / G-other | "Queued, then dropped by the failure" comment claims what the test cannot observe | low | A failed bot never runs `apply()`; the clear is unobservable | patch (comment) |
| B3 | Sprint resync deferral (1.6 B2/E2) is now reachable: an attack clears server sprint | medium | Pre-existing, already deferred; `attack()` makes it live | defer |
| B2 / E1 | Attacking self, an item, an orb or an arrow gets the bot kicked | low | Real server behaviour, as for a client; a host bug; guard adds branches | reject |
| B5 / E8 | A pending `setSlot` is overwritten by a server held-slot packet arriving the same tick | low | Frozen rule adopts the server slot as the control; rare | reject |
| B7 | World-change ack untested | low | An extra loaded ack is a no-op when the server is not waiting | reject |
| B9b | OFF_HAND, cascading one-shot, dead-bot, null-argument paths untested | low | Thin wrappers over tested paths | reject |
| B11 | Code Map stale; spec status vs sprint status | false | Fix edits this build's spec; statuses are synced at the end of the workflow | reject |
| B12 / E5 / E6 | Targets checked at queue time; world change or removal before send sends a stale packet | low | One-tick window; the server's reach checks and `PlayerUseUnknownEntityEvent` handle it as for a client | reject |
| E2 | Illegal chat characters get the bot kicked | low | Server behaviour for any client; unlikely in host code | reject |
| E4 | Command spam can kick the bot | low | Spigot spam rule applies to real clients alike | reject |
| E9 | Null face throws NPE, not IAE | false | Null arguments throw NPE like null block/entity (documented in notes) | reject |
| E10 | Test fails on a single-world server | false | It ran green: the test bundle has several worlds | reject |

## Design Notes

One-shots go after the move so the server has the look the host set this tick when it ray-traces a swing or a use; a vanilla client sends clicks before its move packet, one tick stale. The slot is state like the movement keys; everything else is an event a client sends once per key press. `startBreaking` only starts: instant breaks (creative, soft blocks) complete, longer breaks need client-side progress and a `STOP_DESTROY_BLOCK`, deferred.

## Verification

**Commands:**
- `gradlew.bat build` then `gradlew.bat integrationTest` (in `NekotineCore/`) -- expected: BUILD SUCCESSFUL; `BotHandsIT` and every existing IT pass
- `gradlew.bat -PVi6CleanJarOutputPath=build/dist`, `gradlew.bat build`, `gradlew.bat integrationTest` (Vi6Clean) -- expected: all green

**Manual checks:**
- No lockfile changes; no "moved wrongly", "invalid carried item" or kick lines in the `integrationTest` server log.
