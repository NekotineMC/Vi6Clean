package fr.nekotine.vi6clean.it;

import static fr.nekotine.vi6clean.it.Vi6Asserts.assertCleanLog;
import static fr.nekotine.vi6clean.it.Vi6TestHooks.GAME_TIMEOUT_TICKS;
import static fr.nekotine.vi6clean.it.Vi6TestHooks.currentMap;
import static fr.nekotine.vi6clean.it.Vi6TestHooks.dispatch;
import static fr.nekotine.vi6clean.it.Vi6TestHooks.game;
import static fr.nekotine.vi6clean.it.Vi6TestHooks.gameEndedLines;
import static fr.nekotine.vi6clean.it.Vi6TestHooks.savedFiles;
import static fr.nekotine.vi6clean.it.Vi6TestHooks.shortTimersWithoutKoths;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.vi6clean.bot.PhaseBrain;
import fr.nekotine.vi6clean.bot.PreparationBrain;
import fr.nekotine.vi6clean.bot.Vi6Bots;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInMap;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.game.phase.Vi6PhasePreparation;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;

/**
 * The unattended-run regression baseline: in one boot, each repetition runs a
 * 4-bot game then, from its lobby, a 6-bot game, both through {@code /bot run}
 * on short timers with no KoTH. Each game goes lobby, preparation,
 * infiltration, lobby and logs one summary line; the 6-bot run starts with
 * nothing of the first left; a seed gives the same entrance picks every
 * repetition. Replay a failure by hand with {@code bot run 4 42} then
 * {@code bot run 6 7}.
 */
class Vi6SoakIT {

	private static final long FIRST_SEED = 42;

	private static final long SECOND_SEED = 7;

	/**
	 * The phases a game of a run goes through until it ends, sampled each tick and
	 * collapsed.
	 */
	private static final List<String> GAME_PHASES = List.of("lobby", "preparation+in-map", "infiltration+in-map");

	/**
	 * The phases the looping run may be seen in, after infiltration, before the
	 * game's line is seen: the next game can start before the line's task runs, and
	 * its lobby can pass between two samples, so {@link #SETUPS} proves the return
	 * to the lobby.
	 */
	private static final List<List<String>> AFTER_GAME_PHASES = List.of(List.of(), List.of("lobby"),
			List.of("preparation+in-map"), List.of("lobby", "preparation+in-map"));

	/**
	 * The phases a game sets up, in order, until its line, lobby return included.
	 */
	private static final List<String> SETUPS = List.of("Vi6PhaseInMap", "Vi6PhasePreparation", "Vi6PhaseInfiltration",
			"Vi6PhaseLobby");

	// The info line NekotineCore's PhaseMachine logs as it sets a phase up
	private static final Pattern SETUP = Pattern.compile("La phase (\\w+) est setup \\(\\d+ ms\\)");

	/**
	 * Per bot count, the picks of the first repetition, compared with the next
	 * ones.
	 */
	private static final Map<Integer, Map<String, String>> FIRST_PICKS = new HashMap<>();

	@BeforeAll
	static void waitForAsyncConfigLoad() throws Exception {
		// ConfigManager.setupAsync() loads config.yml off the main thread
		PaperServer.awaitTicks(40);
	}

	@BeforeEach
	@AfterEach
	void reset() throws Exception {
		Vi6TestHooks.reset();
	}

	@RepeatedTest(3)
	void aFourBotThenASixBotGameReachAResult() throws Exception {
		shortTimersWithoutKoths();
		try (var capture = new LogCapture()) {
			var first = new GameRecord();
			var firstStart = PaperServer.onMain(() -> {
				var out = dispatch("bot run 4 " + FIRST_SEED);
				first.sample();
				return out;
			});
			assertStarted(capture, firstStart, 4, FIRST_SEED);
			var firstBots = PaperServer.onMain(() -> Ioc.resolve(BotRoster.class).list());
			first.awaitEnd(capture);
			first.check(capture, 4);

			// Second run, from the lobby of the looping first one
			var firstPlayers = firstBots.stream().map(Bot::player).toList();
			var firstFiles = new ArrayList<Path>();
			var second = new GameRecord();
			var secondStart = new ArrayList<String>();
			everyTick(() -> {
				if (game().getPhaseMachine().getPhase(Vi6PhaseLobby.class) == null) {
					return false;
				}
				// Saved files the reset must wipe
				firstPlayers.forEach(Player::saveData);
				firstBots.forEach(b -> firstFiles.addAll(savedFiles(b.player())));
				assertTrue(firstFiles.stream().anyMatch(Files::exists), "no bot file saved before the second run");
				assertCleanLog(capture);
				capture.clear();
				secondStart.addAll(dispatch("bot run 6 " + SECOND_SEED));
				// Checked in the same tick: the new bots have saved nothing yet
				for (var file : firstFiles) {
					assertFalse(Files.exists(file), "bot file left: " + file);
				}
				second.sample();
				return true;
			}, 2 * GAME_TIMEOUT_TICKS);
			assertStarted(capture, secondStart, 6, SECOND_SEED);
			PaperServer.onMain(() -> {
				var roster = Ioc.resolve(BotRoster.class).list();
				assertEquals(6, roster.size(), "roster " + roster);
				// Same names, same offline UUIDs: only the roster tells old bots from new ones
				roster.forEach(b -> assertFalse(firstBots.contains(b), b.name() + " is a bot of the first run"));
				roster.forEach(b -> assertTrue(b.player().isOnline(), b.name() + " is not online"));
				// isOnline() of an old player is true again once a new bot has its UUID
				var rosterPlayers = roster.stream().map(Bot::player).toList();
				for (var player : Bukkit.getOnlinePlayers()) {
					assertTrue(rosterPlayers.stream().anyMatch(p -> p == player),
							player.getName() + " is online but not a bot of this run");
					assertFalse(firstPlayers.stream().anyMatch(p -> p == player),
							player.getName() + " of the first run is online");
				}
				var online = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
				var stale = game().getScoreboard().getEntries().stream()
						.filter(e -> e.startsWith("Bot_") && !online.contains(e)).toList();
				assertEquals(List.of(), stale, "scoreboard entries of bots gone");
				return null;
			});
			second.awaitEnd(capture);
			second.check(capture, 6);

			assertCleanLog(capture);
		}
	}

	/**
	 * Runs {@code condition} on the main thread every tick, from the next one,
	 * until it returns {@code true}; fails after {@code timeoutTicks} ticks or with
	 * what the condition threw. Unlike {@link PaperServer#awaitUntil}, which sleeps
	 * between checks, it sees every tick's state.
	 * <p>
	 * Each check schedules the next as a new task: a task due in a tick runs after
	 * the older ones, so a check sees what the game's own timers changed in that
	 * tick before the bots tick. A repeating task, older than those timers, would
	 * miss a lobby the looping run leaves on the next bot tick.
	 */
	private static void everyTick(Callable<Boolean> condition, int timeoutTicks) throws Exception {
		var done = new CompletableFuture<Void>();
		var plugin = Ioc.resolve(JavaPlugin.class);
		var check = new Runnable() {

			private int ticks;

			@Override
			public void run() {
				try {
					if (condition.call()) {
						done.complete(null);
					} else if (++ticks >= timeoutTicks) {
						done.completeExceptionally(new AssertionError("not met within " + timeoutTicks + " ticks"));
					} else {
						Bukkit.getScheduler().runTaskLater(plugin, this, 1);
					}
				} catch (Throwable t) {
					done.completeExceptionally(t);
				}
			}
		};
		Bukkit.getScheduler().runTask(plugin, check);
		try {
			// Wall-clock guard in case the server stops ticking
			done.get(timeoutTicks * 500L, TimeUnit.MILLISECONDS);
		} catch (ExecutionException e) {
			if (e.getCause() instanceof Exception cause) {
				throw cause;
			}
			if (e.getCause() instanceof Error cause) {
				throw cause;
			}
			throw e;
		}
	}

	private static void assertStarted(LogCapture capture, List<String> out, int count, long seed) {
		assertEquals(1, out.size(), "start output: " + out);
		assertTrue(out.getFirst().startsWith("Run démarré: " + count + " bot(s)"), out.getFirst());
		var line = "Run started: requested=" + count + ", added=" + count + ", seed=" + seed;
		assertTrue(capture.messages().contains(line), "no start line in " + capture.messages());
	}

	/** What a run's first game went through, recorded on the main thread. */
	private static final class GameRecord {

		private final List<String> phases = new ArrayList<>();

		private @Nullable Map<String, String> picks;

		private int thiefSpawns;

		/** Records the current phase, and the picks once in infiltration. */
		void sample() {
			var label = phaseLabel();
			if (phases.isEmpty() || !phases.getLast().equals(label)) {
				phases.add(label);
			}
			if (picks == null && game().getPhaseMachine().getPhase(Vi6PhaseInfiltration.class) != null) {
				// The picks stay set until the next preparation
				picks = readPicks();
				thiefSpawns = currentMap().getThiefSpawns().size();
			}
		}

		/** Samples each tick until the run's game 1 has logged its line. */
		void awaitEnd(LogCapture capture) throws Exception {
			try {
				everyTick(() -> {
					sample();
					return !gameEndedLines(capture, 1).isEmpty();
				}, GAME_TIMEOUT_TICKS);
			} catch (AssertionError | RuntimeException e) {
				throw new AssertionError("phases seen " + phases + ", log " + capture.messages(), e);
			}
		}

		void check(LogCapture capture, int count) {
			var what = count + "-bot game, phases seen " + phases + ", log " + capture.messages();
			assertEquals(1, gameEndedLines(capture, 1).size(), "one game=1 line, " + what);
			assertTrue(phases.size() >= GAME_PHASES.size(), what);
			assertEquals(GAME_PHASES, phases.subList(0, GAME_PHASES.size()), what);
			assertTrue(AFTER_GAME_PHASES.contains(phases.subList(GAME_PHASES.size(), phases.size())), what);
			var messages = capture.messages();
			var setups = messages.subList(0, messages.indexOf(gameEndedLines(capture, 1).getFirst())).stream()
					.map(SETUP::matcher).filter(Matcher::matches).map(m -> m.group(1)).toList();
			assertEquals(SETUPS, setups, "phases set up, " + what);
			assertNotNull(picks, "no pick read in infiltration");
			assertTrue(thiefSpawns >= 2, "the map has " + thiefSpawns + " thief spawn(s): the replay check is moot");
			var first = FIRST_PICKS.putIfAbsent(count, picks);
			if (first != null) {
				assertEquals(first, picks, "the same seed gave other picks in the " + count + "-bot run");
			}
			assertCleanLog(capture);
		}

		private static String phaseLabel() {
			var machine = game().getPhaseMachine();
			var inMap = machine.getPhase(Vi6PhaseInMap.class) != null ? "+in-map" : "";
			if (machine.getPhase(Vi6PhaseInfiltration.class) != null) {
				return "infiltration" + inMap;
			}
			if (machine.getPhase(Vi6PhasePreparation.class) != null) {
				return "preparation" + inMap;
			}
			if (machine.getPhase(Vi6PhaseLobby.class) != null) {
				return "lobby" + inMap;
			}
			return "none" + inMap;
		}

		/** @return bot name to its entrance pick, {@code none} for guards */
		private static Map<String, String> readPicks() {
			var bots = Ioc.resolve(ModuleManager.class).get(Vi6Bots.class);
			var thieves = game().getThiefs();
			var result = new TreeMap<String, String>();
			for (var bot : Ioc.resolve(BotRoster.class).list()) {
				var brain = assertInstanceOf(PhaseBrain.class, bots.getBrain(bot), bot.name() + " brain");
				var picked = assertInstanceOf(PreparationBrain.class, brain.preparation(), bot.name() + " brain")
						.pickedEntrance();
				if (thieves.contains(bot.player())) {
					assertNotNull(picked, bot.name() + " (thief) picked no entrance");
					result.put(bot.name(), picked);
				} else {
					assertNull(picked, bot.name() + " (guard) picked an entrance");
					result.put(bot.name(), "none");
				}
			}
			return result;
		}
	}
}
