---
title: 'Story 1.4: Bots tick in a per-bot event, are ephemeral and fail in isolation'
type: 'feature'
created: '2026-11-10'
status: 'done'
route: 'dispatch'
baseline_commit: 'a933c71b873331df696fe41bf80d1a7a68f60ab6'
baseline_commit_nekotinecore: '5e9669c85e8e71e1610ed50fad3d9da7ca660136'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
  - '{project-root}/NekotineCore/AGENTS.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The roster ticks bots but gives hosts no per-bot moment to act, lets a spawn or remove mutate the roster mid-loop, keeps bot data files after removal, and lets one bot's exception stop every other bot's tick.

**Approach:** Fire a public `BotTickEvent` per live bot inside the roster's tick loop, defer roster changes made during the loop to its end, route every exit through one path that ends with the wipe flags, and guard each bot's library work so a failure marks only that bot `FAILED`.

## Boundaries & Constraints

**Always:**
- Per live bot, spawn order: `connection.tick()` → acks → `BotTickEvent` → flush; then the "lost connection" check. Inputs and physics (1.6) slot in between the event and the flush.
- The event is synchronous, main thread, `getBot()` returns `Bot`. The library does not wrap `callEvent`: host listener exceptions stay Paper's (it logs them) and never fail the bot.
- Library work before and after the event (connection tick, acks, flush) runs in a guard. On a throw: one `logger.error("<name> failed, it stops ticking", e)`, state `FAILED`, no more connection tick, acks or event for that bot; other bots carry on. Controls go neutral once 1.6 adds them.
- A `FAILED` bot stays connected and listed; each tick it only flushes and releases its outbound buffer (silently guarded), and leaves through the exit path if its connection closed (so a kick still completes). `remove` and `unload` work on it.
- Single exit path (remove, `unload()`, kick, server-side disconnect): flush → `connection.disconnect` → `handleDisconnection()` (quit event, synchronous save) → drop from roster → wipe. Removing a bot during the loop marks it; it is not ticked again and leaves at the loop's end.
- Wipe: public record `BotWipe(boolean state, boolean statistics)`, `BotWipe.ALL` default for `spawn(name, location)`, new `spawn(name, location, BotWipe)`. `state` deletes `players/data/<uuid>.dat` and `<uuid>.dat_old`; `statistics` deletes `players/stats/<uuid>.json` and `players/advancements/<uuid>.json`, all under `server.getWorldPath(LevelResource.…)`. A failed delete logs one warning starting with the bot name.
- Library log lines start with the bot's name and carry no host concepts (NFR9).
- **Decision (user, 2026-11-10): a spawn during the tick loop joins at once** (`PlayerJoinEvent` fires, `spawn` returns a live `Bot`), but the bot enters `list()` and the tick order only after the loop ends.
- Branches: NekotineCore from `origin/bot`, PR to `bot`; Vi6Clean from local `bot`, PR to `bot`, committing the NekotineCore gitlink.

**Never:**
- No controls, physics, move packets (1.6), hands, commands, respawn (1.7) or self-test (1.5).
- No catch around host listeners; no `BotFailedEvent`, no restoring bots across boots.
- No Paper or dev-bundle upgrade; no full `--write-locks`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Tick event | Bots A, B live | Each tick: event for A, then B, on main thread, after A's teleport ack is written | N/A |
| Remove in loop | A's listener removes B | B gets no event this tick; quit fires after the loop; listed until then | N/A |
| Spawn in loop | A's listener spawns C | C joins at once but gets no event this tick and is listed only after the loop | Duplicate name of a pending spawn refused |
| Default wipe | Bot removed | Its `.dat`, stats and advancements files are gone | Missing file: nothing logged |
| Keep flags | `new BotWipe(false, false)` | All files kept | N/A |
| Library throw | Fault injected in A's tick | One error line starting `Bot_A`, A `FAILED`, still online after 700 ticks; B keeps getting events | Logged once only |
| Host throw | Listener throws for A | A stays `ACTIVE`, no library failure line | Paper logs it |
| Unload | Separate roster with 2 bots, `unload()` | Both quit, unlisted, offline, files wiped | One remove failing does not stop the others |

</frozen-after-approval>

## Code Map

- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/BotRoster.java` -- `onTick` loop, `removeBody`, `unload`; add the loop flag, deferred removals, the guard and the event. Keep the name and duplicate checks; also count pending spawns.
- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotBody.java` -- split `tick()` into `tickBeforeEvent()` (connection tick + acks) and `tickAfterEvent()` (flush); add the failed housekeeping tick, the wipe after `handleDisconnection()`, and a test seam `failNextTick(RuntimeException)` (internal, documented as a test seam). `state()` reads a field.
- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/BotState.java` -- add `FAILED`.
- NMS for the wipe (26.2, verified): `MinecraftServer.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR | PLAYER_STATS_DIR | PLAYER_ADVANCEMENTS_DIR)`; `PlayerList.remove` fires the quit event then saves synchronously, so delete after `handleDisconnection()` returns, never in a quit listener. A kick only closes the channel after a flush; nothing but `connection.tick()` or our own call runs `handleDisconnection`. At shutdown plugins disable before players are removed, so `unload()` covers it.
- `NekotineCore/bots-test-host/src/integrationTest/java/fr/nekotine/botstesthost/it/BotLifecycleIT.java` -- reuse `LogCapture`, `PlayerEvents`, `cleanUp`, `register`; `publicApiExposesNoNms` scans only the top-level `fr.nekotine.core.bot` package, so new public types go there.

## Tasks & Acceptance

**Execution:**
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/BotTickEvent.java`, `BotWipe.java`, `BotState.java` -- public API.
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/BotRoster.java` -- loop with event, guard, deferred changes, `spawn` overload; class Javadoc describes the tick order and the failure rule.
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotBody.java` -- split tick, failed housekeeping, wipe, fault seam.
- [x] `NekotineCore/bots-test-host/src/integrationTest/java/fr/nekotine/botstesthost/it/BotTickIT.java` (new) -- every matrix row; each test removes its bots and listeners in `finally`.
- [x] `NekotineCore/README.md` -- one line on the tick event, wipe flags and failure state.
- [x] Vi6 gitlink -- commit the new NekotineCore pointer in the Vi6Clean PR.

**Acceptance Criteria:**
- Given a bot teleported between ticks, when its next `BotTickEvent` fires, then its `ServerboundAcceptTeleportationPacket` has already been received (ProtocolLib adapter order).
- Given every library log line captured during `BotTickIT`, then each line from the `NekotineCore` logger about a bot starts with that bot's name.
- Given NekotineCore and Vi6Clean on the new pointer, when `gradlew build` and `gradlew integrationTest` run in both (Vi6Clean also bare `gradlew -PVi6CleanJarOutputPath=build/dist`), then all pass and `BotLifecycleIT` stays green.

## Implementation Notes

- Commits: NekotineCore `74d79ea` on `feat/bots-1-4-tick-event` (from `origin/bot`); Vi6Clean `a23afbf` (gitlink only) on `feat/bots-1-4-tick-event` (from local `bot`).
- `BotLifecycleIT` helpers (`roster`, `spawnLocation`, `cleanUp`, `register`, `LogCapture`) became package-private for reuse; `LogCapture` also records the logger name for the log-prefix check.
- The guard catches `RuntimeException`; an `Error` (e.g. `LinkageError` from broken NMS) still propagates and is left to Story 1.5's self-test.
- If `handleDisconnection()` throws during an exit, the bot is dropped but not wiped (the error is logged with its name).
- An active kicked bot quits inside its own `connection.tick()` (as in 1.3); the exit path then drops and wipes it.
- "One remove failing does not stop the others" in `unload()` is handled (per-bot catch) but untested: no fault seam for removal. The unload test removes a `FAILED` bot alongside an active one instead.
- Verified: NekotineCore `gradlew build` + `integrationTest` (8 `BotTickIT`, 10 `BotLifecycleIT`, 3 `BotsTestHostSmokeIT` passed); Vi6Clean bare `gradlew -PVi6CleanJarOutputPath=build/dist`, `build`, `integrationTest` green. No lockfile changes.
- Review pass 1 patches: NekotineCore `ab54ef5` (guard catches `LinkageError`; `remove()` quits despite a broken flush; wipe warning keeps the stack trace; new `removeOfBotSpawnedInSameLoopLeavesAfterIt`, `kickedFailedBotLeaves`; `.dat_old` and post-kick wipe asserted). Vi6Clean gitlink commit amended to `16bca7c`. Re-verified: NekotineCore `build` + `integrationTest` (10 `BotTickIT`, 10 `BotLifecycleIT`, 3 smoke) and Vi6Clean bare `gradlew`, `build`, `integrationTest` (`Vi6ConfigIT`, `Vi6MapIT`, `Vi6SmokeIT`) green.

## Spec Change Log

## Review Triage Log

Review pass 1 (2026-11-10). Layers: Blind Hunter (B), Edge Case Hunter (E), Verification Gap (G).

| # | Finding | Verdict | Evidence | Route |
|---|---------|---------|----------|-------|
| E1 / E7 / B1 | `guard` catches only `RuntimeException`; a `LinkageError` from NMS escapes `onTick`, never marks FAILED and stops later bots | medium | `catch (RuntimeException e)` in `BotRoster.guard`; the spec says "on a throw"; broken NMS raises `LinkageError` | patch |
| E3 / E8 / B2 | A throwing `flush()` in `BotBody.remove()` (after `removed = true`) skips disconnect and quit: online ghost, unlisted, unremovable | medium | `remove()` sets `removed`, then `flush()` before `disconnect`; a FAILED bot's flush may be what broke; `exit` drops it in `finally` | patch |
| B9a | Failed-delete warning drops the stack trace (`": " + e`) | low | Direct one-line correction | patch |
| G1 / B6 | Removing a bot spawned in the same loop is untested | low | Gap finding as filed | patch |
| G2 | Kicking a FAILED bot is untested | low | Gap finding as filed | patch |
| G3 | Wipe after a kick is untested | low | Gap finding as filed | patch |
| G4 / B8 | `.dat_old` deletion cannot fail its test | low | Gap finding as filed: a first save leaves no backup | patch |
| E2 | Pending spawns/removals skipped if something escapes the loop | low | After the `LinkageError` patch only VM-fatal errors escape; unlikely; moving the drain into `finally` adds branches on a broken server | reject |
| E4 / B12a | `BotTickEvent` accepts a null bot; class not final | low | Only the roster constructs it; adds guards for an unlikely misuse | reject |
| E5 / E9 / B13 | Log-prefix check covers every `NekotineCore` line, may catch unrelated module lines | low | All 8 tests passed; modules load before capture; widen only if it flakes | reject |
| E6 | Misleading message if A never ticks after the teleport | low | Cosmetic, only on an already failing run | reject |
| B3 | Default wipe deletes a real offline player's data with the same name | low | Offline UUID from name is mandated (AD-10), and 1.3 already overwrote that data on save (1.3 B5/E13) | reject |
| B4 | A lost connection exits mid-loop, not after it | false | AD-10 defers spawn and remove *requests*; a kick's quit already fired inside `connection.tick()`, so deferring changes nothing observable | reject |
| B5 | A bot kicked from its own listener still gets `tickAfterEvent` and may fail | false | A kick closes the channel only once its disconnect packet is flushed, so that flush is required; `flush()` checks `isOpen()` | reject |
| B6b | Self-removal, B-removes-A-after-tick, double remove in a loop untested | low | `remove` dedups via a set and `isRemoved`; paths are trivial; extra tests add little | reject |
| G6 / B7 | "Event fires before the flush" is untested | low | No consumer sends from a listener yet; pin it in 1.6, whose controls rely on it | reject |
| G5 | A failure in `tickAfterEvent` is untested | low | Needs a second fault seam; same `guard` as the tested half | reject |
| B9b | Failed-delete warning untested | low | Needs a locked-file setup; unlikely | reject |
| B10 | `failNextTick` is public in the shipped jar | low | `bot.internal` is not API (AD-3), as `writeInbound` in 1.3 | reject |
| B11 | README omits the overload, flag meanings, deferral, frozen FAILED entity | low | README is an inventory; the adopter guide is a parked follow-up; Javadoc covers each | reject |
| B12b | `unload()` called mid-loop corrupts deferral | low | Only plugin disable calls `unload()`, never from a tick listener | reject |
| G-other | New ITs run only under `integrationTest` | false | Documented in AGENTS.md; ran green before the PR | reject |

## Design Notes

"Stops ticking" in AD-11 is read as: no connection tick, acks or event, so the player entity freezes and Paper's keep-alive timeout (checked in the listener tick) never kicks it, which keeps it connected. Flushing and releasing still run because the server keeps writing chunk and entity packets to the bot, which would otherwise pile up in the embedded channel, and because a kick only closes the channel once its disconnect packet is flushed.

## Verification

**Commands:**
- `gradlew.bat build` then `gradlew.bat integrationTest` (in `NekotineCore/`) -- expected: BUILD SUCCESSFUL, `BotTickIT`, `BotLifecycleIT`, `BotsTestHostSmokeIT` pass
- `gradlew.bat -PVi6CleanJarOutputPath=build/dist`, `gradlew.bat build`, `gradlew.bat integrationTest` (Vi6Clean) -- expected: all green

**Manual checks:**
- No lockfile changes.
