# Deferred work

- source_spec: `_bmad-output/implementation-artifacts/spec-test-framework-spike.md`
  summary: Vi6Main.onDisable throws an NPE (`BukkitTask.isCancelled()` on a null task) when `UpdateFolderRestart` failed to start because `plugins/update/` does not exist.
  evidence: Seen on every spike shutdown (`build/spike-logs/`); the staged server has no `plugins/update/`, `UpdateFolderRestart` logs `NoSuchFileException: plugins\update`, then `close()` dereferences the missing task. Pre-existing in Vi6/NekotineCore, not caused by the spike.

- source_spec: `_bmad-output/specs/spec-testing/stories/1-paper-junit-server-harness-skeleton.md`
  summary: paper-junit-server leaves a half-booted server unstopped when PaperServer.start() fails after launch (boot timeout or agent-unpatched check).
  evidence: maybe-false (medium if true). started stays false, so stop() does nothing. The worker's System.exit then runs Paper's shutdown hook, where an unpatched System.exit inside the hook may deadlock the worker. Settle it by booting with the agent patch disabled and watching whether the Gradle worker exits.
- source_spec: `_bmad-output/specs/spec-testing/stories/1-paper-junit-server-harness-skeleton.md`
  summary: Vi6Clean AGENTS.md does not mention the paper-junit-server submodule (read its AGENTS.md before editing, commit the gitlink with changes, the paperJunitServer.bundle property, integrationTest separate from build).
  evidence: Root AGENTS.md lists only NekotineCore as a submodule and says no tests exist; update it with story 2, when includeBuild and the suites land.
- source_spec: `_bmad-output/specs/spec-testing/stories/1-paper-junit-server-harness-skeleton.md`
  summary: No check covers paper-junit-server staging (rcon/query rewrite, plugins/update and session.lock excluded, stale target jar excluded).
  evidence: A boot smoke test passes even if these regress. Add a unit test for rewriteProperty and the exclude rules, or a story-2 check on build/it-server/ after preparePaperTestServer.
- source_spec: `_bmad-output/specs/spec-testing/stories/1-paper-junit-server-harness-skeleton.md`
  summary: No test runs the shutdown=hook fallback that the harness recommends when the agent breaks.
  evidence: All planned suites use agent mode; run the story-2 suite once with shutdown=hook and confirm the results reach the Gradle report.
- source_spec: `_bmad-output/specs/spec-testing/stories/1-paper-junit-server-harness-skeleton.md`
  summary: The no-exit agent's fail-on-upgrade guard is not exercised by any test.
  evidence: A unit test in agent/ that builds a synthetic DedicatedServer class with the ClassFile API (onServerExit with and without System.exit) would pin both the patched flag and stack balance without a server.
- source_spec: `_bmad-output/specs/spec-testing/stories/2-wire-vi6-to-paper-junit-server.md`
  summary: A standalone gradlew build fails spotlessJavaCheck on existing formatting drift in FlagBasedStatusEffectType.java and HotPotatoHandler.java.
  evidence: Both files are unchanged from the base commit, so this already fails before story 2; only spotlessApply build is green. Fix the drift in its own commit (or record it as a pitfall in AGENTS.md) before relying on build as the PR gate.
- source_spec: `_bmad-output/specs/spec-testing/stories/2-wire-vi6-to-paper-junit-server.md`
  summary: NekotineCore/AGENTS.md line 18 still says Vi6 relocates fr.nekotine.core to fr.nekotine.vi6clean.nekotinecore.
  evidence: Story 2 dropped that relocation; the fix belongs in a NekotineCore PR (to dev), then a Vi6 gitlink bump.
- source_spec: `_bmad-output/specs/spec-testing/stories/2-wire-vi6-to-paper-junit-server.md`
  summary: NekotineCore PhaseMachine.isRunning() stays false after the first goTo, because the currentPhase == null branch returns without setting running = true.
  evidence: Seen in story 2 review fixes: isRunning() was false on a healthy server after Vi6Game.start(); PhaseMachine.goTo lines 51-63. Vi6SmokeIT checks getPhase(Vi6PhaseLobby.class) instead. Fix it in a NekotineCore PR to dev, then switch the smoke test back to isRunning().
