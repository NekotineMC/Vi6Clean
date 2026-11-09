---
title: 'Story 1.3: A bot joins and leaves as a real player'
type: 'feature'
created: '2026-11-10'
status: 'in-progress'
route: 'dispatch'
baseline_commit: 'bd9459672f6cded93ea0612f6a09ec5c399f3248'
baseline_commit_nekotinecore: '824410fb64a3e129f1efbef7cd39bb7a8e3056b9'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/NekotineCore/AGENTS.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `bots` is an empty placeholder, so no Nekotine plugin can put a player-shaped stand-in on the server that the game and ProtocolLib treat as a real player.

**Approach:** Build the bot body (fake `Connection` on an `EmbeddedChannel`, joined through `PlayerList.placeNewPlayer` using the spike's recipe) and a roster `IPluginModule` that spawns, lists, ticks (keep-alive and client acks, flush) and removes bots. Prove it in NekotineCore's integration suite through `bots-test-host`.

## Boundaries & Constraints

**Always:**
- Public API in `fr.nekotine.core.bot` exposes only Bukkit, Paper and `core` types; all NMS sits in `fr.nekotine.core.bot.internal`, at most 2-3 files (AD-3). No code or dependency from fake-player plugins.
- Every packet the bot sends is written with the raw `EmbeddedChannel.writeInbound` on the main thread; the `send()` override only records state in thread-safe fields (AD-9).
- Per tick, per bot (spawn order): `connection.tick()` (runs the listener tick: `resetPosition`, player tick, keep-alive check, throttlers) → acks → `flushOutbound()`.
- Removal: flush, `connection.disconnect(...)` then `handleDisconnection()` (the server's own quit path, which saves and fires `PlayerQuitEvent`), then drop from the roster.
- Branches: NekotineCore from `origin/dev` with a PR to `dev`; Vi6Clean from `main` with a PR to `main` that commits the NekotineCore gitlink. Lockfiles: only `--update-locks <group>:*` for what changed, diff reviewed.
- **Decision (agent, 2026-11-10): the bot answers keep-alives.** `listener.tick()` runs Paper's keep-alive, which kicks a silent client after about 30 s. `send()` records the latest `ClientboundKeepAlivePacket` id; the next tick writes `ServerboundKeepAlivePacket` with it.
- **Decision (agent, 2026-11-10): spawn ignores saved player data.** The `ServerPlayer` is created on the target `ServerLevel` and placed at the location before `placeNewPlayer`. Loading a kept state belongs to 1.4's wipe flags.
- **Decision (agent, 2026-11-10): `unload()` removes every bot through the removal path**, so bots never outlive the host; 1.4 adds the formal exit-path and wipe tests.

**Never:**
- No per-bot tick event, controls, physics, move packets, wipe flags, failure isolation or self-test (Stories 1.4-1.7).
- No fabricated Bukkit events; no teleport, position, game-mode or slot setters after join.
- No commands in `bots` (AD-14). No `join-classpath` between Nekotine hosts. No Paper or dev-bundle upgrade; no full `--write-locks`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Spawn | `spawn("Bot_Alice", loc)` on main thread | `PlayerJoinEvent` fires; bot online at `loc`; UUID = `UUIDUtil.createOfflinePlayerUUID("Bot_Alice")` | N/A |
| Duplicate name | A player or bot named `Bot_Alice` is online | No second join | `IllegalArgumentException` naming the bot |
| Off-thread call | `spawn`/`remove` off the main thread | Nothing changes | `IllegalStateException` |
| Teleport acks | Several position packets before one tick | One `ServerboundAcceptTeleportationPacket` next tick, latest id only | N/A |
| Remove | `remove(bot)` | Flush, close, `PlayerQuitEvent`, gone from roster and `getOnlinePlayers()`, no `StacklessClosedChannelException` logged | Removing an unknown or removed bot is a no-op |

</frozen-after-approval>

## Code Map

- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/NekotineBots.java` -- placeholder; delete it and replace its entry in `bots-test-host`'s `verifyShadowJarLayout` required list with the roster class.
- `NekotineCore/core/.../module/IPluginModule.java`, `ModuleManager.java` -- modules are constructed lazily by `Ioc.resolve`, constructor errors only logged; `NekotinePlugin.setupModules()` scans all of `fr.nekotine.core`, so the roster auto-registers.
- `NekotineCore/core/.../ticking/TickingModule.java`, `event/TickElapsedEvent.java` -- fires every tick; the roster constructor must `Ioc.resolve(TickingModule.class)` so ticking runs. Listener pattern: `BowChargeModule` (`implements IPluginModule, Listener`, `EventUtil.register(this)` / `unregister` in `unload()`).
- NMS (26.2, mojang-mapped, verified with `javap`): `new Connection(PacketFlow.SERVERBOUND)`, override `send(Packet, ChannelFutureListener, boolean)`; `Connection.configureInMemoryPipeline`, `configurePacketHandler`, `setupOutboundProtocol`, `tick()`, `disconnect(Component)`, `handleDisconnection()`; `UnconfiguredPipelineHandler.Inbound`; `GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registryAccess))`; `new ServerPlayer(server, level, UUIDUtil.createOfflineProfile(name), ClientInformation.createDefault())`, `setServerLevel`, `snapTo`; `CommonListenerCookie.createInitial(profile, false)`; `PlayerList.placeNewPlayer`; `ClientboundPlayerPositionPacket.id()`, `ServerboundAcceptTeleportationPacket(int)`, `ServerboundPlayerLoadedPacket()`, `Clientbound/ServerboundKeepAlivePacket(long)`.
- `_bmad-output/specs/spec-bots/SPEC.md` lines 124-132 -- the verified pipeline recipe (decoder swap to `inbound_config`, raw channel kept, flush before close).
- `NekotineCore/bots-test-host/src/integrationTest/.../BotsTestHostSmokeIT.java` -- test pattern (`PaperServer.onMain`, `awaitTicks`, `Ioc.resolve`).
- `NekotineCore/bots-test-host/build.gradle.kts` -- needs `compileOnly(libs.protocollib)` for the test `PacketAdapter` (descriptor already has `join-classpath: true` on ProtocolLib).

## Tasks & Acceptance

**Execution:**
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/BotRoster.java` -- `IPluginModule, Listener`: `Bot spawn(String, Location)`, `void remove(Bot)`, `List<Bot> list()` (spawn order), ticks bots on `TickElapsedEvent`, `unload()` removes all -- the host's only entry point.
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/Bot.java`, `BotState.java` -- `name()`, `player()`, `state()`; `BotState` holds `ACTIVE` now (1.4 adds failed) -- the listing contract.
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/` (`BotConnection.java`, `BotBody.java`) -- channel and pipeline recipe, `send()` recording (teleport id, keep-alive id), join, per-tick work, acks (player-loaded once after join), removal.
- [x] `NekotineCore/bots-test-host/build.gradle.kts`, `gradle.lockfile` -- ProtocolLib compileOnly, layout check update; `--update-locks net.dmulloy2:*`.
- [x] `NekotineCore/bots-test-host/src/integrationTest/java/fr/nekotine/botstesthost/it/BotLifecycleIT.java` -- cover every matrix row and the ACs below, observing sent packets with ProtocolLib receiving adapters (the teleport row triggers two teleports in one main-thread task); each test removes its bots and listeners in `finally`.
- [x] `NekotineCore/README.md` -- add the roster to the module inventory.
- [x] Vi6 gitlink -- commit the new NekotineCore pointer in the Vi6Clean PR.

**Acceptance Criteria:**
- Given a spawned bot, then `isOnline()`, `getOnlinePlayers()` contains it, `Bukkit.getPlayer(uuid)` returns its handle, and `list()` returns its name, handle and `ACTIVE`.
- Given ProtocolLib loaded and a receiving `PacketAdapter` on `HELD_ITEM_SLOT`, when the test writes `ServerboundSetCarriedItemPacket` through the bot's channel, then the adapter fires once with `event.getPlayer()` matching the bot's UUID.
- Given a bot left alone for 700 ticks (past the keep-alive limit), then it is still connected and listed; and within 5 ticks of spawn `hasClientLoaded()` is true (the player-loaded ack, not the server timeout).
- Given the public `fr.nekotine.core.bot` types, then no signature mentions `net.minecraft` or `com.mojang` (checked by reflection in the IT).
- Given Vi6Clean with the new gitlink, when bare `gradlew` (with `-PVi6CleanJarOutputPath=build/dist`), `gradlew build` and `gradlew integrationTest` run, then all pass.

## Design Notes

The bot calls `connection.tick()` rather than `resetPosition` + `doTick` by hand: the listener tick also decays the chat and command spam throttlers, which 1.7's commands need. Run inside the bot's tick loop, `Connection.tick()`'s own flush goes through ProtocolLib's proxy channel, so the explicit `flushOutbound()` on the raw channel stays.

## Verification

**Commands:**
- `gradlew.bat build` then `gradlew.bat integrationTest` (in `NekotineCore/`) -- expected: BUILD SUCCESSFUL, `BotLifecycleIT` and `BotsTestHostSmokeIT` pass
- `gradlew.bat -PVi6CleanJarOutputPath=build/dist`, `gradlew.bat build`, `gradlew.bat integrationTest` (Vi6Clean) -- expected: all green

**Manual checks:**
- Lockfile diffs touch only `bots-test-host/gradle.lockfile` (ProtocolLib on compile classpaths).
- IT server log after the run: no `StacklessClosedChannelException`, no keep-alive kick.

## Implementation Notes

- Keep-alives: `send()` queues every `ClientboundKeepAlivePacket` id (thread-safe queue) and the next ack step answers them all, oldest first, rather than only the latest. Paper's `KeepAlive` keeps a queue of pending challenges and disconnects a client that answers out of order, so answering only the latest would kick the bot if two were ever pending; Paper sends one per second, so in practice there is one per tick, as decided.
- Ack timing: per-tick order is `connection.tick()` → acks → flush, so a teleport or keep-alive sent inside the listener tick is answered in the same bot tick (still never from `send()`); one sent between bot ticks (e.g. a plugin teleport) is answered on the next tick.
- `connection.address` is set to a loopback `InetSocketAddress` after `register()`: `Connection.handleDisconnection()` casts the address to `InetSocketAddress` for `PlayerConnectionCloseEvent`, and the embedded channel's own address is not one.
- Every flush is followed by `EmbeddedChannel.releaseOutbound()`: nothing reads what the server sends, and the encoded buffers would otherwise pile up in the channel's outbound queue.
- A bot whose connection closed on its own (a kick) is dropped after its tick through the same removal call (idempotent `handleDisconnection`), so it is not left listed; 1.4 formalises exit paths.
- `BotBody.writeInbound(Packet)` is public (internal package): the IT writes `ServerboundSetCarriedItemPacket` through it; 1.6/1.7 controls build on it.
- `spawn` also refuses names outside `[A-Za-z0-9_]{1,16}` and locations without a world (`IllegalArgumentException`); a failed join is wrapped in `IllegalStateException`.
- The teleport row reads the expected latest id from `ServerGamePacketListenerImpl.awaitingTeleport` by reflection (before and after the two teleports) and observes the acks with a ProtocolLib receiving adapter on `TELEPORT_ACCEPT`; it does not intercept the outbound position packets. The IT (test code only, not the host plugin) uses NMS types for that, for `hasClientLoaded()` and for the `ServerboundSetCarriedItemPacket`.
- Vi6Clean's own `verifyShadowJarLayout` also required `fr/nekotine/core/bot/NekotineBots.class`; it now requires `BotRoster.class`.
- The "no `StacklessClosedChannelException` logged" row is checked in `BotLifecycleIT` with a log4j appender on the server's root logger (a probe line proves it is attached); the IT server log after the run had no closed-channel error and no keep-alive kick.
- Verified: NekotineCore `gradlew build` and `gradlew integrationTest` (8 `BotLifecycleIT` + 3 `BotsTestHostSmokeIT` passed); Vi6Clean `gradlew -PVi6CleanJarOutputPath=build/dist`, `gradlew build`, `gradlew integrationTest` green. Lockfile diff: one line, `net.dmulloy2:ProtocolLib:5.5.0-SNAPSHOT=compileClasspath` in `bots-test-host/gradle.lockfile`.

## Spec Change Log

## Review Triage Log
