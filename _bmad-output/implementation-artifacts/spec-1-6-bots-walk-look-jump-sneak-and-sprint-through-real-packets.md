---
title: 'Story 1.6: Bots walk, look, jump, sneak and sprint through real packets'
type: 'feature'
created: '2026-11-10'
status: 'done'
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
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotPlayer.java` -- simulation window.
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/Bot.java` -- controls.
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotBody.java` -- controls, apply step, neutral on fail, `BotPlayer`.
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/BotRoster.java` -- fail-move diagnosis, Javadoc.
- [x] `NekotineCore/bots-test-host/src/integrationTest/java/fr/nekotine/botstesthost/it/BotMovementIT.java` -- every matrix row; ProtocolLib `PacketAdapter`s on `PLAYER_INPUT`, `ENTITY_ACTION`, `POSITION_LOOK`; a Bukkit listener counting `PlayerToggleSneakEvent`, `PlayerToggleSprintEvent`, `PlayerMoveEvent`, `PlayerFailMoveEvent`.
- [x] `NekotineCore/README.md` -- controls line.
- [x] Vi6 gitlink -- commit the new NekotineCore pointer in the Vi6Clean PR.

**Acceptance Criteria:**
- Given an idle bot with no control change, when 200 ticks pass, then it is not kicked and stays on its platform.
- Given NekotineCore and Vi6Clean on the new pointer, when `gradlew build` and `gradlew integrationTest` run in both (Vi6Clean also bare `gradlew -PVi6CleanJarOutputPath=build/dist`), then all pass, the self-test pass line still appears, and `BotLifecycleIT`, `BotTickIT`, `BotSelfTestIT` stay green.

## Implementation Notes

- Commits: NekotineCore `68aa27e`, `af371dc` (review pass 1 fixes) on `feat/bots-1-6-movement` (from `origin/bot`); Vi6Clean `e28d00a`, `522f07d` (gitlinks) and `e769d43` (docs) on `feat/bots-1-6-movement` (from local `bot`).
- Beyond the code map: `BotSelfTest` now also checks member references made through an internal class (`BotPlayer` calls on `this`) when they resolve to a server member or to nothing; `BotPlayer` also overrides `gameEvent` (bounce events) and `move` (drops the movement recordings the simulation left, so block effects are not applied twice; spectator `noPhysics` moves are not counted).
- ProtocolLib 5.5.0-SNAPSHOT names the input packet `PacketType.Play.Client.STEER_VEHICLE`; tests listen on it.
- Not modelled, as specified: knockback, client sprint rules, flight up/down. Walk speed picks up a sprint change one tick late (the server refreshes it in its own tick). Elytra wall damage can apply inside the simulation (review E7b, rejected).
- The implementation subagent pushed both branches and opened NekotineMC/NekotineCore#10 and NekotineMC/Vi6Clean#84 before review (outside the workflow); the review fix commits are not pushed yet.
- Verified: NekotineCore `gradlew build` + `integrationTest` (14 `BotMovementIT`, 9 `BotSelfTestIT`, 10 `BotLifecycleIT`, 10 `BotTickIT`, 3 smoke); pass line `Bots self-test passed: 189 accessors in 6 classes`; no moved-wrongly/too-quickly lines. Vi6Clean bare `gradlew -PVi6CleanJarOutputPath=build/dist`, `build`, `integrationTest` green. No lockfile changes.

## Spec Change Log

## Review Triage Log

Review pass 1 (2026-11-10). Layers: Blind Hunter (B), Edge Case Hunter (E), Verification Gap (G).

| # | Finding | Verdict | Evidence | Route |
|---|---------|---------|----------|-------|
| E1 | A sneak/input listener that kicks the bot closes the channel; the sprint command then writes on a closed channel and fails the bot | low | `apply()` checks `isConnected()` only after both writes | patch |
| B5 / E6 | Simulated-move counter assumes a recording per position change: the `noPhysics` (spectator) branch moves without recording, so a real server recording is removed | medium | `Entity#move` noPhysics branch calls `setPos` with no `addMovementThisTick`; `removeLatestMovementRecording` then drops the server's last entry | patch |
| B1 / E3 | `fail()` clears getters but the server player keeps the last input (e.g. stays sneaking); Javadoc says every key is released | low | Frozen rule: a failed bot sends nothing; the Javadoc overstates it | patch (Javadoc) |
| B3 | Server/plugin velocity (setVelocity, knockback) silently ignored, undocumented | low | Knockback deferred by user; Javadoc silent | patch (Javadoc) |
| B10 / B11 | Getters return unclamped look; main-thread rule only on setters while Javadoc says "controls" | low | Code as described | patch (Javadoc) |
| B6 / E5b | Creative/spectator flight up/down not modelled or documented | low | Excluded by the frozen Never; documented in the same Javadoc patch | patch (Javadoc) |
| G1 / B9b | `inheritsExternal` never exercised: reverting it keeps `BotSelfTestIT` green | low | Gap as filed | patch |
| G2 / B8 / B9a | Look change and pitch never shown to reach the server; initial-look check cannot fail (spawn at 0,0) | low | Gap as filed | patch |
| G3 / B8 | Backward/left/right, sneak slowdown and sprint speedup have no movement assertion | low | Gap as filed | patch |
| G4 | Teleport velocity reset untested on a moving bot | low | Gap as filed | patch |
| G5 | Fall-distance rewind not observed (no health check) | low | Gap as filed | patch |
| B8b | `PlayerJumpEvent` named in Javadoc, never asserted | low | Grep: no test listens to it | patch |
| B14 | `unchangedControlsSendOnlyMoves` calls `cleanUp` twice | low | Direct deletion | patch |
| B2 / E2 | Sprint/sneak compared only with the last sent input: a server-side `setSprinting(false)` (attack, damage) or a cancelled toggle is never resent, so the bot cannot sprint again without toggling | medium | Frozen rule sends on change only and leaves client sprint rules unmodelled; a real client re-sends START_SPRINTING when its local state differs | defer |
| E7 / E9 | Simulated `travel` not side-effect-free: `stepOn`, walk animation | false | `stepOn` runs in `applyEffectsFromBlocks`, not `travel`; walk animation is client-only; walkDist sits behind the suppressed emission | reject |
| E7b | Gliding bot can take `flyIntoWall` damage inside the simulation | low | `handleFallFlyingCollisions` hurts during `travelFallFlying`; elytra bots unlikely, fix adds a damage guard | reject |
| B4 | Fluid caches stale, walkDist not restored | false | Simulation starts at the server position whose fluid flags the server just computed (same as the client's tick start); walkDist only changes inside the suppressed emission | reject |
| B5b | A zero-length recording can leak | low | It records the current position, which the server's own move covers; no effect | reject |
| E8 | Overrides that stop overriding pass the self-test | false | Every `BotPlayer` override calls `super`, whose Methodref is checked through `ServerPlayer` | reject |
| E4 | Stale `clientOnGround` after a teleport allows one mid-air jump | low | A vanilla client keeps its on-ground flag across a teleport too; unlikely | reject |
| E5a | Dead or riding bot still runs physics and sends moves | low | `handleMovePlayer` ignores immobile (dead) players and only takes rotation from passengers; riding is not in scope | reject |
| B7 | Fail-move warnings not rate-limited | low | Frozen rule: one warning per event | reject |
| B12 | `PosRot` every tick instead of client's minimal packets | low | Frozen rule: one move packet per tick | reject |
| B13 | Boxed `Float` heading invites NPEs | low | Frozen API decision | reject |
| B9c | Broken inherited member named under the internal owner | low | Cosmetic; the member name still identifies it | reject |

## Design Notes

Why simulate on the bot's own `ServerPlayer`: hand-written client physics was rejected in SPEC-bots, and the server already carries everything `travel` needs (attributes, effects, pose, level, collisions). The server never moves a player from input itself (`tickPlayer` runs `doTick` with zero input and snaps back), so the bot runs `travel` in a window it rewinds, then lets `handleMovePlayer` move the player for real; the server's `move(PLAYER)` then produces the real step sounds, jump stats and events once. The `BotPlayer` subclass is the only way to silence `getMovementEmission` during the window; it is still a `ServerPlayer` to Paper, Bukkit and ProtocolLib.

The bot keeps its own client-side velocity: the server's `deltaMovement` for a player is a server shadow that `tickPlayer` and `handleMovePlayer` also change.

## Verification

**Commands:**
- `gradlew.bat build` then `gradlew.bat integrationTest` (in `NekotineCore/`) -- expected: BUILD SUCCESSFUL; `BotMovementIT`, `BotSelfTestIT`, `BotTickIT`, `BotLifecycleIT`, `BotsTestHostSmokeIT` pass
- `gradlew.bat -PVi6CleanJarOutputPath=build/dist`, `gradlew.bat build`, `gradlew.bat integrationTest` (Vi6Clean) -- expected: all green

**Manual checks:**
- No lockfile changes. No "moved wrongly" or "moved too quickly" lines in the `integrationTest` server log.
