# Epic 3 Context: Vi6 tools and phases get regression tests driven by bots

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Give a Vi6 test author what they need to write fast, deterministic regression tests for Vi6 gameplay, using the Epic 1/2 bots only as a driver. A test sets a game up quickly (teams, phase, short timers, tools, player placement), drives scripted bots through real client inputs, asserts Vi6's behaviour, and resets the game so tests don't leak into each other. The epic opens with a phase-duration seam (durations are fixed in field initializers today), then the test hooks, then one representative tool (the Portable Teleporter) and the phase machine / KoTH / scoring driven end to end. It matters because it proves the whole path (hooks, bot inputs, handlers, observable effect) before any per-tool tests are written, and Epic 4's unattended run reuses the short timers and the reset logic.

## Stories

- Story 3.1: Phase durations can be shortened without changing the game for humans
- Story 3.2: Vi6 test hooks set a game up and reset it between tests
- Story 3.3: Bots drive a representative tool, the Portable Teleporter
- Story 3.4: Bots drive the phase machine, KoTH capture and scoring

## Requirements & Constraints

- **Test hooks belong to Vi6.** Forcing teams and phase, shortening timers, granting tools, placing players and resetting the game are Vi6 code. They depend only on Vi6 and the bot API, carry no bot concept of their own beyond driving bots, and are never called by a brain.
- **Human defaults unchanged.** With no override, every phase duration matches `config.yml` exactly, and `Vi6ConfigIT` stays green.
- **Hooks are public methods in a test-support class** (not commands); this settles the spine's open "commands vs public methods" question for tests.
- **Reset is complete and reusable:** every bot removed through the roster, game back in the lobby with empty teams, no leftover scoreboard entries, timer and KoTH overrides cleared, no bot player data on disk. The reset logic lives in Vi6's main source so the Epic 4 run command can reuse it unchanged.
- **Tools come from a hook, not the shop:** the shop is an inventory menu and inventory-click is a deferred bot control, so tests give a bot the tool's registered stack directly. `Usable` overrides need the registered stack in hand.
- **Determinism (NFR10):** every scripted-bot test gives the same result on 3 consecutive runs and passes in either order. KoTH spawning must be forceable (or its random source injectable) so KoTH tests are deterministic.
- **Real handlers only:** bots act through real serverbound packets; no fabricated Bukkit events, no direct position or slot calls. A server-side teleport must be acked by the bot like a client. Block clicks must hit a real block within reach.
- **One representative tool only.** Per-tool tests for the other tools come later, on the user's call.
- **SPEC assumption to confirm:** Vi6 outbound packet sends (the tool under test, or `PantsHandler` / `TrapWireHandler`) reach a bot's `send()` with no exception.

## Technical Decisions

- Tests live in `src/integrationTest/java` and run on the in-process Paper server (paper-junit-server). Vi6 tests test Vi6; bot-library behaviour is tested in NekotineCore, never here.
- Vi6 bot code lives in `fr.nekotine.vi6clean.bot`; no Vi6 game-logic file checks whether a player is a bot.
- `PhaseMachine.isRunning()` stays false after the first `goTo` (a deferred NekotineCore bug): check the phase with `getPhase(...)`, as `Vi6SmokeIT` does.
- `test-bundle/server.properties` uses `spawn-protection=0` so non-op bots can click near spawn.
- Infiltration's lost timer ends a round in which thieves never steal or move, which is what makes short-timer full-round tests possible without navigation or melee.

## Cross-Story Dependencies

- 3.1 (duration seam) feeds 3.2 (the reset clears timer overrides) and 3.4 (full rounds on short timers).
- 3.2 (hooks and reset) is used by 3.3 and 3.4.
- Epic 4 reuses the short timers, hooks and the main-source reset logic for its unattended run and soak test.
- Builds on Epic 2: bot add/remove through the roster, and bot brains that send `/game team` and `/game ready`.
