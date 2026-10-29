# Hand-off — Vi6Clean test framework (2026-10-29)

Read this first, then `_bmad-output/specs/spec-testing/SPEC.md` (the contract) and its companions `harness-design.md` (how) and `spike-findings.md` (evidence).

## Where we are

- **Spike done, verdict GO.** A real Paper 26.2 server runs inside Gradle's test worker. All six spike questions are answered, so the child-process fallback isn't needed.
- **Spec written and committed, not reviewed.** It has 8 capabilities, 0 open questions and 4 assumptions, and both validation passes pass.
- **Nothing is pushed.** No PR is open, and no harness code exists outside the throwaway spike.

## Branches

| Branch | Head | What | Fate |
|---|---|---|---|
| `docs/testing-spec` | `a7e0594` | Spec folder `_bmad-output/specs/spec-testing/` (SPEC, harness-design, spike-findings, `.memlog.md`) + `implementation-artifacts/deferred-work.md` + this file | Push → PR to `main` |
| `spike/test-framework` | `3c22c45` | Throwaway spike code + spike spec `implementation-artifacts/spec-test-framework-spike.md` | **Never merge** (changes the production `shadowJar` relocation and `gradle.lockfile`). Keep as a reference, delete once the real harness exists |

## Decisions made with the user

1. Harness = standalone library **paper-junit-server**, not Nekotine-branded. Maven group `io.github.xxgoldenbluexx`, package `io.github.xxgoldenbluexx.paperjunitserver`. It lives in its own repository under GitHub user XxGoldenbluexX; the user creates it.
2. Consumers (Vi6, then NekotineBots) pull it in as a **git submodule + `includeBuild`**, like NekotineCore. It is **not** in NekotineCore, because Core has no paperweight. Publishing (artifact or Gradle plugin) is deferred.
3. The **bundle** (world, maps, plugin jars, configs) and its location setting are the user's. The user keeps it in line with production, and the build has no default location.
4. **CAP-1** (plain `test` suite) only has to make plain tests possible; no check is needed now. Tool and KoTH configs use the Bukkit API, so they belong to the integration suite. Database access is a later example of a plain test.
5. Exit handling: **no-exit `-javaagent`** as the primary approach; the shutdown-hook hold (`-Pvi6ItShutdown=hook` in the spike) is the documented no-agent fallback. JEP 486 offers no supported veto.
6. Drop the `fr.nekotine.core` relocation and relocate CommandAPI to `fr.nekotine.core.commandapi`. Without that, `Vi6Main.onLoad` throws `NoClassDefFoundError`.

## Next steps (suggested order)

1. Push `docs/testing-spec`, open the PR to `main`, and get it reviewed (a human merges).
2. User: create the `paper-junit-server` repository and set up the bundle folder.
3. Break the spec into stories: `bmad-spec` → "break this into stories" (writes `spec-testing/stories.yaml`). Natural slices:
   - harness repo skeleton (fixture, `onMain` and waits, session listener, agent with self-check, Gradle wiring);
   - Vi6 wiring (submodule, `integrationTest` suite, relocation change, `test` suite);
   - Vi6 game hooks (force map, teams, phase; reset; short timers) — CAP-6;
   - bot-driven tests — CAP-7, which waits on NekotineBots;
   - NekotineBots adoption — CAP-8.
4. Implement each story with `bmad-build`. For Vi6 PRs: run bare `gradlew`, then `gradlew build`, before opening the PR. Commit the submodule pin in the same PR.
5. Afterwards:
   - update `spec-bots` (its lines "no JVM test suite" and "verification is RCON + log");
   - refresh the stale relocation line in `NekotineCore/AGENTS.md`;
   - fix the deferred `onDisable` NPE (`deferred-work.md`).

## Unconfirmed or pending

- Assumption not confirmed by the user: a missing bundle setting fails `integrationTest` with a message naming the setting.
- CAP-7 (scripted bots) depends on NekotineBots' API (`spec-bots` CAP-7).
- The spike ran on dev bundle `26.2.build.135-stable`; `main`'s lockfile is still on `65-beta`. Lock only new configurations, deliberately.

## Pitfalls hit this session

- **Bare `gradlew` restarts the dev server:** the `out` task copies the jar into `D:\shared\MinecraftServer\Vi6Clean_latest\plugins\update`. While working on tests, use named tasks such as `spotlessApply build` or `integrationTest`.
- **`--write-locks` re-resolves everything:** it bumped the dev bundle, downgraded CommandAPI `11.2.1-SNAPSHOT` → `11.2.0`, and rewrote `NekotineCore/gradle.lockfile`. Review the lockfile diff.
- **`spotlessApply` re-wraps two unrelated files** on `main` (`FlagBasedStatusEffectType.java`, `HotPotatoHandler.java`) because of existing formatting drift. Revert them unless fixing the drift is the point.
- **Raw spike server output is in `build/spike-logs/`** (unversioned). Don't commit server logs.

## How to rerun the spike (on `spike/test-framework`)

- `gradlew integrationTest`: 9 tests; boot ~11.5 s; build ~20 s.
- `gradlew integrationTestInPlugin`: 3 Vi6-typed tests, loaded through Vi6's plugin classloader.
- Options: `-Pvi6ItShutdown=agent|hook|none`, `-Pvi6ItBundle=<dir>`; the default bundle is the dev server copy above.
