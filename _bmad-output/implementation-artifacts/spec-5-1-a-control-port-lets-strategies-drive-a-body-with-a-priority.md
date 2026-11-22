---
title: 'Story 5.1: A control port lets strategies drive a body, with a priority order per control group'
type: 'feature'
created: '2026-11-22'
status: 'done'
baseline_commit: 'ea206a0af796631a8f197eb2582b7f8256504013'
nekotinecore_baseline_commit: 'c8bc25285f9de1acfc9ab82bfe786c2e9740c6a9'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-5-context.md'
  - '{project-root}/NekotineCore/AGENTS.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Walkers (Story 5.3) and the melee controller (Story 6.1) must drive bots now and mobs later, but the only control surface is `Bot`, which is bot-only and last-write-wins, so two strategies writing the same controls in a tick jitter by listener order (AD-5 amended, AD-8).

**Approach:** Add a body-agnostic control port in `core` (`fr.nekotine.core.navigation`) whose writers carry a fixed priority; a reusable arbiter keeps, per control group and per tick, only the highest-priority writer's writes. `bots` ships a 1:1 bot adapter, reached through `Bot.body()`, whose arbitrated writes the roster applies right after the bot's `BotTickEvent`.

## Boundaries & Constraints

**Always:**
- Port types use Paper API only (`core` has no paperweight). Groups mirror AD-4: movement (forward, backward, left, right, jump, sneak, sprint, heading), aim (look), hands (slot, useItem, useItemOn, leftClick, startBreaking, attack, interact, swapHands). Reads: location (`Location` copy), velocity (`Vector`), on-ground, look yaw/pitch, all world-space.
- One order for every group: `WALKER` < `MELEE` < `BRAIN` (enum ordinal). Per group per tick, the highest priority that wrote anything wins; only its writes in that group apply, in call order; lower writes in that group are dropped; controls it did not set keep their persisted value. A tick with no port write in a group leaves the bot's controls as they are.
- Direct `Bot` setters stay as they are and sit below every port priority (port writes apply after the event, so they overwrite a same-tick direct write of the same control).
- Port writes made during the bot's `BotTickEvent` apply that tick; made anywhere else, at the bot's next tick. No write fails for priority reasons: no claim, release or ownership. Writes are main-thread only (`IllegalStateException` off it) and validate their arguments when called (`IllegalArgumentException`), as `Bot` does.
- Writes to a failed or removed bot are dropped; failing a bot clears its pending port writes.
- NekotineCore changes are committed on a branch from NekotineCore `bot`, PR into `bot`; the Vi6 PR (into Vi6 `bot`) bumps the gitlink.

**Never:**
- No walker, pathfinder or melee code (Stories 5.2, 5.3, 6.1); no Vi6 game-code change; Vi6 brains are not migrated to the port.
- No `Other` group (commands, respawn) on the port; no cooldown/reach reads (Story 6.1 adds them).
- No change to physics, packets or `Bot` semantics beyond adding `body()`.
- Non-goals from the deferred-work grep (retro item 10): server knockback for bots and sprint/sneak resync after the server clears sprinting were tagged "Epic 5 melee"; melee is Epic 6, so they are retargeted to Story 6.1, not done here. No open `action_items` target Story 5.1 or Epic 5.
- No refresh of `bots-adopter-guide.md` (Story 7.2).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Aim conflict | WALKER and MELEE `setLook` in one tick, either listener order | MELEE's look applies | N/A |
| Writer stops | next tick only WALKER `setLook` | WALKER's look applies | N/A |
| Group-level win | WALKER `leftClick`, MELEE `setSlot(5)` same tick | slot 5; no left click sent | N/A |
| Lone writer | only WALKER writes hands (`setSlot(2)`, `useItem`) | both apply | N/A |
| Port over direct | brain `bot.setForward(false)` and WALKER `setForward(true)` in the event | forward held | N/A |
| Write outside event | WALKER `setLook` from a plain task | applies at the next tick | N/A |
| Bad argument | non-finite look/heading, slot 9, hand `HEAD`, non-cube face, other-world target | rejected at call | `IllegalArgumentException` |
| Off main thread | any port write | rejected | `IllegalStateException` |

</frozen-after-approval>

## Code Map

- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/Bot.java` -- public bot API (AD-4 controls, validation and threading rules to mirror); add `Body body()`.
- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotBody.java` -- implements `Bot`; `tickAfterEvent()` (~line 520) is where arbitrated writes must be applied before `apply()`; `fail()` (~471) and `remove()` (~630) clear state; reuse `requireMainThread`, `requireSameWorld`, `hand`, `direction` validation logic.
- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotPlayer.java` -- client-side `clientVelocity`, `clientOnGround()`; add a `clientVelocity()` getter for the velocity read.
- `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/BotRoster.java` -- tick loop (~270-279): before-event, `BotTickEvent`, after-event; no change expected.
- `NekotineCore/core/src/test/java` -- JUnit 5 + paper-api on the test classpath; existing files are commented out, so a new test is the only one that runs.
- `NekotineCore/bots-test-host/src/integrationTest/java/fr/nekotine/botstesthost/it/BotMovementIT.java`, `BotHandsIT.java`, `BotLifecycleIT.java` -- IT patterns (platform at `FLOOR_Y`, `register`, `roster`, `cleanUp`, `PlayerEvents`) to reuse.
- `src/integrationTest/java/.../Vi6SoakIT` (Epic 4 soak) -- must stay green unchanged.

## Tasks & Acceptance

**Execution:**
- [x] `NekotineCore/core/src/main/java/fr/nekotine/core/navigation/ControlPriority.java` -- enum `WALKER, MELEE, BRAIN` with Javadoc stating the order applies to every group -- AD-5 fixed order.
- [x] `NekotineCore/core/src/main/java/fr/nekotine/core/navigation/BodyControls.java` -- writer interface: the movement, aim and hands setters with `Bot`'s signatures and argument rules -- port method names (fixed here, per the spine's deferral).
- [x] `NekotineCore/core/src/main/java/fr/nekotine/core/navigation/Body.java` -- port: `getLocation()`, `getVelocity()`, `isOnGround()`, `getLookYaw()`, `getLookPitch()`, `controls(ControlPriority)`; Javadoc states the tick, priority and strategies-run-only-in-the-body's-tick-event rules -- AD-5, AD-8.
- [x] `NekotineCore/core/src/main/java/fr/nekotine/core/navigation/ControlArbiter.java` -- reusable buffer: `writer(priority)` validates eagerly (thread check and world supplied by the adapter) and records ops per group under the I/O rules; `applyTo(BodyControls sink)` replays the winners (movement, aim, hands) and resets; `clear()` -- shared by bot and future mob adapters.
- [x] `NekotineCore/core/src/test/java/fr/nekotine/core/navigation/ControlArbiterTest.java` -- plain tests of the matrix's arbitration rows against a recording sink -- no server needed.
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/internal/BotControlPort.java` -- `Body` adapter: reads from the bot (`player().getLocation()`, client velocity, client on-ground, look), sink maps 1:1 to `BotBody` setters; `BotBody` owns one, applies it at the start of `tickAfterEvent()`, clears it in `fail()`/`remove()`, and drops writes when not live; `Bot.body()` returns it -- AD-8 1:1 adapter.
- [x] `NekotineCore/bots-test-host/src/integrationTest/java/fr/nekotine/botstesthost/it/BotControlPortIT.java` -- matrix rows on a real bot (both listener orders via `EventPriority`), reads (location, on-ground, velocity y > 0 after a jump), next-tick application outside the event, eager errors -- AD-13 library test.
- [x] `NekotineCore/bots/src/main/java/fr/nekotine/core/bot/Bot.java` -- Javadoc for `body()`: direct setters rank below every port priority.
- [x] `_bmad-output/implementation-artifacts/deferred-work.md` -- retarget the two "Epic 5 melee" entries (knockback, sprint resync) to Story 6.1 -- retro item 10.
- [x] `NekotineCore` gitlink in Vi6 -- bump to the NekotineCore commit -- AGENTS.md submodule rule.

**Acceptance Criteria:**
- Given a bot and the brains from Epics 2-4, when NekotineCore lands, then Vi6 compiles unchanged and `gradlew.bat integrationTest` (including the Epic 4 soak) passes.
- Given the port types, when `core` builds, then they import only Java, Bukkit and Paper types.
- Given any port write, when it is called, then it never fails for a priority reason.

## Implementation Notes

- `ControlArbiter(name, threadCheck, world, live)`: the adapter supplies the thread check, the body's world for target checks and a liveness test; writes are checked (thread, then arguments), dropped when not live, then recorded per group. `applyTo` snapshots and resets before replaying, so writes made while replaying wait for the next replay. Faces are checked with `BlockFace.isCartesian()`.
- `BotControlPort` re-checks a block/entity target's world when replaying and drops it if the bot changed world since the write (a write made outside the event), rather than letting `BotBody`'s setter throw and fail the bot.
- `BotBody.requireMainThread` is now package-private so the port reuses it.
- `core` tests never ran under Gradle 9 (no JUnit Platform launcher on the test runtime classpath): added `junit-platform-launcher` (versionless, from the engine's `junit-bom`) to `libs.versions.toml` and `core` `testRuntimeOnly`, lock updated with `--update-locks org.junit.platform:junit-platform-launcher` (one added line in `core/gradle.lockfile`).
- Vi6 verified with `gradlew.bat spotlessApply build` instead of bare `gradlew`, because the local `Vi6CleanJarOutputPath` points at the dev server's `plugins/update` and would restart it.
- Review pass 1 patches: `BotControlPortIT` gained `portTargetsReachTheBot` and `targetLeftBehindByAWorldChangeIsDropped`; the vacuous failed/removed assertions became `writesToAFailedOrRemovedBotDoNotThrow`; port reads now check the main thread; `Body` Javadoc states the client-velocity caveat and that losing writes drop silently, so strategies rewrite their whole group each tick.
- Final verification (2026-11-22): NekotineCore `gradlew.bat spotlessApply build integrationTest` green (`ControlArbiterTest` 16/16, `BotControlPortIT` 11/11); Vi6 `gradlew.bat spotlessApply build integrationTest` green (`Vi6SoakIT` 3/3).
- Checkpoint 1 approval was given by the session that delegated this story (its done criterion: spec `done`, build green, PR into `bot`), not by a human in this session.

## Spec Change Log

## Review Triage Log

Pass 1 (blind, edge-case, verification-gap layers).

| # | Finding | Verdict | Evidence | Route |
|---|---------|---------|----------|-------|
| 1 | No test sends a block or entity target through the port's sink, nor covers the replay-time world drop (verification-gap; blind; edge-case) | medium | `BotControlPortIT` reaches the sink only with slot, useItem and leftClick; nothing teleports a bot between write and replay | patch |
| 2 | `failedOrRemovedBotDropsPortWrites` cannot fail (verification-gap; blind) | low | a FAILED bot only runs `tickFailed`, a removed bot is not ticked, so a pending write can never apply either way | patch (drop the vacuous assertions) |
| 3 | Port reads skip the main-thread check `Body` documents (blind; edge-case) | low | `BotControlPort` reads call no `requireMainThread`; one-line fix per read | patch |
| 4 | `getVelocity()` doc hides that the bot adapter returns client velocity without server-applied velocity (blind) | low | `BotPlayer.clientVelocity` is the simulated client state; `Bot` Javadoc lists knockback as not modelled | patch (doc) |
| 5 | A losing write is dropped silently, so a write-on-change strategy goes out of sync (blind) | medium | Design Notes accept group-level drops; `Body` Javadoc did not tell strategies to rewrite their group each tick | patch (doc) |
| 6 | One throwing sink write loses the rest of the replay and skips the tick's apply (blind; edge-case) | false | after the arbiter's eager checks the only replay-time throw left is `requireSameWorld`, which the sink pre-checks; `requireMainThread` holds on the roster's tick; `attack`/`interact` on a removed entity read only its id and box, which do not throw | reject |
| 7 | Replay does not check that an entity target is still valid (blind) | false | a client sends an attack on a dead entity too; the server's own checks decide, as `Bot` documents ("The library checks no reach") | reject |
| 8 | Null block or target throws NPE, not IAE (blind; edge-case for `controls(null)`) | low | same as `Bot`'s own `Objects.requireNonNull`; rare, needs no guard | reject |
| 9 | Writers cannot tell a write was dropped (blind) | false | the intent rules out claims and feedback; Story 6.1's melee reads cooldown from the server, not from its own write | reject |
| 10 | `port.apply()` runs before the connection check (blind) | false | replay only sets fields; `apply()` and its packets stay behind `isConnected()` | reject |
| 11 | `ControlArbiter` is public API (blind) | false | public on purpose: the spec's task makes it the shared piece for future mob adapters | reject |
| 12 | NekotineCore commit and gitlink missing; planning files not in the diff (blind) | false | done at commit time (spec task); the spec and context files are committed with the Vi6 PR | reject |
| 13 | JUnit launcher added to `core` only (blind) | false | `bots` and `bots-test-host` have no `src/test`; only `core` runs plain tests | reject |
| 14 | IT steps rely on fixed `awaitTicks` timing (blind) | maybe-false | would be at most low; settle by running `BotControlPortIT` under load several times; all runs so far passed | reject |
| 15 | A cross-world target that wins the hands group blocks lower writes that tick (edge-case) | low | real but needs a world change between a write outside the event and the tick; fix adds branching | reject |
| 16 | `scene()` leaks the bot and listeners if it throws midway (edge-case) | low | test-only, only after another failure; fix adds try/catch scaffolding | reject |
| 17 | The bad-target test needs a second world (edge-case) | false | the IT server loads the nether and end; the test ran green | reject |
| 18 | Tests assume the initial held slot is 0 (edge-case) | false | bots spawn with the reset-state wipe by default, so no saved slot is restored | reject |
| 19 | `NekotineCore/AGENTS.md` still says every `core` test is commented out (verification-gap) | medium | `ControlArbiterTest` now runs 16 tests under `gradlew test` | defer (agent-context file) |
| 20 | Vi6's `build` runs neither new test (verification-gap) | false | not a defect: both run in NekotineCore's `build` and `integrationTest`, and were run for this change | reject |

## Design Notes

Arbitration is per group, not per control: a higher writer that sets only `sneak` takes the movement group for that tick, and the walker's `forward` from the previous tick persists (AD-4 persistence), so the bot does not stop. Per-control arbitration was rejected because it mixes two writers' intents in one group, which is the jitter AD-5 prevents.

`BRAIN` is highest so a brain can always override its strategies for a tick without stopping them; direct `Bot` setters stay a base layer so Epic 2-4 brains need no change.

```java
Body body = bot.body();
BodyControls walk = body.controls(ControlPriority.WALKER);
walk.setForward(true); walk.setLook(yaw, 0);           // overridden while melee aims
body.controls(ControlPriority.MELEE).setLook(ty, tp);  // wins aim this tick only
```

## Verification

**Commands:**
- `NekotineCore: gradlew.bat build` -- expected: green, `ControlArbiterTest` runs.
- `NekotineCore: gradlew.bat integrationTest` -- expected: green, including `BotControlPortIT`.
- `Vi6Clean: gradlew.bat` then `gradlew.bat build` and `gradlew.bat integrationTest` -- expected: green.
