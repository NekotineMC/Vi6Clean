---
title: 'Spike: ProtocolLib inbound injection on a bot EmbeddedChannel'
type: 'chore'
created: '2026-11-03'
status: 'done'
route: 'dispatch'
baseline_commit: 'c81fc00306aa8dd10ff8d03e95325f2fb0580257'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/specs/spec-bots/SPEC.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** SPEC-bots requires bot packets to enter through the fake connection's channel pipeline so ProtocolLib inbound listeners (`ClientTrackModule`) see them, but its last Assumption — that ProtocolLib injects into an `EmbeddedChannel` and a decoded packet written inbound reaches its listeners and then the `Connection` — is unverified.

**Approach:** One integration test on the in-process server joins a minimal fake player (`ServerPlayer` on a `Connection` over an `EmbeddedChannel`, via `PlayerList.placeNewPlayer`), writes a decoded serverbound packet inbound, and asserts ProtocolLib injected, its listener saw the packet with the right player, and the game handled it. The outcome, pass or fail, is written back into SPEC-bots.

## Boundaries & Constraints

**Always:** Real NMS objects and the real `placeNewPlayer` join path, so `PlayerJoinEvent` fires and ProtocolLib injects on its own. Packets go in through `EmbeddedChannel.writeInbound`, never straight to `handleX`. Game work runs through `PaperServer.onMain`. The test removes its fake player and its packet listener even when an assertion fails, so other ITs see a clean server.

**Decision — spike test is dropped:** once the findings are in SPEC-bots, the test is deleted; the user later chose to squash the branch, so the test code is not kept in history.

**Never:** No NekotineBots library, bot API, ticking, physics or teleport acks — only what this check needs. No change to `src/main`, NekotineCore or paper-junit-server. No calling ProtocolLib's injector by hand to make the test pass; if it does not inject on its own, that is the finding.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Injection | Fake player joined via `placeNewPlayer` | Channel pipeline holds `protocol_lib_inbound_interceptor` | Assertion message lists the pipeline handler names |
| Pass-through | `ServerboundSetCarriedItemPacket(3)` written inbound; receiving `PacketAdapter` on `HELD_ITEM_SLOT` | Listener fires once, `event.getPlayer()` is the bot; bot's held slot becomes 3 | Assertion names which hop failed (listener vs handler) |
| Cancel | Same packet, listener cancels it | Listener fires; held slot unchanged | Same |

</frozen-after-approval>

## Code Map

- `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6SmokeIT.java` -- pattern: package, `PaperServer.onMain`, `Ioc.resolve`.
- `NekotineCore/src/main/java/fr/nekotine/core/track/ClientTrackModule.java` -- the real consumer: receiving `PacketAdapter` on `HELD_ITEM_SLOT` among others, cancels packets.
- Compile classpath: the `integrationTest` suite adds `main`'s compile classpath (`PaperJunitServerPlugin.java:107-108`), so the Paper dev bundle (mojang-mapped NMS) and ProtocolLib 5.4.0 API are available; at runtime test classes load through Vi6Clean's classloader, which joins ProtocolLib's (`paper-plugin.yml` `join-classpath: true`). No build change needed.
- NMS (26.2): `new Connection(PacketFlow.SERVERBOUND)`; `Connection.configureInMemoryPipeline(pipeline, flow)` / `configurePacketHandler(pipeline)`; `new ServerPlayer(MinecraftServer, ServerLevel, GameProfile, ClientInformation)`; `PlayerList.placeNewPlayer(Connection, ServerPlayer, CommonListenerCookie)`; `CommonListenerCookie.createInitial(GameProfile, boolean)`; `PlayerList.remove(ServerPlayer)`.
- ProtocolLib runtime (test-bundle `ProtocolLib.jar`, 5.5.0-SNAPSHOT), from bytecode: `PacketFilterManager$1.handleJoin`/`handleLogin` call `getInjector(player).inject()`. `NettyChannelInjector.inject()` returns silently unless the channel `isActive()`; it anchors on handler names `inbound_config` else `decoder` (adds `protocol_lib_inbound_interceptor` after, `protocol_lib_inbound_protocol_getter` before) and `outbound_config` else `encoder` (adds `protocol_lib_wire_packet_encoder` after) — `addAfter` throws if the anchor is missing. It also replaces `Connection.channel` with a `NettyChannelProxy`, so keep the raw `EmbeddedChannel` reference for `writeInbound`.

## Tasks & Acceptance

**Execution:**
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/ProtocolLibEmbeddedChannelIT.java` -- create the test covering the three matrix rows, with fake-player setup/teardown in helpers and a `try/finally` cleanup -- the spike itself.
- [x] `_bmad-output/specs/spec-bots/SPEC.md` -- rewrite the ProtocolLib Assumption bullet as a verified (or refuted) fact, naming what the bot body must provide (active channel, anchor handler names, raw channel kept for `writeInbound`) or the workaround needed -- downstream bot work builds on it; cite the commit that holds the spike test.
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/ProtocolLibEmbeddedChannelIT.java` -- delete in a separate commit after the findings are recorded -- per the drop decision.

**Acceptance Criteria:**
- Given the spike test on the branch, when `gradlew integrationTest` runs, then the new test runs and the three existing ITs still pass.
- Given the test outcome, when SPEC-bots is read, then its ProtocolLib Assumption states the observed result instead of "unverified" and names the commit holding the spike.
- Given the final branch, when the tree is inspected, then the spike test no longer exists and `gradlew build` is green.

## Implementation Notes

- Spike test was added, run and deleted on the branch; the branch was then squashed at the user's request, so the code is gone (it built a `ServerPlayer` on `new EmbeddedChannel(false, false)` with `configureInMemoryPipeline` + `configurePacketHandler`, swapped `decoder` for `inbound_config`, called `setupOutboundProtocol`, then `placeNewPlayer`). Result: verified, with two setup requirements found on the way (recorded in SPEC-bots): the `decoder` must be swapped for `inbound_config` before `placeNewPlayer` (otherwise it stays HANDSHAKING and ProtocolLib listeners never fire, though injection and `handleX` still work), and outbound writes must be flushed before the channel closes.

## Spec Change Log

## Review Triage Log

| # | Source | Finding | Verdict | Evidence | Route |
|---|--------|---------|---------|----------|-------|
| 1 | edge+blind | SPEC-bots cites spike by short hash `7da6faa` | medium | `main` history is linear and the repo allows rebase/squash merges; either rewrites the hash, so the citation dangles | patch |
| 2 | blind | `event.getPlayer()` checked by UUID only, SPEC-bots claims "is the bot" | low | Test asserts UUID equality only; `ClientTrackModule` keys a `HashMap<Player,…>` on the object, so the claim overstates; wording fix is a direct correction | patch |
| 3 | blind | No packet field read through `PacketContainer` | low | Test never calls `event.getPacket()`; "Not checked" omits it | patch |
| 4 | blind | "maps packets by the decoder's protocol" stated as fact | low | Cause came from bytecode plus the observed fix, never tested on its own | patch |
| 5 | edge+blind | `join` failing after `register()`/`placeNewPlayer` leaks a half-joined player | low | Real path, but never hit in runs and the test is deleted on this branch; guard adds complexity | reject |
| 6 | edge | `onMain(join)` timeout lets the bot join later | low | Same: deleted test, unlikely, guard adds complexity | reject |
| 7 | edge | `flushOutbound` throwing skips `finishAndReleaseAll` | low | Same: deleted test, unlikely | reject |
| 8 | edge | `removeListener` throwing in `finally` skips `leave` | low | Same: deleted test, unlikely | reject |
| 9 | edge | Silent skip of removal when the connection is null | low | Same: deleted test, cosmetic | reject |
| 10 | blind | Cancel test vacuous if the start slot is 3 | false | A fresh `ServerPlayer` inventory starts at slot 0, not 3 | reject |
| 11 | blind | Outbound injection not asserted | low | Anchor names come from bytecode and the diagnostic run; nothing downstream depends on an assertion | reject |
| 12 | blind | Flushing advice ignores registering with `ServerConnectionListener` | false | SPEC-bots Constraints already settle that the bot ticks itself because nothing ticks a connection outside `ServerConnectionListener` | reject |
| 13 | blind | Run results not recorded / committed status `done` | low | Fix edits this build's spec | reject |
| 14 | blind | Verification vs AC conflict on a failing spike | low | Fix edits this build's spec | reject |
| 15 | blind | Compiled against 5.4.0 API, ran on 5.5.0-SNAPSHOT | maybe-false | `test-bundle/` is meant to match production (AGENTS.md); SPEC-bots records the runtime version tested; would only be low | reject |
| 16 | blind | Next assumption (`PantsHandler`/`TrapWireHandler` sends) not cross-referenced | low | Outside the spike's intent (one assumption); flushing note already in SPEC-bots | reject |
| 17 | blind | `f2d03eb` message omits the spike spec file | low | Cosmetic; rewriting local history not worth it | reject |
| 18 | verification-gap | No gaps | — | Net diff is Markdown only | — |

## Verification

**Commands:**
- `gradlew.bat` -- expected: Spotless applied, shadow jar built.
- `gradlew.bat build` -- expected: green.
- `gradlew.bat integrationTest` -- expected: all ITs pass, or only the spike fails with a message that pins the failing hop (then record it as the finding).
