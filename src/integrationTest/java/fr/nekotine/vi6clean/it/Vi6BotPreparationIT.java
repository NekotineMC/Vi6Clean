package fr.nekotine.vi6clean.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.bot.BotTeamIntent;
import fr.nekotine.vi6clean.bot.PhaseBrain;
import fr.nekotine.vi6clean.bot.PreparationBrain;
import fr.nekotine.vi6clean.bot.Vi6Bots;
import fr.nekotine.vi6clean.configuration.ConfigManager;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.game.phase.Vi6PhasePreparation;
import fr.nekotine.vi6clean.wrapper.PreparationPhasePlayerWrapper;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;
import io.papermc.paper.util.Tick;

/**
 * Bot brains get through preparation with the commands a player sends: thieves
 * pick an entrance, everyone readies up, then they stay idle in the map.
 */
class Vi6BotPreparationIT {

	private static final long SEED = 43L;

	/** Infiltration must start within this many ticks of the brains starting. */
	private static final int PREPARATION_TIMEOUT_TICKS = 100;

	private static final int IDLE_TICKS = 40;

	/** The picks of the first repetition, compared with the next ones. */
	private static Map<String, String> firstPicks;

	private List<Bot> guards;

	private List<Bot> thieves;

	@AfterEach
	void reset() throws Exception {
		Vi6TestHooks.reset();
	}

	@RepeatedTest(3)
	void twoAgainstTwoGetThroughPreparationAndStayIdle() throws Exception {
		try (var capture = new LogCapture()) {
			setUp();
			PaperServer.onMain(() -> {
				var duration = Tick.tick()
						.fromDuration(Ioc.resolve(ConfigManager.class).getConfig().game().preparation().duration());
				assertTrue(duration > 2 * PREPARATION_TIMEOUT_TICKS,
						"preparation lasts " + duration + " ticks: its timer would end it, not the brains");
				var bots = vi6Bots();
				bots.seedRandom(SEED);
				guards.forEach(b -> bots.setIntent(b, BotTeamIntent.GUARD));
				thieves.forEach(b -> bots.setIntent(b, BotTeamIntent.THIEF));
				return null;
			});
			PaperServer.awaitUntil(() -> game().getPhaseMachine().getPhase(Vi6PhaseInfiltration.class) != null,
					PREPARATION_TIMEOUT_TICKS);

			var picks = PaperServer.onMain(() -> {
				var result = new TreeMap<String, String>();
				for (var thief : thieves) {
					var brain = (PhaseBrain) vi6Bots().getBrain(thief);
					assertNotNull(brain, thief.name() + " lost its brain");
					var picked = ((PreparationBrain) brain.preparation()).pickedEntrance();
					assertNotNull(picked, thief.name() + " picked no entrance");
					result.put(thief.name(), picked);
				}
				for (var guard : guards) {
					var brain = (PhaseBrain) vi6Bots().getBrain(guard);
					assertNull(((PreparationBrain) brain.preparation()).pickedEntrance(),
							guard.name() + " (guard) picked an entrance");
				}
				return result;
			});
			// Teleports take a few ticks to reach a bot and be acked
			PaperServer.awaitTicks(5);
			var positions = PaperServer.onMain(() -> {
				for (var thief : thieves) {
					var spawn = Vi6TestHooks.thiefSpawn(picks.get(thief.name())).getSpawnPoint()
							.toLocation(game().getWorld());
					var at = thief.player().getLocation();
					assertTrue(at.distance(spawn) <= 1,
							thief.name() + " is at " + at + ", not at its entrance " + picks.get(thief.name()));
				}
				return locations();
			});
			PaperServer.awaitTicks(IDLE_TICKS);
			PaperServer.onMain(() -> {
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseInfiltration.class), "infiltration ended");
				var now = locations();
				for (var entry : positions.entrySet()) {
					var before = entry.getValue();
					var after = now.get(entry.getKey());
					assertTrue(
							Math.abs(before.getX() - after.getX()) < 1e-6
									&& Math.abs(before.getZ() - after.getZ()) < 1e-6,
							entry.getKey() + " moved from " + before + " to " + after);
				}
				return null;
			});

			if (firstPicks == null) {
				firstPicks = picks;
			} else {
				assertEquals(firstPicks, picks, "the same seed gave other entrances");
			}
			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	@Test
	void aThiefWithNoEntranceOnlyReadiesUp() throws Exception {
		try (var capture = new LogCapture()) {
			setUp();
			var thief = thief();
			var brain = new PreparationBrain(new Random(SEED), List::of);
			PaperServer.onMain(() -> {
				vi6Bots().setBrain(thief, new PhaseBrain(PhaseBrain.IDLE, brain, PhaseBrain.IDLE));
				return null;
			});
			PaperServer.awaitTicks(5);
			PaperServer.onMain(() -> {
				// The other bots are brainless: preparation holds
				assertNotNull(game().getPhaseMachine().getPhase(Vi6PhasePreparation.class), "preparation ended");
				var wrapper = Ioc.resolve(WrappingModule.class).getWrapper(thief.player(),
						PreparationPhasePlayerWrapper.class);
				assertTrue(wrapper.isReadyForNextPhase(), "the thief did not ready up");
				assertNull(wrapper.getSelectedSpawn(), "the thief selected an entrance: the fallback would not apply");
				assertNull(brain.pickedEntrance(), "the brain recorded a pick");
				return null;
			});
			// A brain toggling /game prepready each tick would be un-ready after an odd
			// number of further ticks
			PaperServer.awaitTicks(5);
			assertStillReady(thief);
			PaperServer.awaitTicks(1);
			assertStillReady(thief);
			assertEquals(List.of(), capture.failures(), "an exception was logged");
			assertEquals(List.of(), capture.errors(), "an error was logged");
		}
	}

	@Test
	void aThrowingPreparationBrainStopsAndTheBotStays() throws Exception {
		var calls = new AtomicInteger();
		try (var capture = new LogCapture()) {
			setUp();
			var thief = thieves.getFirst();
			PaperServer.onMain(() -> {
				vi6Bots().setBrain(thief, new PhaseBrain(PhaseBrain.IDLE, _ -> {
					calls.incrementAndGet();
					throw new IllegalStateException("preparation brain test failure");
				}, PhaseBrain.IDLE));
				return null;
			});
			PaperServer.awaitTicks(5);
			assertEquals(1, calls.get(), "the brain was not stopped after its first throw");
			var lines = capture.errors().stream().filter(l -> l.startsWith(thief.name() + " ")).toList();
			assertEquals(1, lines.size(), "expected one brain error line, got " + lines);
			assertEquals(lines, capture.errors(), "another error was logged");
			var line = lines.getFirst();
			assertTrue(line.contains("équipe: thief"), line);
			assertTrue(line.contains("phase: Vi6PhasePreparation"), line);
		}
		PaperServer.onMain(() -> {
			assertTrue(thief().player().isOnline(), "the bot was disconnected");
			assertNull(vi6Bots().getBrain(thief()), "the brain was not stopped");
			assertNotNull(game().getPhaseMachine().getPhase(Vi6PhasePreparation.class), "preparation ended");
			return null;
		});
	}

	/**
	 * Resets, then joins 2 guard and 2 thief brainless bots with no KoTH, and goes
	 * to preparation.
	 */
	private void setUp() throws Exception {
		Vi6TestHooks.reset();
		var bots = Vi6TestHooks.spawnBots(4);
		guards = List.of(bots.get(0), bots.get(1));
		thieves = List.of(bots.get(2), bots.get(3));
		Vi6TestHooks.putOnTeams(guards.stream().map(Bot::player).toList(), thieves.stream().map(Bot::player).toList());
		Vi6TestHooks.forceKoths();
		// Brains are installed after: a lobby brain would ready up the lobby
		Vi6TestHooks.goToPreparation();
	}

	/** Asserts preparation still runs and {@code thief} is still ready. */
	private static void assertStillReady(Bot thief) throws Exception {
		PaperServer.onMain(() -> {
			assertNotNull(game().getPhaseMachine().getPhase(Vi6PhasePreparation.class), "preparation ended");
			assertTrue(Ioc.resolve(WrappingModule.class).getWrapper(thief.player(), PreparationPhasePlayerWrapper.class)
					.isReadyForNextPhase(), "the thief is no longer ready: its brain acted again");
			return null;
		});
	}

	private Bot thief() {
		return thieves.getFirst();
	}

	/** Main thread only. */
	private Map<String, Location> locations() {
		var result = new HashMap<String, Location>();
		for (var bot : guards) {
			result.put(bot.name(), bot.player().getLocation());
		}
		for (var bot : thieves) {
			result.put(bot.name(), bot.player().getLocation());
		}
		return result;
	}

	private static Vi6Bots vi6Bots() {
		return Ioc.resolve(ModuleManager.class).get(Vi6Bots.class);
	}

	private static Vi6Game game() {
		return Ioc.resolve(Vi6Game.class);
	}
}
