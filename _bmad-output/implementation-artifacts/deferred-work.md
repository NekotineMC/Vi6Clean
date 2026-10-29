# Deferred work

- source_spec: `_bmad-output/implementation-artifacts/spec-test-framework-spike.md`
  summary: Vi6Main.onDisable throws an NPE (`BukkitTask.isCancelled()` on a null task) when `UpdateFolderRestart` failed to start because `plugins/update/` does not exist.
  evidence: Seen on every spike shutdown (`build/spike-logs/`); the staged server has no `plugins/update/`, `UpdateFolderRestart` logs `NoSuchFileException: plugins\update`, then `close()` dereferences the missing task. Pre-existing in Vi6/NekotineCore, not caused by the spike.
