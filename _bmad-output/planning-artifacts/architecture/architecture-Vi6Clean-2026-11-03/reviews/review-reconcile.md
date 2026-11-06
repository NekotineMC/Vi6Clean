---
review: reconcile
target: ../ARCHITECTURE-SPINE.md (updated 2026-11-05)
sources:
  - _bmad-output/specs/spec-bots/SPEC.md
  - _bmad-output/specs/spec-bots/test-coverage-boundary.md
decision-log: ../.memlog.md
date: 2026-11-05
---

# Reconcile review: spine vs spec-bots

## Verdict

**Needs revision, but only in a few places.** The spine carries almost all of the spec's load-bearing constraints: real fake players through `placeNewPlayer`, real handlers reached through the inbound pipeline, server physics plus `handleMovePlayer`, the boot self-test, ephemeral per-host rosters, one MC version for `bots`, and no bot branch in game logic. Every structural departure from the spec is recorded in the memlog. Six silent gaps remain. Four of them sit on the boundary between the bot tick and the server (listener-position reset, ack rules, the threading of `send()`, flush-before-close). The other two leave a spec requirement without an owner (the host side of "failures are loud", and repeatable runs). Any of the six could let the `bots` body, the Vi6 brains and the Vi6 test hooks be built inconsistently.

## (a) Deliberate changes recorded in the memlog: the spec needs an update

| # | Spec text | Spine / memlog decision |
|---|---|---|
| D1 | NekotineBots is "its own repository … stays out of NekotineCore so Core is not pinned"; "no required dependency on Core" | NekotineCore becomes a two-module repo: `core` (Paper API only, unpinned) and `bots` (paperweight, NMS) **depending on `core`** (AD-1; memlog "NekotineBots becomes part of NekotineCore", "repo becomes multi-module"). CAP-7's "depends on NekotineBots" now means "depends on NekotineCore `bots`". |
| D2 | "included … as a git submodule and Gradle subproject like NekotineCore" | One NekotineCore submodule consumed through `includeBuild`. No separate submodule, no Gradle subproject (AD-1; memlog question on wiring, resolved). |
| D3 | "its public API uses Bukkit types only" | Widened to Bukkit, Paper **and `core`** types (AD-3). Implied by D1 and the control-port decision. |
| D4 | NekotineBots "owns the body and input layers; host plugins own the behaviour layer" (navigation location unstated) | Pathfinding, path following (walkers) and melee controllers live in `core`, as strategies behind a control port. `bots` ships only the bot adapter (AD-6, AD-7, AD-8; memlog "pathfinding is not bot-only", "melee controller lives in NekotineCore too"). |
| D5 | Navigation is "A* over Bukkit block data, the Pathetic library or a precomputed per-map navigation graph" | Still allowed. Adds a vanilla-pathfinder implementation and an RL walker later. The shared contract is Paper `PathResult` (AD-6, AD-7). |
| D6 | "An exception in bot behaviour is caught and logged with the bot's name **and the current phase**; a bot never stops acting silently" | Library logs once with the name and stack trace, then **disables** the bot (it stays connected and is reported as failed). The phase is dropped: the library knows no host concepts. Exceptions thrown by host listeners are left to Paper (AD-11; memlog "Spec change: 'logged with the current phase' dropped … Offer to update SPEC.md"). See F2 for what this leaves unowned. |
| D7 | NekotineBots "tests itself … through a tiny host plugin in its own repository" | The tests live in the NekotineCore repository (`bots-test-host`). How that repo gets paper-junit-server is an open question in the spine (AD-13; memlog last question). |
| D8 | (unstated) shading per host | Libraries never shade; only the final plugin shades (AD-2). Note: NekotineCore's `build.gradle.kts` currently runs shadow and relocates CommandAPI to `fr.nekotine.core.commandapi`. AD-2 changes current Core behaviour, and Vi6's CommandAPI relocation (AGENTS.md "matching Core's own shading") has to follow. |
| D9 | (unstated) commands | The library ships no command. Hosts own their commands (AD-14, `[ASSUMPTION]`). |
| D10 | (unstated) threading | All bot work runs on the main thread. A pathfinder may search off-thread (AD-9, `[ASSUMPTION]`). See F4. |
| D11 | (none) | F3 path-debug rendering is out of scope (Deferred; memlog). |

## (b) Findings: silent drops, contradictions, weakenings

### F1 (High): the tick sequence omits "reset the listener position", and the ack rules are dropped

- **Spec:** "the bot's own tick **resets the listener position**, sends pending acks, runs actions and physics." Also: "One teleport ack per received position id, **latest only**, **on the next tick** and **never from inside `send()`** (a matching id with nothing pending disconnects the bot); a player-loaded ack **after join and each respawn**."
- **Spine:** the "One bot tick" sequence diagram is the de facto contract for the body, and it starts at "send pending teleport ack / player-loaded". The listener-position reset (the per-tick bookkeeping `ServerGamePacketListenerImpl.tick()` does for a real connection) is missing. Without it, the movement checks compare against stale positions. The likely result is the "moved too quickly" snap-back that CAP-8 forbids ("without being snapped back by the server"). The ack rules shrink to "pending teleport acks". "Latest only", "next tick", "never inside `send()`" and the disconnect hazard are gone.
- **Divergence risk:** the respawn control (AD-4, UI) and the player-loaded ack after each respawn sit in different places in the code. With no stated rule connecting them, whoever implements respawn need not trigger the ack. AD-5 promises hosts an ordering ("after acks, before inputs") but never says what an ack is.
- **Fix:** add the reset step as the first step of the tick diagram, and state the ack rules (or cite the SPEC constraint) in AD-5 or a new body rule. Include "player-loaded ack after join and after every respawn control".

### F2 (High): "a bot never stops acting silently / logged with the bot's name" has no owner for host behaviour

- **Spec:** "An exception **in bot behaviour** is caught and logged with the bot's name." Behaviour means the brains, which the spine puts in the host.
- **Spine:** AD-11 guards only the library's own per-bot work. Host listener exceptions "belong to the host, and Paper logs them". Paper's "Could not pass event" log does not carry the bot's name. The bot keeps ticking, so a broken brain logs one stack trace per tick, which is exactly the "20 stack traces a second" that AD-11 claims to prevent. The spine also leaves unclear who owns an exception thrown by a `core` walker or melee strategy. It runs inside the host's tick-event listener, so by AD-11 it is a host exception, not a library one.
- **The deliberate part (D6)** is dropping the phase and moving library errors into `bots`. **The silent part** is that no rule binds Vi6's brains (or `core` strategies) to catch and log with the bot's name, or to stop. Two Vi6 brain units, say navigation and combat, can handle failures differently, and the unattended soak run then fails on log spam rather than on the actual defect.
- **Fix:** add a host-side rule for Vi6: brains wrap their tick-event work, log once with the bot's name (and, in Vi6, the phase), then stop driving or ask the roster to disable the bot. Alternatively, give the roster a "report failure" API that hosts and strategies call.

### F3 (Medium): repeatability (3 identical runs), the soak criterion and clean-roster restarts are not carried, and AD-7 works against them

- **Spec:** "Scripted-bot tests of a tool or phase give the same result on 3 consecutive runs." "A soak test with autonomous bots passes when the game reaches a result with no exception in the log." CAP-5: "can be repeated, each run starting from a clean roster with **no leftover bots or scoreboard entries**."
- **Spine:** none of these appear. AD-13 only assigns "resetting between tests" to Vi6. AD-7 lets a pathfinder finish its search off-thread and hand the result back on a later, unspecified tick. A scripted run that waits on such a search is then nondeterministic from tick to tick, which defeats the 3-run rule. AD-5 settles listener order but not the order bots tick in (the roster's iteration order), which also affects determinism.
- **Fix:** state determinism as a rule. The roster ticks bots in a stable order (for example spawn order). A scripted test either uses a synchronous pathfinder or waits for the `PathResult` before it asserts. Name who clears bot scoreboard and team entries when a bot is removed: Vi6 quit cleanup (CAP-2 parity) or the Vi6 reset hook.

### F4 (Medium): AD-9 "all bot work on the main thread" does not hold for `send()`

- **Spec:** clientbound packets reach the bot's `send()`. Acks are recorded there but must "never" be sent "from inside `send()`". Assumptions: outbound sends from `PantsHandler` and `TrapWireHandler` are safe. "Not checked: writing inbound off the main thread."
- **Spine:** AD-9 says controls, tick event, roster and `writeInbound` are main-thread only. That is correct for what the bot *sends*. But the server calls the bot's `send()` from whichever thread sends a packet (async chunk sends, ProtocolLib or plugin async sends). AD-9 never says that this path must be thread-safe and must only record state, such as the latest teleport id, for the tick to act on.
- **Fix:** add to AD-9 that `send()` may run on any thread and only records (into thread-safe state) what the next main-thread tick acts on. It never writes inbound and never acks.

### F5 (Medium): flushing before close is dropped, and the removal path is unspecified

- **Spec:** flush outbound writes "from the bot's tick **and before closing the channel**, or every pending write fails with `StacklessClosedChannelException` warnings on close."
- **Spine:** the tick diagram ends with `flushOutbound()`. AD-10 says a bot "leaves through the normal quit path" and `unload()` removes every bot, but nothing says the channel is flushed before it closes. Removal happens on disable, on the remove command and between tests, so different callers would produce warning spam. That spam undermines the "no exception in the log" success checks (CAP-5, soak, Demonstration step 5).
- **Fix:** add to AD-10: removal flushes, then quits, then closes the channel, and it is the only removal path.

### F6 (Medium): AD-7 allows an off-thread search, but the caller's passability rule reads Bukkit block data

- **Spec:** "A* over Bukkit block data". CAP-4: "respecting the map's entrance and exit rules" (Vi6 rules, supplied by the caller per AD-7).
- **Spine:** AD-7 says the caller supplies passability and cost, and that an implementation may search off the main thread. It does not say which thread the caller's rule runs on, or what world view it gets (live `World` versus snapshots). Vi6's entrance and exit rule and an async A* implementation can each be built correctly on their own terms and still be unsafe together.
- **Fix:** make the passability contract either main-thread-only (so a pathfinder that wants to run async must snapshot first) or explicitly snapshot-based.

### F7 (Low): ProtocolLib's unverified points are not tracked in the spine

- **Spec, "Not checked":** whether `event.getPlayer()` is the bot's `CraftPlayer` or a ProtocolLib temporary player, whether `ClientTrackModule` itself works, and whether packet fields read through `PacketContainer` (for example `getPlayerActions()` on `ENTITY_ACTION`) come back right.
- **Spine:** these are not listed as `[ASSUMPTION]` or as an open question. `ClientTrackModule` lives in NekotineCore `core` (`track/ClientTrackModule.java`). It keys a `HashMap<Player, ActionState>` and reads sneak and sprint from `ENTITY_ACTION`. The coverage table, however, routes sneak and sprint through `handlePlayerInput`. AD-4's "held movement keys: sneak, sprint" does not say which serverbound packet(s) a sprint or sneak becomes. A body that sends only `PlayerInput` could therefore leave `ClientTrackModule`'s state stale.
- **Fix:** add these to Open Questions, or mark them `[ASSUMPTION]` on AD-4, and pin the packet each held key emits.

### F8 (Low): smaller constraints that are dropped or weakened

- **Java, not Kotlin:** the Stack lists "Java 25" but never says Kotlin is ruled out, or why (Kotlin API features leak into Java callers). It matters now that `core` strategies are a shared public API too.
- **Baritone does not run server-side, and "no multi-version layer":** neither is stated. AD-7 makes pathfinders swappable without excluding Baritone. AD-1 says `bots` is tied to one version, but does not forbid a multi-version layer.
- **"Minimal reflection":** AD-3 keeps the count and the self-test, but drops "minimal".
- **MIT notice on verbatim snippets:** dropped. Related, not in the spec: AD-2 shades Pathetic (MIT) into host jars, so its licence notice has to ship in each jar.
- **Refused moves diagnosed through `PlayerFailMoveEvent`:** dropped. This is the loud-failure path for snap-backs (CAP-8).
- **Sizing (6 players at most; fill means 1-5 bots; unattended means 4, or 6 at most):** only "`max-players` ≥ 6" survives. AD-14's "add N bots" has no bound. The spec's coverage boundary says `max-players=20` is enough. It is also unverified whether `placeNewPlayer` enforces `max-players` at all, since the login-time check is skipped.
- **The verified `EmbeddedChannel` / ProtocolLib setup** (unregistered `EmbeddedChannel(false,false)` then `register()`, the `inbound_config` decoder swap, `setupOutboundProtocol`, keeping the raw channel for `writeInbound`): this is internal to one unit and can stay in the spec. The spine should still cite the SPEC Assumptions block from AD-3/AD-4 as binding body setup, because ProtocolLib silently skips an inactive channel or a HANDSHAKING decoder.
- **ProtocolLib version:** the spike verified 5.5.0-SNAPSHOT. The spine compiles against 5.4.0 and does not say which version production runs. Production must match the test server's 5.5.0-SNAPSHOT, or the verification does not carry over.

### F9 (Low): the memlog and the spine disagree on tick order

The memlog decision (line 7 of the log) says the per-bot tick event fires "**before** acks/inputs/physics". AD-5 and the sequence diagram fire it **after** the acks. The spine agrees with the spec ("resets … sends pending acks, runs actions and physics"), but the memlog records no reversal. Add a memlog entry so the log stays authoritative.

### Not in the spec, added by the spine (check only)

- "Spawn protection disabled on the dev server" is a new server requirement. `test-bundle/` must match it ("keep it in line with production"), and the coverage boundary's "Server settings a bot run needs" should list it.

## What landed correctly (no action)

Real fake players and the join/quit events (AD-10). Real handlers through the inbound pipeline, with no fabricated events and no direct game-mode, slot or position calls (AD-4). Server physics with `Pos`/`PosRot` through `handleMovePlayer` (AD-4 and the diagram). An NMS surface of about 25 types plus the boot self-test (AD-3). Ephemeral, per-host rosters (AD-10). No bot branch in game logic, and team choice through the lobby path (AD-12). The dialog custom-click and respawn controls (AD-4). Library tests that use no harness copy (AD-13 plus the open question). Navigation and combat kept separate (AD-7, AD-8). Main-thread `writeInbound` (AD-9). Chat and commands sent by bots stay excluded (AD-4 omits them). Entity interaction beyond attack and interact is deferred.
