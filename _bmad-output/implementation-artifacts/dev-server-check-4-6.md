# Story 4.6 dev-server check: unattended runs

Date: 2026-11-21. Summary of the dev server log, no raw log committed (AGENTS.md).

## Setup

- Jar: built from the Story 4.6 working tree on top of `bot` at `df42915e6befa81dc1ce142546e9394766e0ce1b`, with the lobby ready delay of this story. Deployed through `plugins/update/`; the server restarted on it at 13:58.
- Server: Paper `26.2-134` (`26.2.build.134-stable`), offline mode, `override-config: true`.
- Real timers from the jar's `config.yml`: preparation 300 s, infiltration lost timer 240 s, `bots.lobby-ready-delay-seconds` 10. KoTHs as configured, not forced.
- Boot: `Bots self-test passed: 260 accessors in 6 classes`, Vi6Clean enabled, `Done (13.757s)`, no ERROR or exception line.
- No human online: `list` before each run showed only `Bot_N` players (0 players before the first run).
- The runs were sent over RCON by a script that waited for each run's `Game ended: game=1` line and sent the next `bot run` at once, inside the 10 s lobby window.

## Runs

| # | Command | Requested / added | Phases | Result line | Exceptions / WARN / ERROR |
|---|---------|-------------------|--------|-------------|---------------------------|
| 1 | `bot run 4 42` | 4 / 4 | lobby 10 s, in-map + preparation, infiltration (same second), lobby | `game=1, reason=lost-timer, stolen=0, escaped=0, duration=255s` | none |
| 2 | `bot run 6 7` | 6 / 6 | same | `game=1, reason=lost-timer, stolen=0, escaped=0, duration=254s` | none |
| 3 | `bot run 4 1234` | 4 / 4 | same | `game=1, reason=lost-timer, stolen=0, escaped=0, duration=254s` | none |

- Clean start: before runs 2 and 3, the previous run's bots all left the game (roster removal) before the new ones joined, in the same second as `bot run`; each new run logged `Run started` with every requested bot added.
- No player data, stats or advancement file was written in the worlds folder during the runs.
- Duration: 10 s lobby delay + about 1 s for preparation (every bot readies up at once) + 240 s lost timer + end spectator time.
- KoTHs ran with the real config: each game's in-map phase spawned them (run 1: `archives` emp; run 2: `livraison` emp; run 3: `processeur` light and `archives` regen), with no error.
- The self-test passed on Paper `26.2.build.134-stable`, a newer build than the `26.2.build.65-beta` dev bundle the plugin is compiled and integration-tested against.
- After run 3, `bot remove all` removed the 4 bots, so no run keeps looping on the server, which stays up.

## Finding fixed in this story

A first attempt on the jar of `df42915` (before the delay) reached the same result for run 1 (`duration=245s`, no exception), but the run loops and its lobby brains readied up in the same tick, so the lobby lasted 1-2 ticks and `bot run 6` could not be sent from the console. `LobbyBrain` now waits `bots.lobby-ready-delay-seconds` (default 10) before `/game ready`.

## Not covered

- Idle bots only: no artefact stolen, no escape, no PvP (Epics 5 and 6).
- Each run was checked for its first game only; later games of a looping run are covered by `Vi6BotRunIT`.
- Not exercised on the dev server, covered by tests only: an invalid `lobby-ready-delay-seconds` falling back to 10 s (`PhaseDurationValidationTest`), and bots staying unready during the window (`Vi6BotRunIT.aRunStartsInTheLobbyWindowOfALoopingRun`). The window was used by a script; a human typing at the console has the same 10 s.
