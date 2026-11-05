---
reviewer: adversary
lens: 'Attack the spine: two units one level down that each obey every AD to the letter yet build incompatibly'
target: ../ARCHITECTURE-SPINE.md (status draft, updated 2026-11-05)
date: 2026-11-05
---

# Adversarial review: ARCHITECTURE-SPINE (NekotineBots + Vi6 bot layer)

## Verdict

**Not build-safe yet.** The layering, the dependency direction and the packaging rules (AD-1 to AD-3) are tight. The spine is loose wherever two units share **runtime state**: the control port, held keys, a `PathResult`, a bot's lifecycle, a bot's team, and a dialog shown to a bot. I found 12 pairs of units that each obey every AD and still build incompatible code. 6 need a new or tightened AD before the first walker or brain story. The other 6 can be closed with a sentence in an existing AD or in Consistency Conventions.

Units used: **Body** (`bot.internal`), **Bots API** (controls + per-bot tick event), **Roster**, **Adapter** (bot control adapter), **Pathfinders** (core impls), **Walkers**, **Melee** (core combat), **Brain** (Vi6 behaviour), **Cmd** (Vi6 commands), **Hooks** (Vi6 test hooks), **TestHost** (`bots-test-host`).

Fact checked in the code: Core's ticking module fires `TickElapsedEvent` (`NekotineCore/src/main/java/fr/nekotine/core/ticking/TickingModule.java`), not `TickEvent` as the spine's convention table and sequence diagram call it. Fix the name. Hole H3 depends on that event.

## Holes, ranked

### H1. Two strategies write one control port in one tick (Walker x Melee) — new AD, critical

- **Walker complies:** AD-8 says it drives its body only through the port. On the brain's call it writes look, forward and sprint every tick.
- **Melee complies:** under the same AD it writes look, strafe, attack and slot on the same port in the same tick event.
- **Clash:** AD-5's "later `EventPriority` wins; inputs never merge" is about two **listeners**. It says nothing about two strategies called by one listener, or by two listeners at the **same** priority, where Bukkit's order is registration order. Each control is last-writer-wins on its own, so the bot gets the walker's forward with the melee's look and strafe. It runs sideways off the path, and that frame was never designed. Both strategies also write `slot`. Melee switches to the sword and never switches back, while the brain still thinks the tool is in hand (CAP-3).
- **Fix (tighten AD-8):** *Rule: a body's control port has at most one driving strategy per tick, granted by the host through an explicit `acquire(port)` or `drive(port)` handoff. The previous holder is released to neutral, and a strategy's writes outside its grant fail loudly. Every frame comes from exactly one strategy.*

### H2. Latched held keys vs per-tick inputs (Bots API x Walker/Melee/Brain) — new AD, critical

- **Walker A complies:** it reads AD-4's "held movement keys" as latched. It sets forward when a path starts and clears it when the path ends.
- **Melee B complies:** it reads AD-5's "controls set during the event apply that same tick" as a per-tick frame. It sets only what it wants this tick and assumes everything else is neutral.
- **Clash:** if the keys latch, B inherits A's forward and sprint, and the bot sprints into its target. If they reset, A moves one tick and stops. Hand actions get it worst. A latched `attack`, `use item` or dialog custom-click fires 20 times a second, which breaks the attack cooldown (CAP-8) and repeats `PlayerInteractEvent` (CAP-3).
- **Fix (new AD on input semantics):** *Rule: held keys, look and slot latch until written again. Hand and UI actions are one-shot: each queues exactly one packet for the next apply step. The port offers `neutral()`, which clears every held key.*

### H3. Strategy ticked from `TickElapsedEvent` vs the per-bot tick event (Walker x Roster) — tighten AD-5

- **Walker complies:** the Ticking convention says per-tick work "hangs off Core's `TickingModule`". So the walker subscribes to `TickElapsedEvent` itself and writes the port there. Core's AD-8 code cannot see the bots tick event (core never depends on bots).
- **Roster complies:** it also listens to `TickElapsedEvent`, and it applies controls after its per-bot event.
- **Clash:** AD-5 says controls "set anywhere else apply on the bot's next tick". But the walker's writes land this tick or next depending on whether its listener was registered before or after the roster's. That is the scheduling-order dependence AD-5 exists to prevent, and the order differs between Vi6 and TestHost.
- **Fix (tighten AD-5/AD-8):** *Rule: core strategies have no clock of their own. They expose `tick(port)`, and only the host calls it, from the per-bot tick event for bots and from the host's mob tick for mobs. A strategy never subscribes to `TickElapsedEvent`.*

### H4. A bot removed or spawned during its own tick event (Roster x Brain/Cmd) — tighten AD-10

- **Brain complies:** AD-10 and AD-14 say lifecycle goes through the roster, so the brain or a hook calls `roster.remove(bot)` from inside the bot's tick event (a died-in-test cleanup, say).
- **Roster complies:** it fires the quit path synchronously (AD-10), then returns to its per-bot loop and applies inputs, steps physics, writes inbound and flushes on a closed channel.
- **Clash:** you get a `ConcurrentModificationException` on the roster iteration, or `writeInbound` on a closed `EmbeddedChannel`. AD-11 then logs a **failure** for a bot that was removed deliberately, and the CAP-5 "no exception in the log" check fails. A spawn during the loop has the same problem, and the new bot may or may not tick this tick.
- **Fix:** *Rule: a spawn or remove requested while the roster is iterating takes effect at the end of that roster pass. A bot marked removed skips its remaining tick steps. A removed bot is never reported as failed.*

### H5. Two owners of a bot's connection: roster vs the server (Roster x Body x Vi6 quit) — tighten AD-10

- **Roster complies:** it is "the only owner of bot lifecycles" (AD-10), and it tracks live bots in its own list.
- **Body complies:** per the spec, an unmatched teleport ack disconnects the bot. Vi6 code, an operator `/kick`, or an exception in `handleX` can disconnect it the same way. That runs the normal quit path, and `Vi6PhaseGlobal.onPlayerLeft` calls `Vi6Game.removePlayer`.
- **Clash:** the server removed the player, but the roster still lists it as live. `list` shows a ghost, the next tick writes into a dead channel (AD-11 noise), and CAP-5's "clean roster" breaks. The spine never says whether the roster follows the server or the other way round. A second clash on host disable: `unload()` fires `PlayerQuitEvent` for every bot. If Vi6's game modules have already unloaded, `Ioc.resolve(Vi6Game.class)` in `onPlayerLeft` throws.
- **Fix:** *Rule: the roster drops a bot whenever its connection closes, whoever closed it, and fires one `BotRemovedEvent` after `PlayerQuitEvent`. The roster's module unloads before any host module that listens to `PlayerQuitEvent`.*

### H6. Who owns a bot's team (Cmd x Brain x Hooks) — tighten AD-12/AD-14

- **Cmd complies:** AD-14 says "add N bots **for a team**". AD-12 constrains only "bot behaviour", so the command calls `game.addPlayerInThiefs(bot)`.
- **Brain complies:** AD-12 says it picks a team "through the same lobby path a human uses". It answers the dialog with its own choice, and the `customClick` lambda (`LobbyPhasePlayerWrapper.java:112-122`) calls `addPlayerInGuards`.
- **Hooks comply:** AD-13 explicitly lists "forcing teams" as a Vi6 test hook.
- **Clash:** three writers of one field. The command sets thief, the brain clicks guard, and the last write wins in an order that depends on timing. CAP-2's "teams balanced through the normal path" is never actually tested, because the command or the hook set the team.
- **Fix:** *Rule: a bot's requested team is an intent handed to its brain at spawn. Only the brain makes it real, through the lobby dialog. Commands never call team mutators, and test hooks that force teams are banned from the CAP-5 unattended-run path.*

### H7. A dialog shown to a bot has no read side (Body x Brain) — new AD

- **Bots API complies:** AD-4 offers the control "dialog custom-click", which writes a real serverbound custom-click packet.
- **Brain complies:** AD-12 says it reads only Vi6's public API. That API exposes `displayMenu()`, not the dialog's action key or its expected payload (`team`, `ready`).
- **Clash:** to click, the brain needs the action id and payload shape that exist only in the **clientbound** show-dialog packet the body received. One implementer adds an NMS-shaped "last dialog" getter to the body, which breaks AD-3. Another hard-codes Vi6's key strings in the brain, which then fails because the `ClickCallback` key is a random UUID. AD-4 defines what a client sends but says nothing about what a client receives.
- **Fix (new AD, observation surface):** *Rule: the bots API exposes, in Bukkit and Adventure types, the clientbound state a client would act on: the currently shown dialog and its action keys, the death or respawn screen, and the last server-forced position. The custom-click control takes an action key plus a payload map. No other clientbound state is read.*

### H8. `PathResult` cursor and mutable `Location`s (Pathfinder x Walker, Walker x Walker) — tighten AD-6

- **Pathfinder complies:** it returns its own `PathResult` (AD-6) and advances `getNextPointIndex()` as the body moves, the way vanilla's mob path does.
- **Walker complies:** "accepts any `PathResult`" (AD-6), keeps its own cursor and calls `point.add(dir)` while computing look-ahead.
- **Clash:** Paper's `PathResult` has no setter for the next index, so it is unclear who owns the cursor. A vanilla mob's live `PathResult` is advanced by the mob's own navigation, so a walker following it moves along with a second driver. `Location` is mutable, so one walker's `add()` corrupts the path shared with a second bot or with a debug renderer.
- **Fix:** *Rule: a `PathResult` handed across the port is an immutable snapshot whose `getPoints()` returns fresh copies. The walker owns the only cursor and starts it at `getNextPointIndex()` when it accepts the path.*

### H9. Async search vs caller rules and bot lifetime (Pathfinder x Brain) — tighten AD-7/AD-9

- **Pathfinder complies:** under AD-7 it searches off the main thread and calls the caller's passability rule from there.
- **Brain complies:** it supplies a passability rule that reads Vi6's public API (entrance and exit state) and the world (AD-12).
- **Clash:** the Vi6 API and world reads now run off-thread, which AD-9 forbids for bot work but doesn't forbid for a **caller rule**. The result also arrives on the main thread after the bot was removed, or after the brain asked for a newer path, and the walker starts a stale path.
- **Fix:** *Rule: a passability rule is a pure function over a main-thread snapshot the pathfinder takes before going async. Every search returns a cancellable handle, and results of cancelled or superseded searches are never delivered.*

### H10. What "disabled" leaves running (Roster/Body x Brain/Vi6 game) — tighten AD-11

- **Roster complies:** AD-11 says a disabled bot "stops ticking, stays connected". So it stops everything: tick event, acks, physics and flush.
- **Brain/Vi6 comply:** they never check bot status (AD-12 forbids `isBot` checks in game logic).
- **Clash:** the disabled bot's held keys stay latched (H2) while physics stops, so it floats in place. Teleports Vi6 sends at phase changes are never acked, outbound writes pile up unflushed, and the lobby waits forever on a bot that never clicks "ready". The unattended run hangs silently, which is exactly what AD-11 exists to prevent. Also, a **host** listener that throws in every tick event gets 20 stack traces a second from Paper, because AD-11 leaves those exceptions to the host.
- **Fix:** *Rule: disabling a bot releases all controls to neutral and stops the tick event, keeps acks, physics and flush running so the body stays a valid idle client, and fires `BotFailedEvent` so the host can remove it. The bots module counts host-listener exceptions per bot and disables the bot after the first one.*

### H11. What the port reads (Adapter x Walker, bot adapter x future mob adapter) — tighten AD-8

- **Bot adapter complies:** it reads `location` and `velocity` from the Bukkit `Player`. For a player, the server-side velocity is not the body's own physics delta and is often zero.
- **Walker complies:** it is tuned against a mob adapter whose velocity is real server physics.
- **Clash:** the same walker overshoots on bots and is fine on mobs, or the other way round. Neither adapter breaks AD-8, whose read set gives names but no meaning.
- **Fix:** *Rule: port reads are the body's own simulated state as of the start of this tick's apply step, after acks and before inputs. Adapters document that frame and never return Bukkit-cached values.*

### H12. Body key state vs server-forced state (Body x Vi6 listeners) — Consistency Convention

- **Body complies:** sprint is a held key (AD-4), applied each tick.
- **Vi6 complies:** `Vi6PhaseGlobal.onPlayerToggleSneak` calls `setSprinting(false)` on the server.
- **Clash:** if the body re-sends START_SPRINTING every tick while held, Vi6's forced stop is undone at once. A real client sends START and STOP only on key changes. Tests pass with bots and fail with humans, or the other way round. The same applies to slot changes forced by Vi6.
- **Fix:** *Rule: the body sends serverbound state packets only when a key changes, like a client does, and treats server-forced state as truth until the key is pressed again.*

## Minor (fold into existing text)

- **Roster per host vs Vi6 seeing foreign bots:** with two hosts, Vi6's brains ignore the other host's bots (correct), but Vi6 game logic treats them as humans who never click the dialog. Say in AD-10 that a host must not count another host's bots toward its readiness. That is a host concern, so a note is enough.
- **Respawn ownership:** AD-4 has a "respawn" control, and the spec makes the body send player-loaded after each respawn. State that the body never auto-respawns and the brain presses respawn, or the reverse.
- **Reset ownership:** both the CAP-5 run command and Vi6's reset hook "clear the roster". Name one of them, the hook, and have the command call it.
- **Event name:** `TickEvent` in the conventions and diagram should be `TickElapsedEvent`.

## Proposed AD changes, summary

| Hole | Change | Rule (short) |
| --- | --- | --- |
| H1 | tighten AD-8 | One driving strategy per port per tick via an explicit handoff; the previous holder is neutralised |
| H2 | new AD | Held keys, look and slot latch; hand and UI actions are one-shot; the port has `neutral()` |
| H3 | tighten AD-5/AD-8 | Strategies have no clock; the host calls `tick(port)` from the per-bot tick event |
| H4 | tighten AD-10 | Spawn and remove during the roster pass are deferred to its end; removed is never failed |
| H5 | tighten AD-10 | The roster follows the connection: any close drops the bot plus `BotRemovedEvent`; the roster unloads first |
| H6 | tighten AD-12/AD-14 | The team is a brain intent, realised only via the dialog; no command or run-path hook sets teams |
| H7 | new AD | The bots API exposes client-visible clientbound state (dialog keys, death screen, forced position) in Bukkit types |
| H8 | tighten AD-6 | `PathResult` is an immutable snapshot; the walker owns the only cursor |
| H9 | tighten AD-7 | Passability runs on a main-thread snapshot; searches are cancellable and stale results are dropped |
| H10 | tighten AD-11 | Disabling neutralises controls and keeps acks, physics and flush, fires `BotFailedEvent`, and caps host-listener spam |
| H11 | tighten AD-8 | Port reads are the body's simulated state at the start of the apply step |
| H12 | convention | The body sends state packets only on key changes; server-forced state is truth |
