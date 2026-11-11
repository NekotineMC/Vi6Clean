package fr.nekotine.vi6clean.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.vi6clean.bot.Vi6Bots;
import fr.nekotine.vi6clean.configuration.ConfigManager;
import fr.nekotine.vi6clean.configuration.GameConfig;
import fr.nekotine.vi6clean.game.PhaseDurations;
import fr.nekotine.vi6clean.game.PhaseDurations.Key;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.game.phase.Vi6PhasePreparation;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;

/**
 * Phases read their durations through {@link PhaseDurations}: config values by
 * default, test overrides when set, so a round runs in seconds.
 */
class Vi6PhaseDurationsIT {

	private static final Map<Key, Function<GameConfig, Duration>> CONFIG = Map.of(Key.PREPARATION,
			c -> c.preparation().duration(), Key.INFILTRATION_LOST, c -> c.infiltration().lostDuration(),
			Key.DELAY_BETWEEN_CAPTURE, c -> c.infiltration().delayBetweenCapture(), Key.DELAY_BEFORE_ESCAPE,
			c -> c.infiltration().delayBeforeEscape(), Key.END_SPECTATOR_TIME,
			c -> c.infiltration().endSpectatorTime());

	private static final int TOLERANCE_TICKS = 5;

	@BeforeAll
	static void waitForAsyncConfigLoad() throws Exception {
		// ConfigManager.setupAsync() loads config.yml off the main thread.
		PaperServer.awaitTicks(40);
	}

	@AfterEach
	void reset() throws Exception {
		PaperServer.onMain(() -> {
			Ioc.resolve(PhaseDurations.class).clearOverrides();
			var roster = Ioc.resolve(BotRoster.class);
			for (var bot : roster.list()) {
				roster.remove(bot);
			}
			return null;
		});
		PaperServer.awaitTicks(2);
		PaperServer.onMain(() -> {
			Ioc.resolve(Vi6Game.class).getPhaseMachine().goTo(Vi6PhaseLobby.class, null);
			return null;
		});
		PaperServer.awaitTicks(2);
	}

	@Test
	void defaultsAreTheConfigValues() throws Exception {
		PaperServer.onMain(() -> {
			var durations = Ioc.resolve(PhaseDurations.class);
			var game = Ioc.resolve(ConfigManager.class).getConfig().game();
			assertEquals(Key.values().length, CONFIG.size(), "a key is missing from the test");
			for (var key : Key.values()) {
				assertEquals(CONFIG.get(key).apply(game), durations.get(key), key + " differs from config.yml");
			}
			return null;
		});
	}

	@Test
	void clearingOverridesRestoresTheConfigValues() throws Exception {
		PaperServer.onMain(() -> {
			var durations = Ioc.resolve(PhaseDurations.class);
			var game = Ioc.resolve(ConfigManager.class).getConfig().game();
			for (var key : Key.values()) {
				durations.override(key, Duration.ofMillis(1500));
				assertEquals(Duration.ofMillis(1500), durations.get(key), key + " override not returned");
			}
			durations.clearOverrides();
			for (var key : Key.values()) {
				assertEquals(CONFIG.get(key).apply(game), durations.get(key), key + " not back to config.yml");
			}
			return null;
		});
	}

	@Test
	void unoverriddenDurationsFollowAnEditedConfig() throws Exception {
		PaperServer.onMain(() -> {
			var infiltration = Ioc.resolve(ConfigManager.class).getConfig().game().infiltration();
			var original = infiltration.lostDuration();
			try {
				infiltration.lostDuration(original.plusSeconds(17));
				assertEquals(original.plusSeconds(17), Ioc.resolve(PhaseDurations.class).get(Key.INFILTRATION_LOST),
						"get() did not read the edited config");
			} finally {
				infiltration.lostDuration(original);
			}
			return null;
		});
	}

	@Test
	void invalidOverridesAreRejected() throws Exception {
		PaperServer.onMain(() -> {
			var durations = Ioc.resolve(PhaseDurations.class);
			durations.override(Key.PREPARATION, Duration.ofSeconds(7));
			for (var invalid : new Duration[]{null, Duration.ZERO, Duration.ofSeconds(-1), Duration.ofMillis(49),
					Duration.ofMillis(50L * Integer.MAX_VALUE + 50)}) {
				assertThrows(IllegalArgumentException.class, () -> durations.override(Key.PREPARATION, invalid),
						"accepted " + invalid);
			}
			assertEquals(Duration.ofSeconds(7), durations.get(Key.PREPARATION),
					"a rejected override replaced the value");
			return null;
		});
	}

	@RepeatedTest(3)
	void roundEndsOnOverriddenTimers() throws Exception {
		PaperServer.onMain(() -> {
			assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseLobby.class), "game is not in the lobby");
			assertEquals(0, Ioc.resolve(BotRoster.class).list().size(), "leftover bots");
			var durations = Ioc.resolve(PhaseDurations.class);
			durations.override(Key.PREPARATION, Duration.ofSeconds(2));
			durations.override(Key.INFILTRATION_LOST, Duration.ofSeconds(3));
			durations.override(Key.END_SPECTATOR_TIME, Duration.ofSeconds(1));
			return null;
		});
		try (var capture = new LogCapture()) {
			// Separate thief and guard adds would start preparation before the guards join
			assertTrue(PaperServer.onMain(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "bot add 4 any")),
					"bot add not dispatched");

			int preparationStart = awaitPhase(Vi6PhasePreparation.class, 200);
			// The lost timer ends the round only with thieves in the map
			PaperServer.onMain(() -> {
				assertEquals(2, game().getThiefs().size(), "thieves at preparation");
				assertEquals(2, game().getGuards().size(), "guards at preparation");
				return null;
			});
			// Brains would ready up again in the next lobby and start a new round
			PaperServer.onMain(() -> {
				var bots = Ioc.resolve(ModuleManager.class).get(Vi6Bots.class);
				Ioc.resolve(BotRoster.class).list().forEach(bot -> bots.setBrain(bot, null));
				return null;
			});
			int infiltrationStart = awaitPhase(Vi6PhaseInfiltration.class, 40 + 100);
			int lobbyStart = awaitPhase(Vi6PhaseLobby.class, 60 + 20 + 100);

			assertAround(40, infiltrationStart - preparationStart, "preparation length");
			assertAround(60 + 20, lobbyStart - infiltrationStart, "infiltration lost timer + end spectator time");
			assertEquals(List.of(), capture.failures(), "an exception was logged during the round");
		}
	}

	private static int awaitPhase(Class<?> phase, int timeoutTicks) throws Exception {
		var reachedAt = new int[]{-1};
		PaperServer.awaitUntil(() -> {
			if (game().getPhaseMachine().getPhase(phase) != null) {
				reachedAt[0] = Bukkit.getCurrentTick();
				return true;
			}
			return false;
		}, timeoutTicks);
		return reachedAt[0];
	}

	private static void assertAround(int expectedTicks, int actualTicks, String what) {
		assertTrue(Math.abs(actualTicks - expectedTicks) <= TOLERANCE_TICKS,
				what + ": expected " + expectedTicks + " ticks (±" + TOLERANCE_TICKS + "), was " + actualTicks);
	}

	private static Vi6Game game() {
		return Ioc.resolve(Vi6Game.class);
	}
}
