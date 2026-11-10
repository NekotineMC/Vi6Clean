package fr.nekotine.vi6clean.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.game.phase.Vi6PhasePreparation;
import fr.nekotine.vi6clean.wrapper.LobbyPhasePlayerWrapper;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * One human and 3 {@code any} bots balance to 2v2 and start preparation; the
 * bots idle in the round, and removing them mid-round is a plain quit. A
 * brainless bot stands in for the human and sends the commands a player types.
 */
class Vi6BotMixedLobbyIT {

	private static final String HUMAN = "Human_1";

	private static final List<String> BOTS = List.of("Bot_1", "Bot_2", "Bot_3");

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
	void humanKeepsJoinTeam() throws Exception {
		var human = spawnHuman();
		// The join path makes a lone player a guard
		assertEquals("guard", teamOf(human));
		fillReadyAndRemove(human);
	}

	@RepeatedTest(3)
	void humanPicksThiefFirst() throws Exception {
		var human = spawnHuman();
		PaperServer.onMain(() -> {
			human.sendCommand("game team thief");
			return null;
		});
		PaperServer.awaitTicks(2);
		assertEquals("thief", teamOf(human));
		fillReadyAndRemove(human);
	}

	private static Bot spawnHuman() throws Exception {
		PaperServer.onMain(() -> {
			assertNotNull(Ioc.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhaseLobby.class),
					"game is not in the lobby");
			assertEquals(0, Ioc.resolve(BotRoster.class).list().size(), "leftover bots");
			return null;
		});
		// No brain: the stand-in only sends what the test tells it to
		var human = PaperServer.onMain(() -> Ioc.resolve(BotRoster.class).spawn(HUMAN,
				Bukkit.getWorlds().getFirst().getSpawnLocation().add(0.5, 0, 0.5)));
		PaperServer.awaitTicks(5);
		return human;
	}

	private static void fillReadyAndRemove(Bot human) throws Exception {
		var humanTeam = teamOf(human);
		try (var capture = new LogCapture()) {
			assertEquals(List.of("Bot_1 ajouté (any)", "Bot_2 ajouté (any)", "Bot_3 ajouté (any)"),
					run("bot add 3 any"));
			// The bots pick and ready; the lobby waits for the stand-in
			PaperServer.awaitUntil(() -> {
				var wrapping = Ioc.resolve(WrappingModule.class);
				var bots = Ioc.resolve(BotRoster.class).list().stream().filter(b -> BOTS.contains(b.name())).toList();
				return bots.size() == BOTS.size() && bots.stream()
						.allMatch(b -> wrapping.getWrapperOptional(b.player(), LobbyPhasePlayerWrapper.class)
								.map(LobbyPhasePlayerWrapper::isReadyForNextPhase).orElse(false));
			}, 200);
			PaperServer.onMain(() -> {
				assertNotNull(Ioc.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhaseLobby.class),
						"the lobby completed before the human readied");
				human.sendCommand("game ready");
				return null;
			});
			PaperServer.awaitUntil(
					() -> Ioc.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhasePreparation.class) != null,
					200);

			var teams = teams();
			assertEquals(4, teams.size(), "expected the stand-in and 3 bots: " + teams);
			assertEquals(2, teams.values().stream().filter("guard"::equals).count(), "not 2 guards: " + teams);
			assertEquals(2, teams.values().stream().filter("thief"::equals).count(), "not 2 thiefs: " + teams);
			// Nothing rebalances around the human
			assertEquals(humanTeam, teams.get(HUMAN), "the stand-in changed team");

			// Bots idle in the round with no error
			PaperServer.awaitTicks(40);
			assertEquals(List.of(), capture.errors(), "errors logged from the add through the idle round");
			assertEquals(List.of(), capture.failures(), "exceptions logged from the add through the idle round");

			// Mid-round removal: the stand-in is a roster bot too, so remove by name
			var players = PaperServer.onMain(() -> {
				var map = new TreeMap<String, Player>();
				for (var bot : Ioc.resolve(BotRoster.class).list()) {
					map.put(bot.name(), bot.player());
				}
				return map;
			});
			assertTrue(players.keySet().containsAll(BOTS), "bots missing before removal: " + players.keySet());
			assertPreparation("before the removal");
			for (var name : BOTS) {
				assertEquals(List.of("1 bot(s) retiré(s)"), run("bot remove " + name), "bot remove " + name);
			}
			PaperServer.awaitTicks(2);
			assertPreparation("after the removal");
			PaperServer.onMain(() -> {
				var game = Ioc.resolve(Vi6Game.class);
				var scoreboard = game.getScoreboard();
				for (var name : BOTS) {
					var player = players.get(name);
					assertFalse(player.isOnline(), name + " still online after removal");
					assertFalse(game.getPlayerList().contains(player), name + " still in the player list");
					assertFalse(game.getGuards().contains(player), name + " still a guard");
					assertFalse(game.getThiefs().contains(player), name + " still a thief");
					assertNull(scoreboard.getEntryTeam(name), name + " still in a scoreboard team");
					assertFalse(scoreboard.getTeam("guard").hasEntry(name), name + " still on Garde");
					assertFalse(scoreboard.getTeam("thief").hasEntry(name), name + " still on Voleur");
				}
				var self = human.player();
				assertTrue(self.isOnline(), "the stand-in was disconnected");
				assertTrue(game.getPlayerList().contains(self), "the stand-in left the game");
				assertEquals(List.of(human), Ioc.resolve(BotRoster.class).list());
				return null;
			});
			assertEquals(List.of(), capture.errors(), "errors logged by the mid-round removal");
			assertEquals(List.of(), capture.failures(), "exceptions logged by the mid-round removal");
		}
	}

	private static void assertPreparation(String when) throws Exception {
		PaperServer.onMain(() -> {
			assertNotNull(Ioc.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhasePreparation.class),
					"preparation not active " + when);
			return null;
		});
	}

	private static String teamOf(Bot bot) throws Exception {
		return PaperServer.onMain(() -> {
			var game = Ioc.resolve(Vi6Game.class);
			var player = bot.player();
			return game.getGuards().contains(player) ? "guard" : game.getThiefs().contains(player) ? "thief" : "none";
		});
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
