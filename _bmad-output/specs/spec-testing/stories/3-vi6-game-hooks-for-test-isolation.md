---
title: 'Map forcing and configuration reading tests'
type: 'feature'
created: '2026-11-03'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'de92899894c4faaad38648070d65c2a3f3d78e2d'
context:
  - '{project-root}/_bmad-output/specs/spec-testing/SPEC.md'
  - '{project-root}/paper-junit-server/README.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The only integration test is a smoke test. Nothing checks that a test can choose the map, or that Vi6 reads its three kinds of configuration correctly: `config.yml` (Configurate), `tools/<code>.yml` and `koths/<code>.yml` (Bukkit YAML).

**Approach:** Add integration tests for the two map behaviours and for configuration reading. They use only Vi6's public API and need no players.

**Decision (scope):** The user narrowed story 3 to `forcesEachMap`, `unknownMapFails` and configuration reading. Phase forcing, short timers, reset between tests, order tests and team forcing are dropped for now, to be specified again once NekotineBots exists.

## Boundaries & Constraints

**Always:** Game and plugin access goes through `PaperServer.onMain`. A test that changes the map puts the default map back afterwards. Assertion messages name the tool, koth or key that failed. Run named Gradle tasks only.

**Never:**
- Changes under `src/main`, `NekotineCore/` or `paper-junit-server/`.
- Phase, timer, team or reset helpers.
- Writing to `test-bundle/`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| forcesEachMap | each name from `MapModule.listMaps()` | `getMapName()` equals it, and `getWorld()` key equals that map's `worldName` | — |
| unknownMapFails | `setMapName("NoSuchMap")` | throws | message contains `NoSuchMap`; the current map is unchanged |
| Game config | live `ConfigManager.getConfig()` | same values as `new Vi6Config()`, because `override-config: true` | — |
| Config file | `plugins/Vi6Clean/config.yml` on the staged server | exists, uses kebab-case keys (`override-config`, `duration-seconds`), and reloads to the same values as the live config | — |
| Tool configs | every handler in `ToolHandlerContainer` | `tools/<code>.yml` is in the jar, a server copy exists, and every jar key has the jar value | message names tool and key |
| Koth configs | `new EmpKothEffect()`, `LightKothEffect`, `RegenKothEffect` | `koths/<code>.yml` is in the jar, a server copy exists, and every jar key has the jar value | message names koth and key |

</frozen-after-approval>

## Code Map

- `game/Vi6Game.java` `setMapName(String)`: throws `NullPointerException` (no metadata) or `RuntimeException` (world missing). It sets `mapName`/`world` only after both checks pass. The default map is chosen in the constructor; read `getMapName()` before the test and restore it after.
- `test-bundle/plugins/Vi6Clean/Maps/`: Market, SlimeFactory and SolarIndustries. Their `Vi6Map.yml` `worldName` values (`vi6clean:market`, …) exist under `worlds/world/dimensions/vi6clean/`. Read them through `IMapModule.getContent(metadata, Vi6Map.class).getWorldName()`.
- `configuration/ConfigManager.java`:
  - `getConfig()`; config is loaded asynchronously by `setupAsync()`, so wait 40 ticks before the first config assertion.
  - Its loader is `YamlConfigurationLoader` on `getDataPath().resolve("config.yml")`, with `NodeStyle.BLOCK`.
  - Compare objects by serializing both into Configurate nodes and comparing `raw()`.
- `tool/ToolHandler.java`: the constructor loads `tools/<code>.yml` with `overrideAndLoadYaml` when `override_config()` is true, so values equal the jar's. `getConfiguration()`, `getToolCode()`. Handlers come from `Ioc.resolve(ToolHandlerContainer.class).getHandlers()`.
- `map/koth/AbstractKothEffect.java`: since `de92899` it reads `ConfigManager.getConfig().override_config()`, as tools do, so with `override-config: true` its values equal the jar's. Effects are instantiated directly (`Vi6PhaseInMap` does `new EmpKothEffect()` …); construct them the same way on the main thread.
- `NekotineCore/.../ConfigurationUtil.java`: both loaders write the server copy under `getDataFolder()` and read the jar copy through `JavaPlugin.getResource(path)`.

## Tasks & Acceptance

**Execution:**
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6MapIT.java` -- `forcesEachMap` and `unknownMapFails`, with `@AfterEach` restoring the default map.
- [x] `src/integrationTest/java/fr/nekotine/vi6clean/it/Vi6ConfigIT.java` -- one test per configuration row of the matrix: game config, config file, tool configs, koth configs.
- [x] `AGENTS.md` -- delete the "KoTH configs do not follow that flag" pitfall, which `de92899` made false. Extend the `override-config` pitfall to say it covers `koths/*.yml` too.

**Acceptance Criteria:**
- Given `gradlew integrationTest`, when it runs, then `Vi6MapIT`, `Vi6ConfigIT` and `Vi6SmokeIT` all pass in one server boot.
- Given `gradlew build`, when it runs, then no server boots and the build stays green.

## Implementation Notes

- Negative check (2026-11-03): with `AbstractKothEffect`'s override condition inverted temporarily, `kothOverrideReplacesStaleServerValue` failed, and so did `kothConfigsMatchJar`, which ran after it with the stale value left in place. `src/main` was restored afterwards; the suite passes 10/10 with it intact.

## Spec Change Log

## Review Triage Log

| # | Layer | Finding | Verdict | Evidence | Route |
|---|---|---|---|---|---|
| 1 | blind, edge | `gameConfigMatchesDefaults` passes even if `config.yml` is never read | medium | With override on, `setupAsync` sets `config = new Vi6Config()`, the same object the initial field holds | patch (adds a `tryLoad()` test on a modified staged file) |
| 2 | verif, blind, edge | Koth override unobserved: the test's own construction rewrites the files, and the bundle already holds jar values | medium | Reverting `de92899` keeps `kothConfigsMatchJar` green | patch (stale-value test) |
| 3 | verif | Boot-time override for `config.yml` and `tools/*.yml` unobserved | gap | These load once at boot and the bundle holds default values; needs a stale-fixture mechanism | defer, and the Javadoc is softened |
| 4 | edge (claim) | Tools overwrite their configs even with `override-config: false` | high | `Vi6Main.onEnable` calls `setupAsync()` and then builds the `ToolHandler`s, which read the in-memory default `true` before the async load; production code, outside scope | defer |
| 5 | blind | `assertTrue(Objects.equals(...))` instead of `assertEquals` | low | Getters called twice; no expected/actual output; direct correction | patch |
| 6 | blind, edge | `unknownMapFails` catches `Exception` | low | A harness timeout would pass the type check; `onMain` rethrows the cause, an NPE | patch |
| 7 | blind, edge | `@AfterEach` calls `setMapName(null)` after a failed `@BeforeEach` | low | Buries the real failure; one guard | patch |
| 8 | blind, verif-other | AGENTS.md dropped the `override-config: false` advice | low | `updateAndLoadYaml` still only adds missing keys; one clause | patch |
| 9 | blind, edge | 40-tick wait guesses when the async load ends | low | Load is a single file read; tests start after boot plus 40 ticks; the new `tryLoad()` test does its own read | reject |
| 10 | edge | `ConfigManager.config` not volatile | low | Production code, pre-existing; no observed stale read | reject |
| 11 | blind, edge | Koth list hardcoded (Emp, Light, Regen) | low | Mirrors `Vi6PhaseInMap`; listing jar resources adds complexity | reject |
| 12 | blind | One-direction key comparison | low | Override saves the jar config verbatim, so extra keys can't survive | reject |
| 13 | blind | Mixed `getDataPath`/`getDataFolder`; plugin resolved per tool | low | Cosmetic | reject |
| 14 | blind, edge | `forcesEachMap` messages for a missing world or null content | low | `setMapName`'s message already names the world; the bundle has all three | reject |
| 15 | blind | Story files missing from the diff; no production hooks | false | Excluded from the review diff on purpose; scope renegotiated by the user | reject |

## Verification

**Commands:**
- `gradlew.bat integrationTest` -- expected: BUILD SUCCESSFUL; the three classes passed in one boot
- `gradlew.bat build` -- expected: BUILD SUCCESSFUL, no server boot
