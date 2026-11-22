# Epic 5 Context: Bots move around the map on their own

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Bots stop standing idle: with no operator input, a bot travels between map areas for the whole round while respecting the map's entrance and exit rules, so a bot-filled game becomes watchable and exercises real movement. The epic builds reusable, body-agnostic navigation strategies in NekotineCore `core` (a control port with a bot adapter, a pathfinder port with an implementation chosen for Vi6, and a vanilla-like walker) so mobs and future minigames can reuse them, then adds Vi6's passability rules and roaming brains. Its last story upgrades the Epic 4 unattended run from idle bots to roaming ones, with the soak test still green. Epic 6 (melee) builds directly on the control port and the walker's moving goal.

## Stories

- Story 5.1: A control port lets strategies drive a body, with a priority order per control group
- Story 5.2: A pathfinder port, with an implementation chosen for Vi6
- Story 5.3: A vanilla-like walker follows any `PathResult`
- Story 5.4: Vi6 passability rules respect entrances, exits and gates
- Story 5.5: Vi6 bots roam between map areas for the whole round
- Story 5.6: The unattended run uses roaming bots

## Requirements & Constraints

- **Autonomous roaming:** in in-map and infiltration, guard bots pick map areas (rooms, artefacts, KoTHs) as goals, walk to each, and pick a new goal on arrival or when stuck. Thief bots first walk from their spawn through their selected entrance, then roam the same way. No bot stays stuck longer than a set time without picking a new goal, for the whole round.
- **Map rules for bots match those for humans:** guards never route through an exit or an entrance blocking box; a thief not yet inside routes only through its selected entrance; a thief inside may route to an exit only when its caller asks to leave.
- **Stealing in the unattended run:** a roaming thief that reaches an artefact stays in its zone until the capture completes, gives up if a guard enters the zone, and after stealing walks to an exit.
- **Pathfinder:** `find(from, to, rule)` returns Paper's `Pathfinder.PathResult`; the caller supplies the passability and cost rule per search. Choice of implementation for Vi6 (own A*, Pathetic adapter `de.bsommerfeld.pathetic:engine` 5.5.2, per-map graph, or vanilla mob pathfinder) is recorded with reasons (path quality on a Vi6 map, speed for 6 bots, licence). If Pathetic is shaded, its MIT notice ships in the host jar.
- **Walker:** reaches the goal on a test structure with walls, a 1-block step, a drop and a door, with no `PlayerFailMoveEvent` refusal. When blocked for a set time it reports "stuck", sets movement to neutral and leaves the decision to the caller. It follows a moving goal without restarting.
- **Determinism:** library walker tests give the same result 3 runs in a row; brain randomness uses the seedable per-run random source from Epic 4. The soak test must pass 3 times in a row with roaming brains on short timers, reaching a result with no exception.
- **Backward compatibility:** Epic 2-4 brains that write controls directly keep working unchanged after the control port lands; the Epic 4 soak test stays green throughout.
- **Failure isolation:** a brain or walker failure is logged once with bot and game context (phase), that brain stops, and the bot stays connected.
- **Dev-server acceptance:** a 4-bot then a 6-bot unattended run reach a result with visibly roaming bots, summarized in a Markdown note (never commit server logs).
- **Out of scope:** RL walker, mob adapter, F3 path debug rendering, inventory clicks (bots still buy no tools).

## Technical Decisions

- **Layering:** host brains (Vi6 `fr.nekotine.vi6clean.bot`) → `core` strategies and ports (`fr.nekotine.core.navigation`, Paper API only) ← `bots` adapter. `core` never depends on `bots`. Points cross module boundaries as Bukkit `Location`; NMS types never leave `fr.nekotine.core.bot.internal`.
- **Control port:** mirrors the movement (heading yaw, forward/back, strafe, jump, sneak, sprint), aim (yaw/pitch) and hands groups, and exposes location, velocity, on-ground and look as world-space Bukkit values. Story 5.1 fixes the method names. The `bots` adapter maps it 1:1 onto bot controls with no extra behaviour; a strategy moves a body only through this port.
- **Priority, not ownership:** any writer may set any group; when several write the same group in one tick, a fixed priority order decides, independent of listener order. For aim, walker applies first and melee overrides it. A writer that sets nothing leaves the lower-priority write. No claim/release, no ownership errors. Story 5.1 fixes the full order per group.
- **Tick model:** strategies run only inside the per-bot tick event (never their own `TickElapsedEvent` listener); controls set in that event apply the same tick. Position changes only via the body's physics step along the movement heading (heading may differ from look).
- **Path contract:** walkers accept any `PathResult` (including a vanilla mob's), treat it as read-only, keep their own progress cursor, and infer step/jump/drop/door from point geometry and the world. A pathfinder may implement a Nekotine sub-interface with per-point hints; walkers may use them but must work without.
- **Threading:** everything is main-thread except an optional off-thread search, which reads only a main-thread world snapshot, calls the caller's rule only on that snapshot, and completes on the main thread.
- **Game rules stay out of the pathfinder:** Vi6's rule is built from `Vi6Map` data (entrances with trigger and blocking boxes, exits, gates, room captors) read through Vi6's public API.
- **Human rules for brains:** brains read state only through Vi6's public API and act only through bot controls and player commands; no Vi6 game-logic file checks for bots; brains never call test hooks.
- **Test placement:** library stories (5.1-5.3) are tested in NekotineCore through `bots-test-host` on its own paper-junit-server, with no Vi6 dependency. Vi6 stories (5.4-5.6) are tested in Vi6's `integrationTest` suite, using bots only as a driver. Avoid growing `Vi6PhaseMachineBotIT` further; split it if needed.
- **Repo mechanics:** NekotineCore changes ship with their gitlink commit in the same Vi6 PR; story PRs target the `bot` branch.

## Cross-Story Dependencies

- 5.1 (control port, adapter, priority) is the foundation for 5.3 and for Epic 6's melee controller.
- 5.2's test structure is reused by 5.3; 5.3 consumes 5.2's `PathResult` but must also work with vanilla paths.
- 5.4 depends on 5.2's rule parameter; 5.5 combines 5.3's walker with 5.4's rule and Epic 4's seedable random source and thief entrance selection.
- 5.6 swaps 5.5's brains into Epic 4's `/bot run` and soak test (Stories 4.4-4.5). Known Epic 4 limitation: the run seed replays bot picks only, not KoTH placement, so with KoTHs on the same seed gives a different game.
- Epic 6 relies on the walker following a moving goal (5.3) and on melee aim overriding walker aim (5.1).
