package fr.nekotine.vi6clean.it;

import static fr.nekotine.vi6clean.it.Vi6TestHooks.game;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.vi6clean.bot.BotTeamIntent;
import fr.nekotine.vi6clean.bot.Vi6Bots;
import fr.nekotine.vi6clean.bot.Vi6Run;
import fr.nekotine.vi6clean.configuration.ConfigManager;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.game.phase.Vi6PhasePreparation;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * {@code /bot run} starts an unattended run from a clean roster; its games loop
 * and each logs one summary line once back in the lobby.
 */
class Vi6BotRunIT {

	/** Ticks a game of the run may take, map loads included. */
	private static final int GAME_TIMEOUT_TICKS = 600;

	private static final Pattern GAME_ENDED = Pattern.compile(
			"Game ended: game=(\\d+), reason=(lost-timer|all-thieves-left), stolen=(\\d+), escaped=(\\d+), duration=(\\d+)s");

	@BeforeAll
	static void waitForAsyncConfigLoad() throws Exception {
		// ConfigManager.setupAsync() loads config.yml off the main thread
		PaperServer.awaitTicks(40);
	}

	@AfterEach
	void reset() throws Exception {
		Vi6TestHooks.reset();
	}

	@Test
	void runsLoopAndASecondRunStartsClean() throws Exception {
		Vi6TestHooks.reset();
		shortTimersWithoutKoths();
		try (var capture = new LogCapture()) {
			var started = run("bot run 4 42");
			assertEquals(1, started.size(), "start output: " + started);
			assertTrue(started.getFirst().startsWith("Run démarré: 4 bot(s)"), started.getFirst());
			assertTrue(capture.messages().contains("Run started: requested=4, added=4, seed=42"),
					"no start line in " + capture.messages());
			var firstBots = PaperServer.onMain(() -> {
				var roster = Ioc.resolve(BotRoster.class).list();
				assertEquals(List.of("Bot_1", "Bot_2", "Bot_3", "Bot_4"), roster.stream().map(Bot::name).toList());
				var bots = Ioc.resolve(ModuleManager.class).get(Vi6Bots.class);
				roster.forEach(b -> assertEquals(BotTeamIntent.ANY, bots.getIntent(b), b.name() + " intent"));
				return roster;
			});

			awaitGameEnded(capture, 1);
			// The games loop on their own
			awaitGameEnded(capture, 2);

			// Second run, from the lobby of the looping first one
			var firstPlayers = firstBots.stream().map(Bot::player).toList();
			var firstFiles = new ArrayList<Path>();
			var secondStart = new ArrayList<String>();
			PaperServer.awaitUntil(() -> {
				if (game().getPhaseMachine().getPhase(Vi6PhaseLobby.class) == null) {
					return false;
				}
				// Saved files the reset must wipe
				firstPlayers.forEach(Player::saveData);
				firstBots.forEach(b -> firstFiles.addAll(savedFiles(b.player())));
				assertTrue(firstFiles.stream().anyMatch(Files::exists), "no bot file saved before the second run");
				capture.clear();
				secondStart.addAll(dispatch("bot run 6 7"));
				// Checked in the same tick: the new bots have saved nothing yet
				for (var file : firstFiles) {
					assertFalse(Files.exists(file), "bot file left: " + file);
				}
				return true;
			}, 2 * GAME_TIMEOUT_TICKS);
			assertEquals(1, secondStart.size(), "start output: " + secondStart);
			assertTrue(secondStart.getFirst().startsWith("Run démarré: 6 bot(s)"), secondStart.getFirst());
			PaperServer.onMain(() -> {
				var roster = Ioc.resolve(BotRoster.class).list();
				assertEquals(6, roster.size(), "roster " + roster);
				// Same names, same offline UUIDs: only the roster tells old bots from new ones
				roster.forEach(b -> assertFalse(firstBots.contains(b), b.name() + " is a bot of the first run"));
				roster.forEach(b -> assertTrue(b.player().isOnline(), b.name() + " is not online"));
				var online = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
				var stale = game().getScoreboard().getEntries().stream()
						.filter(e -> e.startsWith("Bot_") && !online.contains(e)).toList();
				assertEquals(List.of(), stale, "scoreboard entries of bots gone");
				return null;
			});
			assertTrue(capture.messages().contains("Run started: requested=6, added=6, seed=7"),
					"no start line in " + capture.messages());
			// The counter restarts with the run
			awaitGameEnded(capture, 1);

			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	@Test
	void aResetMidGameAbortsTheRunSilently() throws Exception {
		Vi6TestHooks.reset();
		shortTimersWithoutKoths();
		try (var capture = new LogCapture()) {
			run("bot run 4 42");
			PaperServer.awaitUntil(() -> game().getPhaseMachine().getPhase(Vi6PhaseInfiltration.class) != null,
					GAME_TIMEOUT_TICKS);
			Vi6TestHooks.reset();
			// Longer than the shortened lost timer and end-spectator time
			PaperServer.awaitTicks(120);
			assertTrue(capture.messages().stream().noneMatch(m -> m.startsWith("Game ended:")),
					"aborted game logged: " + capture.messages());
			PaperServer.onMain(() -> {
				assertFalse(Ioc.resolve(ModuleManager.class).get(Vi6Run.class).isActive(), "run still active");
				return null;
			});
			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	@Test
	void wrongCountsAreRefused() throws Exception {
		Vi6TestHooks.reset();
		try (var capture = new LogCapture()) {
			for (var count : List.of(5, 2)) {
				var out = run("bot run " + count);
				assertEquals(List.of("Run refusé: " + count + " bots demandés, 4 ou 6 seulement"), out);
			}
			assertNothingStarted(capture);
			PaperServer.onMain(() -> {
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseLobby.class), "left the lobby");
				assertEquals(List.of(), Ioc.resolve(BotRoster.class).list(), "the roster changed");
				return null;
			});
		}
	}

	@Test
	void aRunIsRefusedPastTheLobby() throws Exception {
		Vi6TestHooks.reset();
		var hooks = Vi6TestHooks.spawnBots(2);
		Vi6TestHooks.putOnTeams(List.of(hooks.get(0).player()), List.of(hooks.get(1).player()));
		Vi6TestHooks.forceKoths();
		Vi6TestHooks.goToPreparation();
		try (var capture = new LogCapture()) {
			assertEquals(List.of("Run refusé: la partie n'est pas dans le lobby"), run("bot run 4"));
			assertNothingStarted(capture);
			PaperServer.onMain(() -> {
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhasePreparation.class), "left preparation");
				assertEquals(hooks, Ioc.resolve(BotRoster.class).list(), "the roster changed");
				return null;
			});
		}
	}

	@Test
	void gameLobbyTwiceKeepsOneHandler() throws Exception {
		Vi6TestHooks.reset();
		PaperServer.onMain(() -> {
			assertTrue(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "game lobby"));
			assertTrue(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "game lobby"));
			var game = game();
			var handlers = Arrays.stream(PlayerItemHeldEvent.getHandlerList().getRegisteredListeners())
					.filter(l -> l.getListener() == game).count();
			assertEquals(1, handlers, "Vi6Game handlers for PlayerItemHeldEvent");
			return null;
		});
	}

	/** Short phases and no KoTH, until {@link Vi6TestHooks#reset()}. */
	private static void shortTimersWithoutKoths() throws Exception {
		PaperServer.onMain(() -> {
			var config = Ioc.resolve(ConfigManager.class).getConfig().game();
			config.preparation().duration(Duration.ofSeconds(2));
			config.infiltration().lostDuration(Duration.ofSeconds(3));
			config.infiltration().endSpectatorTime(Duration.ofMillis(500));
			return null;
		});
		Vi6TestHooks.forceKoths();
	}

	/** Waits for exactly one {@code Game ended: game=<game>} line. */
	private static void awaitGameEnded(LogCapture capture, int game) throws Exception {
		PaperServer.awaitUntil(() -> gameEndedLines(capture, game).size() > 0, GAME_TIMEOUT_TICKS);
		var lines = gameEndedLines(capture, game);
		assertEquals(1, lines.size(), "game " + game + " lines: " + lines);
	}

	private static List<String> gameEndedLines(LogCapture capture, int game) {
		return capture.messages().stream().filter(m -> m.startsWith("Game ended: game=" + game + ","))
				.peek(m -> assertTrue(GAME_ENDED.matcher(m).matches(), "malformed line: " + m)).toList();
	}

	private static void assertNothingStarted(LogCapture capture) throws Exception {
		assertTrue(capture.messages().stream().noneMatch(m -> m.startsWith("Run started:")),
				"a run started: " + capture.messages());
		assertEquals(List.of(), capture.failures(), "an exception was logged");
		assertEquals(List.of(), capture.errors(), "an error was logged");
	}

	/** @return the player's data, stats and advancements files; main thread only */
	private static List<Path> savedFiles(Player player) {
		var server = MinecraftServer.getServer();
		var uuid = player.getUniqueId().toString();
		return List.of(server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat"),
				server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(uuid + ".json"),
				server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(uuid + ".json"));
	}

	private static List<String> run(String command) throws Exception {
		return PaperServer.onMain(() -> dispatch(command));
	}

	/** Main thread only. */
	private static List<String> dispatch(String command) {
		var out = new ArrayList<String>();
		var sender = Bukkit.createCommandSender(c -> out.add(PlainTextComponentSerializer.plainText().serialize(c)));
		assertTrue(Bukkit.dispatchCommand(sender, command), "command not dispatched: " + command);
		return out;
	}
}
