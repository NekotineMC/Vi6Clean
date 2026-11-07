# Epic 1 Context: Any Nekotine plugin can spawn, drive and remove a bot

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Give any Nekotine plugin a NekotineCore API to spawn a real fake player, drive it with client-equivalent controls through real serverbound packets, and remove it cleanly, with no NMS code in the host. The epic first restructures NekotineCore into a multi-module build (a version-agnostic `core`, an NMS-pinned `bots`, a tiny `bots-test-host`, and paper-junit-server moved in as a submodule) so bot code has a home without pinning every Core consumer to one Minecraft version. It then builds the bot body in layers: join/leave skeleton, per-bot tick event with ephemeral lifecycle and failure isolation, startup self-test, movement/aim, then hands/commands/respawn. It is the foundation for every later epic (Vi6 lobby fill, test hooks, unattended runs, navigation, melee). Work happens in the NekotineCore repository; each story ships as a NekotineCore PR to `dev` plus a Vi6Clean PR that bumps the gitlink.

## Stories

- Story 1.1: NekotineCore splits into `core` and `bots` modules, and Vi6 shades both
- Story 1.2: NekotineCore owns paper-junit-server and boots its own test host
- Story 1.3: A bot joins and leaves as a real player
- Story 1.4: Bots tick in a per-bot event, are ephemeral and fail in isolation
- Story 1.5: A startup self-test turns bots off loudly when NMS breaks
- Story 1.6: Bots walk, look, jump, sneak and sprint through real packets
- Story 1.7: Bots use their hands, send commands and respawn

## Requirements & Constraints

- **Real fake player:** a bot is in `getOnlinePlayers()`, `isOnline()` is true, `Bukkit.getPlayer(uuid)` resolves, `PlayerJoinEvent`/`PlayerQuitEvent` fire, and its UUID is the offline UUID derived from its name.
- **Real handlers only:** every input is a real serverbound packet written inbound into the fake connection's channel (so ProtocolLib inbound listeners see it) and handled by the real `handleX`. Never fabricate Bukkit events; never call game-mode, slot, sneak or position setters directly. Send controls on change, as a client does.
- **Control surface (only these groups):** lifecycle (spawn with name, location, wipe flags; remove; list; `Player` handle), movement (heading yaw, forward/back, strafe, jump, sneak, sprint), aim (yaw/pitch), hands (hotbar slot, use item, use item on block + face, left-click/start-break, attack entity, interact entity, swap hands), other (send command, respawn). Movement keys, sneak/sprint, look and slot persist until changed; hands actions and commands fire once in the next apply step. No setPosition, teleport, dialog, inventory-click or drop control.
- **Client-confirmation fidelity:** one teleport ack per tick for the latest position id only, sent on the next tick, never from inside `send()`. Player-loaded ack after join and after each respawn. A bot idle for 200 ticks must not be kicked.
- **Ephemeral:** the roster's `unload()` removes every bot; nothing restores bots. Wipe flags default on: "reset state" deletes `world/players/data/<uuid>.dat`, "reset statistics" deletes stats and advancements files; each can be turned off per bot at spawn; wipe runs after the quit save.
- **Self-test:** at host enable, a check of every NMS/reflective accessor; on failure, log loudly naming the accessor, disable the module, make every bot API call report "bots unavailable", keep host and server running. Needs an internal fault-injection seam for testing.
- **Failure isolation (library side):** an exception in library per-bot work is logged once (bot name + stack trace), controls go neutral, the bot stops ticking but stays connected and is listed as failed; other bots keep ticking. Exceptions from host listeners are not caught by the library.
- **Logging:** library lines start with the bot's name and carry no host/game concepts; one stack trace per failure.
- **Reuse proof:** `bots-test-host` (no NMS code of its own) spawns a bot, makes it right-click with an item seen by its `PlayerInteractEvent` listener, and removes it.
- **Migration gates (1.1):** bare `gradlew`, `gradlew build` and `gradlew integrationTest` green in Vi6Clean (`Vi6SmokeIT`, `Vi6MapIT`, `Vi6ConfigIT` pass); jar holds exactly one `fr.nekotine.core`; `verifyShadowJarLayout` passes and still forbids relocated Core copies; CommandAPI still relocated to `fr.nekotine.core.commandapi`; jar unchanged for humans on the dev server.
- **Repo policy:** Java only; Conventional Commits; every NekotineCore or paper-junit-server change ships with its gitlink commit in the same Vi6Clean PR; lockfiles updated only for what changed (`--update-locks <group>:*`, not full `--write-locks`) and the diff reviewed; Spotless on every NekotineCore module, failing on wildcard imports. Body is written from scratch: no code or dependency from fake-player plugins (any verbatim snippet keeps its MIT notice).
- **Sizing:** at most 6 bots per game.

## Technical Decisions

- **Modules:** `core` = all existing Core code, Paper API only, no paperweight, unpinned. `bots` = paperweight userdev on the same dev bundle as Vi6 (`26.2.build.65-beta`), depends on `:core`; `core` never depends on `bots`. One Minecraft version per release, no multi-version layer.
- **Shading:** `core` and `bots` publish plain jars and never run shadow. Only the final plugin shades exactly one copy of each and does its own relocation, so CommandAPI shading/relocation moves from Core to Vi6 (and to `bots-test-host`). Relies on Paper-plugin classloader isolation; no `join-classpath` between Nekotine hosts.
- **Consumption:** Vi6Clean keeps one NekotineCore submodule plus `includeBuild`, replacing `project(":NekotineCore")` with the `core`/`bots` coordinates. paper-junit-server becomes a NekotineCore submodule; Vi6Clean reaches it through the nested path in `pluginManagement` `includeBuild` and keeps no copy. Clones then need `git submodule update --init --recursive`; both `AGENTS.md` files and the Vi6Clean README must describe the new layout.
- **Packages:** public API `fr.nekotine.core.bot`; all NMS/reflection in `fr.nekotine.core.bot.internal` (about 25 NMS types in 2-3 files, minimal reflection). Public types expose only Bukkit, Paper and `core` types; NMS `BlockPos`/`Vec3` never leave internal; points cross boundaries as Bukkit `Location`.
- **Roster:** an `IPluginModule` in `bots`, resolved via `Ioc.resolve(...)`, resolved eagerly at host enable because Core's `ModuleManager` loads lazily and only logs constructor errors. Each host has its own roster. `bots` ships no commands (hosts own commands).
- **Body bring-up (verified by spike):** create `new EmbeddedChannel(false, false)` unregistered; `Connection.configureInMemoryPipeline(pipeline, SERVERBOUND)` and `connection.configurePacketHandler(pipeline)`; then `register()` so `channelActive` sets `Connection.channel` (ProtocolLib silently skips inactive channels). Before `placeNewPlayer`, replace `decoder` with `UnconfiguredPipelineHandler.Inbound` named `inbound_config` (otherwise ProtocolLib listeners never fire). Call `connection.setupOutboundProtocol(GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registryAccess)))`. Keep the raw `EmbeddedChannel` for `writeInbound`, since ProtocolLib swaps `Connection.channel` for a proxy. `flushOutbound()` every tick and before close, or close spams `StacklessClosedChannelException`. Join via `PlayerList.placeNewPlayer` with `UUIDUtil.createOfflinePlayerUUID(name)`.
- **Threading:** controls, tick event, roster changes, every `writeInbound` and flush on the main thread. `send()` may be called from any thread and only records state thread-safely (e.g. pending teleport id).
- **Tick order per bot (driven by Core `TickingModule` `TickElapsedEvent`, bots in spawn order):** reset the listener's awaited position and run the player tick; send pending acks; fire the per-bot Bukkit tick event; write changed inputs, hands actions and commands inbound; physics step along the movement heading; submit `Pos`/`PosRot` with current look via `handleMovePlayer` (fires `PlayerMoveEvent`); `flushOutbound()`. After the loop, apply spawns/removes requested during it. Controls set inside the per-bot event apply that tick; set elsewhere, next tick.
- **Movement:** position changes only via the server's own physics step along the movement heading (defaults to look yaw); heading/look separation is accepted (anti-cheat is a non-goal). Refused moves are diagnosed from `PlayerFailMoveEvent` with a log line starting with the bot name.
- **Single exit path** for every exit (roster removal, `unload()`, kick, server-side disconnect): flush, close connection, normal quit (`PlayerQuitEvent`), drop from roster, then apply wipe flags.
- **Test placement:** library tests run in NekotineCore's own integration suite on paper-junit-server through `bots-test-host`, with ProtocolLib loaded, never depending on Vi6. `bots-test-host` is a Paper plugin (`paper-plugin.yml`) shading `core` and `bots`, with its own test-server bundle folder gitignored except a README (ProtocolLib, `spawn-protection=0`), modelled on Vi6's `test-bundle/`.

## Cross-Story Dependencies

- 1.1 (module split, shading move) gates everything; 1.2 (harness move, `bots-test-host`, NekotineCore integration suite) gates all library tests in 1.3-1.7.
- 1.3 builds the body, roster, acks and flush that 1.4-1.7 extend; 1.4's per-bot tick event and same-tick apply semantics are what 1.6 and 1.7 controls rely on.
- 1.5's self-test must cover every accessor added in 1.3-1.7; extend it as new NMS accessors appear.
- Later epics depend on this API: Epic 2 (Vi6 shades `bots`, eager roster resolve, brain-side failure guarding), Epic 3 (bots as test drivers, teleport acks for Portable Teleporter), Epic 5 (control port and bot adapter mapping 1:1 onto these controls, with a per-group priority order where the melee aim write overrides the walker's). Keep the API host-agnostic.
- Deferred, not in this epic: Paper 26.3 upgrade, `ClientTrackModule` checks against bots, inventory-click/drop and dialog controls, Maven publishing, adopter-guide rewrite.
