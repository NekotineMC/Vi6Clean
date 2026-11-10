---
title: 'Story 2.1: A bot joins the Vi6 lobby like a player'
type: 'feature'
created: '2026-11-10'
status: 'done'
route: 'oneshot'
baseline_commit: 'd024c645d6fbcdba587b074f8cf1ac5736b084e1'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/human-coding-style.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Vi6Clean shades the bots library but never resolves `BotRoster`, so its self-test never runs and no bot can exist on a Vi6 server. Nothing proves that Vi6's normal join and quit paths accept a bot unchanged (FR4, FR5, AD-3, AD-12).

**Approach:** `Vi6Main.onEnable` resolves `BotRoster` eagerly right after `super.onEnable()`, in its own `try/catch` so a throw logs in French and the rest of enable still runs (the roster logs the self-test result itself and turns bots off on failure). A new integration test spawns a bot in the lobby, checks that it joins the game through `Vi6PhaseLobby.onPlayerJoined` with no listener exception, removes it through the roster, and checks that `Vi6PhaseGlobal.onPlayerLeft` takes it off the player list, the teams and the scoreboards. `test-bundle/README.md` requires `spawn-protection=0`, and the local `test-bundle/server.properties` is set to it. No game-logic file checks whether a player is a bot.

</frozen-after-approval>

## Implementation Notes

- Branch `feat/bots-2-1-lobby-join` from local `bot` (`d024c64`); PR to `bot`. No NekotineCore change, so no gitlink.
- Investigation: `BotRoster()` never throws on a failed self-test (it logs and sets `isAvailable()` false; later calls throw `BotsUnavailableException("Bots unavailable: …")`), so "bots unavailable" reporting is the library's until 2.2 adds commands. The lobby dialog is only opened from the beacon item, not on join; join runs `Vi6PhaseLobby.onPlayerJoined` → `Vi6Game.addPlayer` → lobby `itemSetup` (wrapper, sidebar score, beacon, teleport) and Core's `ResourcePackModule.onPlayerJoined`, which sends nothing until the async GitHub fetch in `Vi6ResourcePack.setup` has added a pack. The test adds a pack to `ResourcePackModule` itself so the send path runs offline. "Scoreboards" are the Garde/Voleur teams of `Vi6Game.getScoreboard()` and the lobby `playerListing` sidebar.
- Changed: `Vi6Main.onEnable` (eager `BotRoster` resolve in its own try/catch; null result logged, since `ModuleManager` swallows constructor errors), `test-bundle/README.md` (`spawn-protection=0` after copying), new `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6BotLobbyIT.java`. The local `test-bundle/server.properties` (gitignored, not in the PR) is now `spawn-protection=0`.
- `selfTestPassedAtEnable` reads `latest.log` and requires the pass line before the server's "Done (" line; a lazy resolve from a test would log it after. Mutation check: with the resolve disabled the test fails.
- AD-12: the only `src/main/java` reference to `fr.nekotine.core.bot` is the resolve in `Vi6Main`; no file checks whether a player is a bot.
- Verified (2026-11-10): bare `gradlew -PVi6CleanJarOutputPath=build/dist`, `gradlew build integrationTest` green, 12 tests. Server log: "Bots self-test passed: 260 accessors in 6 classes" before "Done"; `Bot_Lobby` joins, is teleported to `solar_industries`, leaves; no listener exception. No lockfile changes.

## Review Triage Log

Review pass 1 (2026-11-10), Blind Hunter (B).

| # | Finding | Verdict | Evidence | Route |
|---|---------|---------|----------|-------|
| B1 | Self-test test passes without the eager resolve | medium | The test resolved the roster itself; now checks the pass line precedes "Done", mutation-checked | patch |
| B2 | Vi6Main catch is dead for constructor errors; resolve returns cached null | medium | `ModuleManager.load` catches `Exception`; added null check, 2.2 follow-up deferred | patch + defer |
| B3 | A `LinkageError` aborts the whole enable | low | Constructor errors arrive wrapped by reflection; static-init errors do not; catch widened to `Exception \| LinkageError` | patch |
| B4 | Unavailable-bots path untested; cleanup would throw on it | medium (unverified) | Cleanup now skips an unavailable or null roster; the test itself needs fault injection | patch + defer |
| B5 | Test pack leaks into other tests | low | No removal API in `ResourcePackModule`; bots ignore packs and no other IT joins a player | reject |
| B6 | AD-12 not enforced automatically | low | Idea for the coming bot package | defer |
| B7 | Bots quit after `game.close()` at shutdown | maybe-false | Pre-existing order in `onDisable`; not reached by this story's tests | defer |
| B8 | README now silently differs from production | low | Reworded as a test-only override applied after copying | patch |
| B9 | Teleport and pack send not asserted | low | Teleport assertion added; asserting the pack packet needs a ProtocolLib adapter for a path that only must not throw | patch / reject |
| B10 | LogCapture may capture nothing | low | Added a sanity check logging a known exception through the Bukkit logger | patch |
| B11 | Spec lacks a verification record | low | Notes above | patch |
