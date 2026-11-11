package fr.nekotine.vi6clean.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.vi6clean.bot.Vi6Bots;
import fr.nekotine.vi6clean.configuration.ConfigManager;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.game.phase.Vi6PhasePreparation;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;

/**
 * Phases read their durations from the live config when they are built, so a
 * test shortens a round by setting them in memory and restores them with
 * {@link ConfigManager#tryLoad()}.
 */
class Vi6PhaseDurationsIT {

	private static final int TOLERANCE_TICKS = 5;

	@BeforeAll
	static void waitForAsyncConfigLoad() throws Exception {
		// ConfigManager.setupAsync() loads config.yml off the main thread.
		PaperServer.awaitTicks(40);
	}

	@AfterEach
	void reset() throws Exception {
		PaperServer.onMain(() -> {
			// Nothing saves the in-memory config: reloading the file undoes the test's
			// changes
			Ioc.resolve(ConfigManager.class).tryLoad();
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
	void durationsKeepFractionsOfASecond() throws Exception {
		PaperServer.onMain(() -> {
			var manager = Ioc.resolve(ConfigManager.class);
			var path = Ioc.resolve(JavaPlugin.class).getDataPath().resolve("config.yml");
			var original = Files.readAllBytes(path);
			try {
				var loader = YamlConfigurationLoader.builder().path(path).nodeStyle(NodeStyle.BLOCK).build();
				var node = loader.load();
				node.node("game", "preparation", "duration-seconds").set(1.5);
				node.node("game", "infiltration", "end-spectator-time-seconds").set(0.25);
				loader.save(node);
				manager.tryLoad();
				var game = manager.getConfig().game();
				assertEquals(Duration.ofMillis(1500), game.preparation().duration(), "duration-seconds: 1.5");
				assertEquals(Duration.ofMillis(250), game.infiltration().endSpectatorTime(),
						"end-spectator-time-seconds: 0.25");

				game.infiltration().lostDuration(Duration.ofMillis(2750));
				assertEquals(Duration.ofMillis(2750), game.infiltration().lostDuration(), "setter lost the fraction");
			} finally {
				Files.write(path, original);
			}
			return null;
		});
	}

	@RepeatedTest(3)
	void roundEndsOnShortenedTimers() throws Exception {
		PaperServer.onMain(() -> {
			assertNotNull(game().getPhaseMachine().getPhase(Vi6PhaseLobby.class), "game is not in the lobby");
			assertEquals(0, Ioc.resolve(BotRoster.class).list().size(), "leftover bots");
			var config = Ioc.resolve(ConfigManager.class).getConfig().game();
			config.preparation().duration(Duration.ofSeconds(2));
			config.infiltration().lostDuration(Duration.ofSeconds(3));
			config.infiltration().endSpectatorTime(Duration.ofMillis(500));
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
				// Brains would ready up again in the next lobby and start a new round
				var bots = Ioc.resolve(ModuleManager.class).get(Vi6Bots.class);
				Ioc.resolve(BotRoster.class).list().forEach(bot -> bots.setBrain(bot, null));
				return null;
			});
			int infiltrationStart = awaitPhase(Vi6PhaseInfiltration.class, 40 + 100);
			int lobbyStart = awaitPhase(Vi6PhaseLobby.class, 60 + 10 + 100);

			assertAround(40, infiltrationStart - preparationStart, "preparation length");
			assertAround(60 + 10, lobbyStart - infiltrationStart, "infiltration lost timer + end spectator time");
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
