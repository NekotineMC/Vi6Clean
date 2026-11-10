---
title: 'Story 1.6: Bots walk, look, jump, sneak and sprint through real packets'
type: 'feature'
created: '2026-11-10'
status: 'in-progress'
route: 'dispatch'
baseline_commit: '8a1ee5cc1e3c4b713634050b5e3830a3de8fc620'
baseline_commit_nekotinecore: 'edc92732d2526367a4bc4d2a79786ac3589a554e'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/NekotineCore/AGENTS.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A bot can join, tick and leave, but cannot move or look: hosts have no movement or aim control, so no `PlayerMoveEvent`, sneak or sprint handler ever sees a bot act.

**Approach:** Add movement (heading, forward/back/left/right, jump, sneak, sprint) and aim (yaw/pitch) controls to `Bot`. Each tick after the bot's `BotTickEvent`, the body writes changed inputs as real serverbound packets, steps the server's own `travel` physics on its `ServerPlayer` along the heading in a side-effect-free simulation, rewinds, and submits the result through `handleMovePlayer` as `PosRot` with the look.

## Boundaries & Constraints

**Always:**
- Controls (public, on `Bot`, main thread only, `IllegalStateException` off it): `setForward/Backward/Left/Right/Jump/Sneak/Sprint(boolean)`, `setHeading(Float yaw)` (`null` = follow the look yaw, the default), `setLook(float yaw, float pitch)`, plus matching getters. All persist until changed. Initial look = spawn location's yaw/pitch; keys off.
- Apply step, in `tickAfterEvent` before the flush, so writes inside `BotTickEvent` apply that tick and writes elsewhere apply at the bot's next tick:
  1. If `Input(forward, backward, left, right, jump, sneak, sprint)` differs from the last one sent, write `ServerboundPlayerInputPacket` inbound (→ `handlePlayerInput`, `PlayerToggleSneakEvent`).
  2. If sprint changed, also write `ServerboundPlayerCommandPacket` `START_SPRINTING`/`STOP_SPRINTING` (→ `handlePlayerCommand`, `PlayerToggleSprintEvent`), as a client does. Sprint follows the control; client sprint rules (hunger, walls) are not modelled.
  3. Physics step, then write `ServerboundMovePlayerPacket.PosRot(target, look yaw, look pitch, onGround, horizontalCollision)` inbound every tick (→ `handleMovePlayer`, `PlayerMoveEvent`).
- Physics step = the server's own code on the bot's own `ServerPlayer`, in a "simulating" window: save server state; load the bot's client state (velocity, onGround, collision flags); set yRot to the heading; map keys to `xxa`/`zza` as the client does (normalized, ×sneaking-speed attribute when crouching, ×0.98); apply a ground jump (`jumpFromGround` math without stats/exhaustion, 10-tick delay) or liquid jump like `LivingEntity.aiStep`; call `travel`; read the new position, velocity, onGround and collisions into the client state; restore server state. While simulating, movement emits no sounds or game events and nothing is awarded. On a teleport ack the client velocity resets to zero.
- `PlayerFailMoveEvent` for a bot of this roster logs one warning starting with the bot's name, with reason and target.
- `fail()` sets every control neutral. A failed bot sends no inputs and runs no physics.
- `BotSelfTest` covers every new NMS reference automatically (no list to edit); it must still pass.
- **Decision (user, 2026-11-10): knockback is deferred.** Server-sent velocity (`ClientboundSetEntityMotionPacket`, explosion knockback) is ignored in this story; logged in `deferred-work.md`.
- **Decision (user, 2026-11-10): the full spec is kept** above the 1600-token target.
- Branches: NekotineCore `feat/bots-1-6-movement` from `origin/bot`, PR to `bot`; Vi6Clean from local `bot`, PR to `bot`, with the gitlink.

**Never:**
- No `setPosition`, teleport or velocity control in the API; no direct `setShiftKeyDown`/`setSprinting`/position call outside the simulating window; no fabricated Bukkit events.
- No hand-written physics formulas beyond the client-side key mapping and the jump branch above.
- No hands, commands or respawn (1.7); no creative-flight vertical controls; no Paper or dev-bundle upgrade; no lockfile changes.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Walk | forward held on a flat platform | `PlayerMoveEvent`s, position advances along the heading, zero `PlayerFailMoveEvent` | N/A |
| Heading ≠ look | heading 90, look yaw 0 | moves toward −X, `player().getLocation().getYaw()` stays 0 | N/A |
| Step up | forward + jump into a 1-block step | ends standing on top of it | N/A |
| Drop | forward off a 3-block ledge | lands on the lower floor, on ground | N/A |
| Sneak / sprint | toggle on then off | one input packet per change seen by ProtocolLib; toggle event fired each change; sprint also one command packet | N/A |
| Same-tick | set forward inside `BotTickEvent` | input packet in that same tick | N/A |
| Unchanged | no control change for 20 ticks | no input packet; still one move packet per tick | N/A |
| Refused move | listener refuses a `PlayerFailMoveEvent` | one log line starting with the bot name | N/A |

</frozen-after-approval>

## Code Map

- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/Bot.java` -- add the controls (Javadoc: persistence, same-tick rule, heading default, main thread).
- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotBody.java` -- control fields, last sent `Input`, client state; `tickAfterEvent` = apply step then `flush()`; `fail()` neutralizes; `join` builds a `BotPlayer` instead of `new ServerPlayer`; teleport ack resets client velocity in `sendAcks`.
- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotPlayer.java` (new, package-private, `extends ServerPlayer`) -- `simulate(...)`: the save/load/travel/restore window. Overrides `getMovementEmission()` (→ `NONE` while simulating, else `super`). Accesses protected `jumping`, `stuckSpeedMultiplier`, `getJumpPower()`, `jumpInLiquid(TagKey)`; public `xxa`, `zza`, `moveDist`, `flyDist`, `setOnGround`, `setDeltaMovement`, `absSnapTo`. Saves/restores also `fallDistance`, `horizontalCollision`, `verticalCollision`, `verticalCollisionBelow`, `minorHorizontalCollision`, `stuckSpeedMultiplier`.
- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/BotRoster.java` -- `@EventHandler(MONITOR)` on `io.papermc.paper.event.player.PlayerFailMoveEvent`, matching `event.getPlayer()` against its bots; Javadoc tick order gains "inputs, physics, move".
- Mapped server sources (read-only, for reference): `~/.gradle/caches/paperweight-userdev/v2/work/applyDevBundlePatches_*/output.jar` -- `ServerGamePacketListenerImpl#handlePlayerInput` (sneak event only if `hasClientLoaded`), `#handlePlayerCommand` (sprint event), `#handleMovePlayer` (does its own `move(PLAYER)` and `jumpFromGround` + `PlayerJumpEvent` when it sees a jump, so the simulation must not award), `#tickPlayer` (already runs `doTick` then snaps to `firstGood`); `LivingEntity#aiStep` jump branch, `#travel`; `Player#canSimulateMovement` is true server-side; `Entity#move` skips fall damage for client-authoritative players.
- `NekotineCore/bots-test-host/src/integrationTest/java/fr/nekotine/botstesthost/it/BotMovementIT.java` (new) -- reuse `BotLifecycleIT.roster()`, `cleanUp`, `register(...)`, `PaperServer.awaitTicks`, `LogCapture` from `BotTickIT`. Build the structure with Bukkit `setType` on a stone platform at y≈200 above spawn, cleared in `finally`.
- `NekotineCore/README.md` -- one line on the controls.

## Tasks & Acceptance

**Execution:**
- [ ] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotPlayer.java` -- simulation window.
- [ ] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/Bot.java` -- controls.
- [ ] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotBody.java` -- controls, apply step, neutral on fail, `BotPlayer`.
- [ ] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/BotRoster.java` -- fail-move diagnosis, Javadoc.
- [ ] `NekotineCore/bots-test-host/src/integrationTest/java/fr/nekotine/botstesthost/it/BotMovementIT.java` -- every matrix row; ProtocolLib `PacketAdapter`s on `PLAYER_INPUT`, `ENTITY_ACTION`, `POSITION_LOOK`; a Bukkit listener counting `PlayerToggleSneakEvent`, `PlayerToggleSprintEvent`, `PlayerMoveEvent`, `PlayerFailMoveEvent`.
- [ ] `NekotineCore/README.md` -- controls line.
- [ ] Vi6 gitlink -- commit the new NekotineCore pointer in the Vi6Clean PR.

**Acceptance Criteria:**
- Given an idle bot with no control change, when 200 ticks pass, then it is not kicked and stays on its platform.
- Given NekotineCore and Vi6Clean on the new pointer, when `gradlew build` and `gradlew integrationTest` run in both (Vi6Clean also bare `gradlew -PVi6CleanJarOutputPath=build/dist`), then all pass, the self-test pass line still appears, and `BotLifecycleIT`, `BotTickIT`, `BotSelfTestIT` stay green.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Design Notes

Why simulate on the bot's own `ServerPlayer`: hand-written client physics was rejected in SPEC-bots, and the server already carries everything `travel` needs (attributes, effects, pose, level, collisions). The server never moves a player from input itself (`tickPlayer` runs `doTick` with zero input and snaps back), so the bot runs `travel` in a window it rewinds, then lets `handleMovePlayer` move the player for real; the server's `move(PLAYER)` then produces the real step sounds, jump stats and events once. The `BotPlayer` subclass is the only way to silence `getMovementEmission` during the window; it is still a `ServerPlayer` to Paper, Bukkit and ProtocolLib.

The bot keeps its own client-side velocity: the server's `deltaMovement` for a player is a server shadow that `tickPlayer` and `handleMovePlayer` also change.

## Verification

**Commands:**
- `gradlew.bat build` then `gradlew.bat integrationTest` (in `NekotineCore/`) -- expected: BUILD SUCCESSFUL; `BotMovementIT`, `BotSelfTestIT`, `BotTickIT`, `BotLifecycleIT`, `BotsTestHostSmokeIT` pass
- `gradlew.bat -PVi6CleanJarOutputPath=build/dist`, `gradlew.bat build`, `gradlew.bat integrationTest` (Vi6Clean) -- expected: all green

**Manual checks:**
- No lockfile changes. No "moved wrongly" or "moved too quickly" lines in the `integrationTest` server log.
