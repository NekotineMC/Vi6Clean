# Deferred work

- source_spec: `_bmad-output/specs/spec-testing/SPEC.md`
  summary: Vi6Main.onDisable throws an NPE (`BukkitTask.isCancelled()` on a null task) when `UpdateFolderRestart` failed to start because `plugins/update/` does not exist.
  evidence: Seen on every spike shutdown (`build/spike-logs/`); the staged server has no `plugins/update/`, `UpdateFolderRestart` logs `NoSuchFileException: plugins\update`, then `close()` dereferences the missing task. Pre-existing in Vi6/NekotineCore, not caused by the spike.
- source_spec: `_bmad-output/specs/spec-testing/SPEC.md`
  summary: paper-junit-server leaves a half-booted server unstopped when PaperServer.start() fails after launch (boot timeout or agent-unpatched check).
  evidence: maybe-false (medium if true). started stays false, so stop() does nothing. The worker's System.exit then runs Paper's shutdown hook, where an unpatched System.exit inside the hook may deadlock the worker. Settle it by booting with the agent patch disabled and watching whether the Gradle worker exits.
- source_spec: `_bmad-output/specs/spec-testing/SPEC.md`
  summary: No check covers paper-junit-server staging (rcon/query rewrite, plugins/update and session.lock excluded, stale target jar excluded).
  evidence: A boot smoke test passes even if these regress. Add a unit test for rewriteProperty and the exclude rules, or a story-2 check on build/it-server/ after preparePaperTestServer.
- source_spec: `_bmad-output/specs/spec-testing/SPEC.md`
  summary: No test runs the shutdown=hook fallback that the harness recommends when the agent breaks.
  evidence: All planned suites use agent mode; run the story-2 suite once with shutdown=hook and confirm the results reach the Gradle report.
- source_spec: `_bmad-output/specs/spec-testing/SPEC.md`
  summary: The no-exit agent's fail-on-upgrade guard is not exercised by any test.
  evidence: A unit test in agent/ that builds a synthetic DedicatedServer class with the ClassFile API (onServerExit with and without System.exit) would pin both the patched flag and stack balance without a server.
- source_spec: `_bmad-output/specs/spec-testing/SPEC.md`
  summary: NekotineCore/AGENTS.md line 18 still says Vi6 relocates fr.nekotine.core to fr.nekotine.vi6clean.nekotinecore.
  evidence: Story 2 dropped that relocation; the fix belongs in a NekotineCore PR (to dev), then a Vi6 gitlink bump.
- source_spec: `_bmad-output/specs/spec-testing/SPEC.md`
  summary: NekotineCore PhaseMachine.isRunning() stays false after the first goTo, because the currentPhase == null branch returns without setting running = true.
  evidence: Seen in story 2 review fixes: isRunning() was false on a healthy server after Vi6Game.start(); PhaseMachine.goTo lines 51-63. Vi6SmokeIT checks getPhase(Vi6PhaseLobby.class) instead. Fix it in a NekotineCore PR to dev, then switch the smoke test back to isRunning().
- source_spec: `_bmad-output/specs/spec-testing/SPEC.md`
  summary: Tool configs are overwritten at boot even when config.yml says override-config false.
  evidence: Vi6Main.onEnable calls ConfigManager.setupAsync() (runTaskAsynchronously) and then immediately builds every ToolHandler, whose constructor reads ConfigManager.getConfig().override_config() — still the in-memory default true. Fix by loading config.yml synchronously before discoverHandlers(), or by building handlers after the load; then a test can check tools with override false.
- source_spec: `_bmad-output/specs/spec-testing/SPEC.md`
  summary: No test observes the boot-time override of config.yml and tools/*.yml.
  evidence: Both load once at boot, and the local test-bundle holds default values, so removing the override branch keeps Vi6ConfigIT green. Needs a stale-value fixture staged before boot (for example a harness bundle overlay); koths are covered in-process.
