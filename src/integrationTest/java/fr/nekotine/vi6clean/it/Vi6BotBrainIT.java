package fr.nekotine.vi6clean.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.vi6clean.bot.Vi6Bots;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.game.phase.Vi6PhasePreparation;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Bots pick their team and ready up with the commands a player sends, and the
 * lobby advances with no human.
 */
class Vi6BotBrainIT {

	@AfterEach
	void resetToLobby() throws Exception {
		PaperServer.onMain(() -> {
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

	@RepeatedTest(3)
	void fourAnyBotsBalanceReadyUpAndStartPreparation() throws Exception {
		PaperServer.onMain(() -> {
			assertNotNull(Ioc.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhaseLobby.class),
					"game is not in the lobby");
			assertEquals(0, Ioc.resolve(BotRoster.class).list().size(), "leftover bots");
			return null;
		});
		assertEquals(4, run("bot add 4 any").size());

		awaitPreparation();
		var teams = teams();
		assertEquals(Map.of("Bot_1", "guard", "Bot_2", "thief", "Bot_3", "guard", "Bot_4", "thief"), teams);
	}

	@Test
	void thiefIntentJoinsThiefsAndReadiesAlone() throws Exception {
		// The join path makes a lone player a guard: only the brain makes it a thief
		assertEquals(List.of("Bot_1 ajouté (thief)"), run("bot add 1 thief"));
		awaitPreparation();
		assertEquals(Map.of("Bot_1", "thief"), teams());
	}

	@Test
	void anyBotsBalanceAgainInTheNextLobby() throws Exception {
		run("bot add 4 any");
		awaitPreparation();
		PaperServer.onMain(() -> {
			var roster = Ioc.resolve(BotRoster.class);
			for (var bot : roster.list()) {
				if (bot.name().equals("Bot_2") || bot.name().equals("Bot_4")) {
					roster.remove(bot);
				}
			}
			return null;
		});
		PaperServer.awaitTicks(2);
		// The lobby swaps the last teams: both guards come back as thiefs, and both
		// brains pick in the same tick, before either command lands
		PaperServer.onMain(() -> {
			Ioc.resolve(Vi6Game.class).getPhaseMachine().goTo(Vi6PhaseLobby.class, null);
			return null;
		});
		awaitPreparation();
		assertEquals(Map.of("Bot_1", "guard", "Bot_3", "thief"), teams());
	}

	private static void awaitPreparation() throws Exception {
		// The lobby completes only once every player in it is ready
		PaperServer.awaitUntil(
				() -> Ioc.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhasePreparation.class) != null, 200);
	}

	private static Map<String, String> teams() throws Exception {
		return PaperServer.onMain(() -> {
			var game = Ioc.resolve(Vi6Game.class);
			var result = new TreeMap<String, String>();
			for (var bot : Ioc.resolve(BotRoster.class).list()) {
				var player = bot.player();
				assertTrue(player.isOnline(), bot.name() + " was disconnected");
				assertTrue(game.getPlayerList().contains(player), bot.name() + " is not in the game");
				result.put(bot.name(),
						game.getGuards().contains(player)
								? "guard"
								: game.getThiefs().contains(player) ? "thief" : "none");
			}
			return result;
		});
	}

	@Test
	void throwingBrainStopsAndTheBotStays() throws Exception {
		var calls = new AtomicInteger();
		var bot = PaperServer.onMain(() -> Ioc.resolve(BotRoster.class).spawn("Bot_Brain",
				Bukkit.getWorlds().getFirst().getSpawnLocation().add(0.5, 0, 0.5)));
		try (var capture = new LogCapture()) {
			PaperServer.onMain(() -> {
				Ioc.resolve(ModuleManager.class).get(Vi6Bots.class).setBrain(bot, _ -> {
					calls.incrementAndGet();
					throw new IllegalStateException("brain test failure");
				});
				return null;
			});
			PaperServer.awaitTicks(5);
			assertEquals(1, calls.get(), "the brain was not stopped after its first throw");
			var lines = capture.errors().stream().filter(l -> l.startsWith("Bot_Brain ")).toList();
			assertEquals(1, lines.size(), "expected one brain error line, got " + lines);
			var line = lines.getFirst();
			assertTrue(line.contains("équipe: guard") || line.contains("équipe: thief"), line);
			assertTrue(line.contains("phase: Vi6PhaseLobby"), line);
			assertTrue(
					capture.failures().stream()
							.anyMatch(l -> l.startsWith("Bot_Brain ") && l.contains("brain test failure")),
					"the brain error line carries no exception: " + capture.failures());
		}
		PaperServer.onMain(() -> {
			assertTrue(bot.player().isOnline(), "the bot was disconnected");
			assertEquals(List.of(bot), Ioc.resolve(BotRoster.class).list());
			return null;
		});
	}

	private static List<String> run(String command) throws Exception {
		return PaperServer.onMain(() -> {
			var out = new ArrayList<String>();
			var sender = Bukkit
					.createCommandSender(c -> out.add(PlainTextComponentSerializer.plainText().serialize(c)));
			assertTrue(Bukkit.dispatchCommand(sender, command), "command not dispatched: " + command);
			return out;
		});
	}
}
