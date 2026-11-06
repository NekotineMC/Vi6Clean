# Test coverage boundary

What a packet-driven bot run can verify in Vi6Clean, what each input reaches, and what the run needs from the server. Source line numbers are as of commit `3f482a7` and drift.

## Harness

Vi6Clean has an integration suite (`src/integrationTest`, run with `gradlew integrationTest`, never part of `build`). The suite boots one real Paper server in the test JVM per run through paper-junit-server, staged from the local `test-bundle/` (usage in `paper-junit-server/README.md`, internals and plan B in `paper-junit-server/DESIGN.md`).
- **Typed access:** tests use Vi6 and Core types directly, do game work through `PaperServer.onMain`, and wait with `awaitTicks`/`awaitUntil`.
- **Existing tests:** `Vi6SmokeIT`, `Vi6MapIT` and `Vi6ConfigIT`, which need no players.
- **Not built yet:** forcing teams and phase, short timers, and resetting the game between tests (phase, teams, scoreboard). These wait for bots.

Tests of the bots module itself live in NekotineCore, through a tiny host plugin on NekotineCore's own paper-junit-server submodule, and never depend on Vi6. Vi6's suite tests Vi6 and uses bots only as a driver. A bot run checked by assertions belongs in one of those suites. Runs that need a real player stay on the **booted dev server**, driven by console command and checked by reading the log.

## Input → event → Vi6 consumers

| Bot input | Bukkit event | Vi6 consumers |
|---|---|---|
| Right-click (`handleUseItemOn`, `handleUseItem`) | `PlayerInteractEvent` `RIGHT_CLICK_*` | 13 `INTERACT_ANY` handlers, `Majordom:75`, `Vi6PhaseInMap:500`, `SculkSensorHandler:96`, `OmniCaptorHandler:123`, `ShadowHandler:122` |
| Left-click (`handlePlayerAction` `START_DESTROY_BLOCK`) | `PlayerInteractEvent` `LEFT_CLICK_*` | 10 `HIT_ANY` handlers, `Vi6PhaseInMap:453` |
| Sneak (`handlePlayerInput`) | `PlayerToggleSneakEvent`, `PHYSICAL` interacts | `SculkSensorHandler:75`, `InviSneakHandler`, `PortableTeleporterHandler:95` |
| Sprint (`handlePlayerInput`) | `PlayerToggleSprintEvent` | `AsthmaStatusFlag:125` |
| Move (`handleMovePlayer`) | `PlayerMoveEvent` | 14 handlers, incl. `Vi6PhaseInMap:360`, `TazedStatusFlag:64`, `ScoutHandler:135`, those gated on `hasChangedPosition()` / `hasExplicitlyChangedPosition()` |
| Held slot (`handleSetCarriedItem`) | `PlayerItemHeldEvent` | `Vi6Game:333`, `TrapWireHandler`, `ItemChargeModule`, `BowChargeModule` |
| Command (`handleChatCommand`) | `PlayerCommandPreprocessEvent`, then the command | `/game team`, `/game ready` (`Vi6Commands`) |

All 26 `PlayerInteractEvent` handlers take the base event type, so right- and left-click cover them. Further input rules:

- `getClickedBlock()` is read only by `Majordom:75`, `Vi6PhaseInMap:500` and `SculkSensorHandler:75`: block clicks must hit a real block within reach.
- The two `Usable` overrides (`Vi6PhaseLobby:77`, `Vi6PhasePreparation:159`) match `getItemStack().equals(e.getItem())`: the bot must hold the registered stack, not just the same material.
- `Vi6PhasePreparation:281` is the only `PlayerInteractAtEntityEvent` consumer and needs a registered minimap ArmorStand; entity interaction is out of the first scope.

## Covered by a bot run

Team assignment and balance, the phase machine (`Vi6PhaseLobby` → `Preparation` → `InMap` → `Infiltration`), win conditions and scoring (`Vi6PhaseInMap:527-608`), KoTH capture, tool activation, status effects, map loading and tool bags. Fake players are in the player list, so `isOnline()` pruning (`SuffocatingStatusFlag:72`, `TrapWireHandler:207,366`) and `Bukkit.getPlayer(uuid)` (`NekotineCore` `Charge.java:111,129,141`) behave as for humans.

## Not covered

Dialogs: bots neither read nor answer them (`LobbyPhasePlayerWrapper:112-122`, `Vi6Game:345` stay human-only). What a human *sees*: dialog rendering, resource-pack application, ProtocolLib visuals and glowing as a viewer, voice chat (already inert). Clientbound packets reach the bot's `send()`, but no client renders them.

## Server settings a bot run needs

- `online-mode=false` is not required by NMS bots but is already set.
- `max-players=20` is enough for a 3v3, the largest realistic game.
- `spawn-protection=0`, or non-op bots cannot click blocks near spawn. `test-bundle/server.properties` has 16 today.

## Demonstration

1. Restart the dev server; confirm the bots self-test, run when Vi6 enables, passes in the log.
2. Add 4 bots for a 2v2 with no human connected; repeat with 6 bots for a 3v3.
3. Bots pick their teams and ready up through commands; the lobby advances.
4. The round plays through preparation, in-map and infiltration to a win condition.
5. The log records the result and no exception.

Then run the fill case in front of a real player: 1-3 bots complete a 2v2.
