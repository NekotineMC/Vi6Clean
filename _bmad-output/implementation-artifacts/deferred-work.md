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
