# Deferred work

- source_spec: none
  summary: Vi6Main.onDisable throws an NPE (`BukkitTask.isCancelled()` on a null task) when `UpdateFolderRestart` failed to start because `plugins/update/` does not exist.
  evidence: Seen on every spike shutdown (`build/spike-logs/`); the staged server has no `plugins/update/`, `UpdateFolderRestart` logs `NoSuchFileException: plugins\update`, then `close()` dereferences the missing task. Pre-existing in Vi6/NekotineCore, not caused by the spike.
- source_spec: `paper-junit-server/DESIGN.md`
  summary: paper-junit-server leaves a half-booted server unstopped when PaperServer.start() fails after launch (boot timeout or agent-unpatched check).
  evidence: maybe-false (medium if true). started stays false, so stop() does nothing. The worker's System.exit then runs Paper's shutdown hook, where an unpatched System.exit inside the hook may deadlock the worker. Settle it by booting with the agent patch disabled and watching whether the Gradle worker exits.
- source_spec: `paper-junit-server/DESIGN.md`
  summary: No check covers paper-junit-server staging (rcon/query rewrite, plugins/update and session.lock excluded, stale target jar excluded).
  evidence: A boot smoke test passes even if these regress. Add a unit test for rewriteProperty and the exclude rules, or a story-2 check on build/it-server/ after preparePaperTestServer.
- source_spec: `paper-junit-server/DESIGN.md`
  summary: No test runs the shutdown=hook fallback that the harness recommends when the agent breaks.
  evidence: All planned suites use agent mode; run the story-2 suite once with shutdown=hook and confirm the results reach the Gradle report.
- source_spec: `paper-junit-server/DESIGN.md`
  summary: The no-exit agent's fail-on-upgrade guard is not exercised by any test.
  evidence: A unit test in agent/ that builds a synthetic DedicatedServer class with the ClassFile API (onServerExit with and without System.exit) would pin both the patched flag and stack balance without a server.
- source_spec: none
  summary: NekotineCore/AGENTS.md line 18 still says Vi6 relocates fr.nekotine.core to fr.nekotine.vi6clean.nekotinecore.
  evidence: Story 2 dropped that relocation; the fix belongs in a NekotineCore PR (to dev), then a Vi6 gitlink bump.
- source_spec: none
  summary: NekotineCore PhaseMachine.isRunning() stays false after the first goTo, because the currentPhase == null branch returns without setting running = true.
  evidence: Seen in story 2 review fixes: isRunning() was false on a healthy server after Vi6Game.start(); PhaseMachine.goTo lines 51-63. Vi6SmokeIT checks getPhase(Vi6PhaseLobby.class) instead. Fix it in a NekotineCore PR to dev, then switch the smoke test back to isRunning().
- source_spec: none
  summary: Tool configs are overwritten at boot even when config.yml says override-config false.
  evidence: Vi6Main.onEnable calls ConfigManager.setupAsync() (runTaskAsynchronously) and then immediately builds every ToolHandler, whose constructor reads ConfigManager.getConfig().override_config() — still the in-memory default true. Fix by loading config.yml synchronously before discoverHandlers(), or by building handlers after the load; then a test can check tools with override false.
- source_spec: none
  summary: No test observes the boot-time override of config.yml and tools/*.yml.
  evidence: Both load once at boot, and the local test-bundle holds default values, so removing the override branch keeps Vi6ConfigIT green. Needs a stale-value fixture staged before boot (for example a harness bundle overlay); koths are covered in-process.
- source_spec: `_bmad-output/specs/spec-bots/SPEC.md`
  summary: NekotineCore ClientTrackModule never records sneak changes on 26.2, so a player who starts or stops sneaking while untracked (Vi6 Recall) comes back in the wrong sneak state. Affects humans today.
  evidence: The module reads sneak only from ENTITY_ACTION (`getPlayerActions()` START/STOP_SNEAKING). On 26.2, `ServerboundPlayerCommandPacket.Action` has no sneak actions (sprint, sleep, riding jump, inventory, elytra only); sneak travels as `Input.shift` in `ServerboundPlayerInputPacket` (javap on the 26.2.build.65-beta dev bundle). Sprint recording still works. Untracked players' PLAYER_INPUT packets are also not cancelled. User decision (2026-11-05): fix after the bots work is complete, in a NekotineCore PR to dev, then a Vi6 gitlink bump. Bot-side checks on this module (bot identity in the `HashMap<Player, …>` lookup, `PacketContainer` reads) wait with it.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-nekotinecore-splits-into-core-and-bots-modules.md`
  summary: Add a Vi6 verification task that fails when Vi6's resolved paperweight dev bundle differs from NekotineCore's `paper-devbundle` catalog pin.
  evidence: Story 1.1 review (Blind Hunter, Edge Case Hunter and Verification Gap all flagged it). The pin match is prose-only; a full `gradlew --write-locks` in Vi6 moves the dev bundle silently. It has no effect while `bots` is a placeholder; add the check with Story 1.3's first NMS code.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-nekotinecore-splits-into-core-and-bots-modules.md`
  summary: Keep the paperweight userdev plugin version in one place, or document bumping it in both NekotineCore's catalog and Vi6's `plugins {}` block.
  evidence: Story 1.1 review: `2.0.0-beta.21` is now written in both builds, and the AGENTS.md bump note covers only the dev bundle.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-nekotinecore-splits-into-core-and-bots-modules.md`
  summary: Make Vi6's `out` task run `verifyShadowJarLayout` before copying the jar to `Vi6CleanJarOutputPath`.
  evidence: Story 1.1 review: the layout check runs only under `check`, so a broken layout can reach a server's `plugins/update/` through bare `gradlew`. This predates the story.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-nekotinecore-splits-into-core-and-bots-modules.md`
  summary: Record in Vi6 AGENTS.md that compile resolves CommandAPI 11.2.1-SNAPSHOT only from the local Gradle cache (codemc no longer lists it), so a fresh machine fails.
  evidence: Story 1.1 implementation notes: re-resolving `11.2.+` dropped compile to 11.2.0. This predates the story.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-nekotinecore-splits-into-core-and-bots-modules.md`
  summary: Fix NekotineCore AGENTS.md wording: modules "build" plain jars (not "publish"), since nothing is published.
  evidence: Story 1.1 review: line 11 says both "publish plain jars" and "Nothing is published".
- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-nekotinecore-splits-into-core-and-bots-modules.md`
  summary: Make Vi6 AGENTS.md name the concrete locked dev bundle (26.2.build.65-beta, see gradle.lockfile) instead of the `26.2.build.+` selector when comparing with `paper-devbundle`.
  evidence: Story 1.1 review: the new AGENTS.md line points at a range, not the version to compare.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-nekotinecore-splits-into-core-and-bots-modules.md`
  summary: Delete the editor backup `core/src/main/java/fr/nekotine/core/map/.$MapModule.drawio.bkp` from NekotineCore.
  evidence: Story 1.1 review: a junk file, carried along by the source move into `core/`.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-nekotinecore-owns-paper-junit-server-and-boots-its-own-test-host.md`
  summary: Vi6 AGENTS.md's gitlink order (harness, then NekotineCore, then Vi6) should say each inner commit is pushed, with its own PR, before the outer gitlink points at it.
  evidence: Story 1.2 review (Blind Hunter): a gitlink to an unpushed commit breaks every recursive clone; the line omits the push step.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-2-nekotinecore-owns-paper-junit-server-and-boots-its-own-test-host.md`
  summary: Pin the shadow plugin to an exact version in both NekotineCore's and Vi6's catalogs (both say `9.4.+`).
  evidence: Story 1.2 review (Blind Hunter): plugin classpaths aren't covered by dependency locking, so the two builds in the composite can load different shadow versions over time.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-a-startup-self-test-turns-bots-off-loudly-when-nms-breaks.md`
  summary: Test the self-test's class-initialization step (`Class.forName(name, true, loader)` reporting "initialization failed").
  evidence: Review pass 1 (B8/G2): deleting that loop keeps every `BotSelfTestIT` test green; a real test needs a synthesized internal class with a throwing `<clinit>` and a custom class loader.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-a-startup-self-test-turns-bots-off-loudly-when-nms-breaks.md`
  summary: Assert in a test that BotsTestHost runs the bots self-test at enable (pass line in the boot log), not lazily on first `roster()` call.
  evidence: Review pass 2 (G2-3): removing `Ioc.resolve(BotRoster.class)` from `BotsTestHost.onEnable` keeps every IT green; pin it when Vi6 adopts the roster (Story 2.1).
- source_spec: `_bmad-output/implementation-artifacts/spec-1-5-a-startup-self-test-turns-bots-off-loudly-when-nms-breaks.md`
  summary: Make the bots self-test check that every overriding method in `bot.internal` still overrides a method of its external supertype.
  evidence: Review pass 2 (B2-1), medium, unverified for future code: an override without a `super` call (e.g. a new `BotConnection` override in 1.6/1.7) would silently stop being called after a Paper re-signature, and the self-test would pass.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-bots-walk-look-jump-sneak-and-sprint-through-real-packets.md`
  summary: Bots apply server-sent knockback: record `ClientboundSetEntityMotionPacket` for the bot's own entity (and explosion knockback) in `BotConnection#send` and start the next physics step from that velocity.
  evidence: User decision (2026-11-10) while planning Story 1.6: deferred, likely to Epic 5 melee; until then bots ignore hits and explosions.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-6-bots-walk-look-jump-sneak-and-sprint-through-real-packets.md`
  summary: Resync a bot's sprint (and sneak) with the server: when the sprint key is held but the server cleared sprinting (attack, damage, a cancelled `PlayerToggleSprintEvent`), resend START_SPRINTING as a client does.
  evidence: Review pass 1 (B2/E2), medium: `apply()` compares only with the last sent input, so after `setSprinting(false)` on the server `setSprint(true)` is a no-op until toggled off and on; matters once bots attack (Story 1.7, Epic 5 melee).
- source_spec: `_bmad-output/implementation-artifacts/spec-1-7-bots-use-their-hands-send-commands-and-respawn.md`
  summary: Add a release-use-item control (`ServerboundPlayerActionPacket` RELEASE_USE_ITEM) so bots can draw and loose bows, crossbows and tridents, eat on demand and stop blocking.
  evidence: Story 1.7 scope (Never list): `useItem` only starts using an item; stopping early needs the release packet.
- source_spec: `_bmad-output/implementation-artifacts/spec-1-7-bots-use-their-hands-send-commands-and-respawn.md`
  summary: Continue block breaking: client-side break progress, then STOP_DESTROY_BLOCK (or ABORT_DESTROY_BLOCK), so bots break blocks that are not instant.
  evidence: Story 1.7 scope (Never list): `startBreaking` sends only START_DESTROY_BLOCK, which breaks only instant blocks (creative, soft blocks).
- source_spec: `_bmad-output/implementation-artifacts/spec-1-7-bots-use-their-hands-send-commands-and-respawn.md`
  summary: Signed commands: `sendCommand` sends `ServerboundChatCommandPacket` (unsigned), so a command with signed arguments (`/msg`, `/say`...) is refused when the server enforces secure profiles; bots would need a chat session and `ServerboundChatCommandSignedPacket`.
  evidence: Story 1.7 scope (Never list).
- source_spec: `_bmad-output/implementation-artifacts/spec-1-7-bots-use-their-hands-send-commands-and-respawn.md`
  summary: The sprint resync deferred from Story 1.6 is now reachable: a bot's `attack()` makes the server clear sprinting, and a held `setSprint(true)` is never resent.
  evidence: Review pass 1 (B3): `apply()` sends sprint only on control change; `Player#attack` calls `setSprinting(false)` on a sprint hit.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-a-bot-joins-the-vi6-lobby-like-a-player.md`
  summary: Core's `ModuleManager.load` logs a module constructor exception and `Ioc.resolve` then returns a cached `null`, so Story 2.2's bot commands must treat a null `BotRoster` like an unavailable one ("bots unavailable"), or Core should stop caching null.
  evidence: Review pass 1 (B2): `ModuleManager.load` catches `Exception` and never puts the type; `Vi6Main` now logs a French error when the resolve returns null.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-a-bot-joins-the-vi6-lobby-like-a-player.md`
  summary: No integration test covers Vi6 enabling with bots unavailable (failed self-test or unbuildable roster): the game must still start and humans play.
  evidence: Review pass 1 (B4), medium unverified: needs a way to make the self-test fail inside the IT server (a fault-injection hook in `bots` or a broken staged jar).
- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-a-bot-joins-the-vi6-lobby-like-a-player.md`
  summary: Guard AD-12 automatically: a plain test that fails when a file in `src/main/java` outside `fr.nekotine.vi6clean.bot` (and `Vi6Main`) references `fr.nekotine.core.bot`.
  evidence: Review pass 1 (B6): the rule is checked only at review time; Stories 2.2-2.4 add the bot package.
- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-a-bot-joins-the-vi6-lobby-like-a-player.md`
  summary: On server stop with bots online, `Vi6Main.onDisable` closes the game before `super.onDisable()` unloads the roster, so bots quit after the game is torn down; check the log stays clean, or remove bots before `game.close()`.
  evidence: Review pass 1 (B7), medium unverified: stop the IT server with a bot online and look for exceptions after "Bot_x left the game".
- source_spec: `_bmad-output/implementation-artifacts/spec-2-2-the-operator-adds-lists-and-removes-bots-with-commands.md`
  summary: Document the op-only `/bot add <count> <guard|thief|any>`, `/bot list` and `/bot remove <name|all>` commands and their 6-player cap in Vi6 `AGENTS.md` "Where things are".
  evidence: Review pass 1 (B13): the commands exist only in code and the spec.
- source_spec: `_bmad-output/implementation-artifacts/spec-3-1-phase-durations-can-be-shortened-without-changing-the-game-for-humans.md`
  summary: No test shows that shortened delay-between-capture and delay-before-escape values reach InMapPhasePlayerWrapper's capture and escape unlock timers.
  evidence: Vi6PhaseDurationsIT's round has no thief inside the map, so thiefScheduleCanCaptureArtefact / thiefScheduleCanLeaveMap never run; Story 3.4 should assert canCaptureArtefact and canLeaveMap turn true after the shortened ticks.
