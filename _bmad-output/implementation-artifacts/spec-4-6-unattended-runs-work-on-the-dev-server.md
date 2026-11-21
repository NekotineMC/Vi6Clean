---
title: 'Story 4.6: Unattended runs work on the dev server'
type: 'feature'
created: '2026-11-21'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'df42915e6befa81dc1ce142546e9394766e0ce1b'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The unattended run (`/bot run`) is proven only in the in-process test server on short timers (Story 4.5). Nobody has yet checked that it works on the real dev server with real timers, real config and KoTHs, which is the Epic 4 gate before navigation work starts (FR13, FR14, NFR11).

**Approach:** Deploy the current `bot` jar to the dev server, restart it with no human connected, and over RCON start `bot run 4 <seed>`, then `bot run 6 <seed>`, then a third `bot run 4 <seed>`, each from the lobby after the previous run's `Game ended: game=1` line. Read the log between runs, then write the results in a Markdown note under `_bmad-output/`. No source changes unless a run fails.

## Boundaries & Constraints

**Always:** Real timers: the dev server's `config.yml` as `override-config: true` writes it from the jar (preparation 300 s, infiltration lost timer 240 s), with no duration edits. Fixed seeds, recorded in the note, so a run can be replayed. Check before each run that no human is online (`list`). For each run, record: `Run started` line, `Game ended` line (reason, stolen, escaped, duration), roster cleanup before the next run, and every WARN/ERROR/exception line between the run's start and its end. Record the self-test line from the boot.

**Never:** Commit or `git add -f` a log file, or copy player names (other than `Bot_N`), IPs or UUIDs into the note. Edit the server's config files by hand. Change game logic, brains or `/bot run` to make a run pass without stopping and reporting first.

**Decisions (human, 2026-11-21):**
- The operator starts the dev server (`START.bat`) and tells the agent when it is up; the agent builds the jar first so the server picks it up from `plugins/update/` at boot. The operator owns the process, so the agent leaves the server running at the end.
- Scope change after the first dev-server run (human, 2026-11-21): a run loops and its lobby brains ready up in the same tick, so the lobby lasts 1-2 ticks and the operator cannot type the next `bot run` between games. `LobbyBrain` keeps joining its team at once but sends `/game ready` only after a delay, new config key `bots.lobby-ready-delay-seconds` (default 10, validated like the phase durations, a bad value falls back to the default with a warning). It ships in this story; after it, the jar is rebuilt and all three runs are redone on it. Integration tests set the delay short through the config seam so they stay fast.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Boot | New jar in `plugins/update/`, server started | `Bots self-test passed` line, Vi6Clean enabled, no exception | Stop; note it, report |
| 4-bot run | Lobby, no human, `bot run 4 42` | `Run started: requested=4, added=4`, then `Game ended: game=1, …` within about 10 min, no exception | Stop; note the lines, report |
| 6-bot run | Lobby after a `Game ended` line, `bot run 6 7` | First run's bots removed; `added=6`; `Game ended: game=1` | Same |
| Third run | Lobby, `bot run 4 1234` | Starts clean (`added=4`, no leftover bot in `list`), reaches `Game ended` | Same |
| Human online | `list` shows a non-bot player | Do not start the run | Ask the operator |
| Lobby window | A run's game ends, lobby current | Bots join their team at once and ready up about `lobby-ready-delay-seconds` later; `bot run` sent in that window is accepted | N/A |
| Bad delay | `lobby-ready-delay-seconds: -1` (or NaN, 0) | Default 10 s used, one warning naming the key | N/A |

</frozen-after-approval>

## Code Map

- `~/.gradle/gradle.properties` -- `Vi6CleanJarOutputPath` = `D:\shared\MinecraftServer\Vi6Clean_latest\plugins\update`: bare `gradlew.bat` drops the jar there. The server is down now (no java process), so the jar is applied when the server next starts. `plugins/update/` holds a Nov 20 jar today; `gradlew.bat` overwrites it.
- `D:\shared\MinecraftServer\Vi6Clean_latest\START.bat` -- `java -Xms3G -Xmx3G -jar paper-26.2-134.jar -nogui`; `spigot.yml` has `restart-script: START.bat`.
- `.claude/mcp/minecraft-rcon/` -- `execute_command` (use `announce: false`), `read_logs` (`fromLine`, `grep`), `log_size` as a bookmark before each run.
- `src/main/java/fr/nekotine/vi6clean/bot/Vi6BotCommands.java:64-109` -- `bot run <4|6> [seed]`: refused outside the lobby; resets bots via `Vi6GameReset.clearBots()`, seeds `Vi6Bots`, logs through `Vi6Run.begin`, then `Vi6Game.start()`.
- `src/main/java/fr/nekotine/vi6clean/bot/Vi6Run.java:92` -- `Game ended: game=N, reason=…, stolen=…, escaped=…, duration=…s`, logged in the lobby the tick after the round ends.
- `NekotineCore/bots/.../BotRoster.java` -- logs `Bots self-test passed: …` at enable.
- `_bmad-output/implementation-artifacts/spec-4-5-…md` -- the soak test's checks, mirrored here by hand.
- `src/main/java/fr/nekotine/vi6clean/bot/LobbyBrain.java` -- acts once per lobby, keyed by the bot's `LobbyPhasePlayerWrapper` (`actedIn`); sends `game team` then `game ready` in the same tick. Keep the team pick at once; count ticks from the brain's first tick in that lobby and send `game ready` once the delay has passed. Constructed only in `PhaseBrain.java:28`. Read the delay from `ConfigManager` when the lobby is first seen.
- `src/main/java/fr/nekotine/vi6clean/configuration/Vi6Config.java` -- top-level Configurate record (kebab-case keys, `@Comment` in French); add a `bots` section (new `BotsConfig`, like `PreparationPhaseConfig`: `double` seconds field, `Duration` getter/setter using `DurationRules.checkedSeconds`).
- `src/main/java/fr/nekotine/vi6clean/configuration/DurationRules.java:77` -- `resetInvalidDurations` walks the sections and calls each one's `resetInvalidDurations(path, warnings)` with `orDefault`; add the `bots` section there. `ConfigManager.java:45` logs the warnings. `DurationSnapshot` (phase durations only) stays as is.
- `src/test/java/...` -- plain JUnit config validation tests already exist for durations (find them with `grep -rl resetInvalidDurations src/test`); add the bad-delay cases next to them.
- `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6TestHooks.java:256` -- `shortTimersWithoutKoths()` sets short durations through the config seam; it also sets a short ready delay. The server boots once for all ITs and config changes persist across classes, so every IT whose bots go through a lobby by their brains (`Vi6BotBrainIT`, `Vi6BotLobbyIT`, `Vi6BotMixedLobbyIT`, `Vi6PhaseMachineBotIT`, `Vi6PrepReadyIT`, `Vi6BotPreparationIT`, `Vi6BotRunIT`, `Vi6SoakIT`, check each) sets it in its own set-up through one hook, not relying on class order. `Vi6ConfigIT.gameConfigMatchesDefaults` compares `game()` only.

## Tasks & Acceptance

**Execution:**
- [x] `LobbyBrain.java`, `BotsConfig.java` (new), `Vi6Config.java`, `DurationRules.java` -- the ready delay per the Decisions and matrix -- gives the operator a window between games.
- [x] Plain JUnit config test -- bad-delay row: invalid values fall back to 10 s with one warning naming `bots.lobby-ready-delay-seconds`.
- [x] `Vi6TestHooks.java` and the brain-driven ITs -- one hook sets a short delay (e.g. 1 s); add an IT for the lobby-window row: in a looping run's lobby, bots are on a team but not ready for the delay, and `bot run 6 <seed>` dispatched from the console sender a few ticks into that lobby is accepted (`Run started: requested=6`).
- [x] `gradlew.bat`, `gradlew.bat build`, `gradlew.bat integrationTest` -- all green.
- [x] (orchestrator only, not the implementation agent) Build -- `gradlew.bat` then `gradlew.bat build` on `bot`; record the commit hash -- the jar under test.
- [x] (orchestrator only) Dev server -- ask the operator to start it after the build, wait for `Done`, check the self-test line and that Vi6Clean enabled with the new jar.
- [x] (orchestrator only) Runs -- the three runs from the matrix, with `list` and a `log_size` bookmark before each.
- [x] (orchestrator only) `_bmad-output/implementation-artifacts/dev-server-check-4-6.md` -- new note: date, commit, server/Paper build, config durations, per-run table (seed, requested/added, end reason, stolen/escaped, duration, exceptions), cleanup between runs, the self-test line, anything odd. No raw log.
- [x] `_bmad-output/implementation-artifacts/deferred-work.md` -- add an entry for any non-blocking oddity found, if any.

**Acceptance Criteria:**
- Given the dev server restarted with the new jar and no human connected, when a 4-bot then a 6-bot run start from the console with real timers, then each reaches a `Game ended` line and the log shows the self-test pass and no exception.
- Given both runs are done, when a third run starts, then it starts clean and reaches a result.
- Given the checks are done, then the note sums them up and `git status` shows no `*.log` staged.

## Implementation Notes

- First attempt (jar of `df42915`, before the scope change): boot clean, self-test passed (260 accessors in 6 classes); run 1 `bot run 4 42` reached `Game ended: game=1, reason=lost-timer, stolen=0, escaped=0, duration=245s` with no WARN/ERROR after the bots joined, then game 2 started 1 s later and `bot run 6` could not be sent in a lobby. Superseded by the redo on the new jar.
- Implementation (subagent): `BotsConfig` (new, top-level `bots` section), `LobbyBrain` counts ticks from its first tick in a lobby and sends `game ready` once the delay is reached. `Vi6TestHooks.shortLobbyReadyDelay()` (20 ticks) is called by `shortTimersWithoutKoths()` and in the set-up of `Vi6BotBrainIT`, `Vi6BotMixedLobbyIT` (both reload `config.yml` after each test, since they do not reset) and `Vi6PhaseDurationsIT` (installs `LobbyBrain` directly). `Vi6BotLobbyIT`, `Vi6PrepReadyIT`, `Vi6PhaseMachineBotIT` and `Vi6BotPreparationIT` need nothing: no lobby brain goes through a lobby there. New IT `Vi6BotRunIT.aRunStartsInTheLobbyWindowOfALoopingRun`. `Vi6ConfigIT.gameConfigMatchesDefaults` compares the whole `Vi6Config`, not just `game()`.
- Redo (jar of the working tree, server restarted 13:58): runs `bot run 4 42`, `bot run 6 7`, `bot run 4 1234` each reached `Game ended: game=1, reason=lost-timer, stolen=0, escaped=0` (255 s, 254 s, 254 s), no WARN/ERROR/exception; see `dev-server-check-4-6.md`. The runs were chained by a script over RCON (not committed), since the agent's reaction time can exceed the 10 s window. `bot remove all` at the end leaves the server idle.
- After review patches: `gradlew.bat spotlessApply build` green; `gradlew.bat integrationTest` green, 108 tests in 16 classes, 0 failures.

## Spec Change Log

## Review Triage Log

Review pass 1 (blind, edge-case, verification-gap layers). Verification-gap: no gaps.

| # | Layer | Finding | Verdict | Evidence | Route |
|---|-------|---------|---------|----------|-------|
| 1 | blind, edge-case | `LobbyBrain` NPEs if `config.bots()` is null | low | Only with a hand-written `bots: ~`; a missing section keeps the field default. `config.game()` is read unguarded the same way everywhere (`DurationSnapshot.of`). | reject |
| 2 | edge-case | Brain state half-updated if the config read throws | low | Same trigger as #1; the brain is dropped after the error anyway. | reject |
| 3 | blind, edge-case | `tryLoad()` in `@AfterEach` of `Vi6BotBrainIT`/`Vi6BotMixedLobbyIT` skipped if an earlier cleanup step throws | low | Only after a test already failed its cleanup; the leak then fails `Vi6ConfigIT` loudly. | reject |
| 4 | edge-case | `Vi6BotBrainIT` sets the short delay without waiting for the async config load, which could replace it | maybe-false | Five ITs wait 40 ticks for that load, this one does not; suite passed. Settled by checking whether `ConfigManager.setupAsync` can finish after the first test starts. | defer |
| 5 | blind, edge-case | Window guard measures from the tick `awaitGameEnded` noticed the line, not the lobby start | low | Undercounts lobby time, but a late check then fails loudly on the "readied before the delay" assertion. | reject |
| 6 | edge-case | `dispatch()` uses a feedback sender, not the console sender | low | `/bot run` checks only OP permission; the dev-server runs used the real RCON console. | reject |
| 7 | blind | New IT clears the log capture before any clean-log check of the first run | low | `capture.clear()` precedes the only `assertCleanLog`; one-line fix. | patch |
| 8 | blind | Lobby gap between two games of one run, with no new `bot run`, not asserted | low | The new IT asserts bots unready 3 ticks into exactly that lobby; the brain code is the same for every lobby. | reject |
| 9 | blind | Code Map says `Vi6ConfigIT` compares `game()` only | low | Fix is a spec edit; Implementation Notes already correct it. | reject |
| 10 | blind | Javadoc of `shortLobbyReadyDelay` names only `reset()` as the undo | low | Accurate for most callers; the two `tryLoad()` callers comment it inline. | reject |
| 11 | blind | Delay cannot be 0; not in `describeDurationChanges` | low | 0.05 s (1 tick) is the same as at once; the in-map line lists phase durations only, by design. | reject |
| 12 | blind | Spec `in-review` vs sprint `in-progress` | false | The sprint status moves to `review` when the story is presented; both follow the workflow. | reject |
| 13 | blind | Run chaining script not committed, procedure not replayable | low | The note says what it did (wait for the line, send the next `bot run` at once); a human can type the same commands. | reject |
| 14 | blind | 10 s may be short for a human at the console | low | The human chose 10 s (configurable); typing `bot run 6 7` takes a few seconds. | reject |
| 15 | blind | Note has no KoTH evidence | low | The log shows KoTH spawns each game. | patch (note) |
| 16 | blind | Note does not flag Paper 134 vs dev bundle 65-beta | low | Real gap, one note line. | patch (note) |
| 17 | blind | Note does not list matrix rows covered only by tests | low | One note line. | patch (note) |
| 18 | blind | `LobbyBrain` counts brain ticks, not server ticks | low | Brains tick once per server tick in the per-bot event (epic context). | reject |
| 19 | blind | `BotsConfig` `@Comment` has no range | low | Other duration comments give none either; the load warning gives the bounds. | reject |

## Verification

**Commands:**
- `gradlew.bat` then `gradlew.bat build` -- expected: BUILD SUCCESSFUL.
- `gradlew.bat integrationTest` -- expected: all ITs pass, the new lobby-window IT included.

**Manual checks (if no CLI):**
- The note's per-run table has three rows, each with a `Game ended` result and no exception; `git status` shows no log file.
