package fr.nekotine.vi6clean.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.vi6clean.bot.BotTeamIntent;
import fr.nekotine.vi6clean.bot.Vi6Bots;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.xxgoldenbluexx.paperjunitserver.PaperServer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * The operator adds, lists and removes bots with {@code /bot}; non-ops are
 * refused.
 */
class Vi6BotCommandsIT {

	@AfterEach
	void removeBots() throws Exception {
		PaperServer.onMain(() -> {
			var roster = Ioc.resolve(BotRoster.class);
			for (var bot : roster.list()) {
				roster.remove(bot);
			}
			return null;
		});
	}

	@Test
	void operatorAddsListsAndRemovesBots() throws Exception {
		// A bot already named Bot_1 forces the command to pick other names
		PaperServer.onMain(() -> Ioc.resolve(BotRoster.class).spawn("Bot_1",
				Bukkit.getWorlds().getFirst().getSpawnLocation().add(0.5, 0, 0.5)));

		// ADD
		var added = run("bot add 2 guard");
		assertEquals(List.of("Bot_2 ajouté (guard)", "Bot_3 ajouté (guard)"), added);
		PaperServer.awaitTicks(5);
		PaperServer.onMain(() -> {
			var bots = Ioc.resolve(ModuleManager.class).get(Vi6Bots.class);
			var roster = Ioc.resolve(BotRoster.class).list();
			assertEquals(List.of("Bot_1", "Bot_2", "Bot_3"), roster.stream().map(Bot::name).toList());
			assertEquals(BotTeamIntent.GUARD, bots.getIntent(roster.get(1)));
			assertEquals(BotTeamIntent.GUARD, bots.getIntent(roster.get(2)));
			var game = Ioc.resolve(Vi6Game.class);
			for (var bot : roster) {
				assertTrue(game.getPlayerList().contains(bot.player()), bot.name() + " did not join the game");
			}
			return null;
		});

		// LIST
		var listed = run("bot list");
		// The lobby balances joins (Bot_1 guard, Bot_2 thief, Bot_3 guard): a guard
		// intent alone puts no bot in a team
		assertEquals(List.of("Bot_1 - intention: aucune, équipe: guard, état: actif",
				"Bot_2 - intention: guard, équipe: thief, état: actif",
				"Bot_3 - intention: guard, équipe: guard, état: actif"), listed);

		// CAP
		var refused = run("bot add 4 any");
		assertEquals(1, refused.size());
		assertTrue(refused.getFirst().startsWith("Ajout refusé: la partie aurait 7 joueurs, 6 au maximum"),
				refused.getFirst());
		assertEquals(3, rosterSize());

		// REMOVE
		assertEquals(List.of("1 bot(s) retiré(s)"), run("bot remove bot_2"));
		PaperServer.awaitTicks(2);
		assertTrue(run("bot list").stream().noneMatch(l -> l.startsWith("Bot_2 ")));
		assertEquals(List.of("Aucun bot nommé Bot_2"), run("bot remove Bot_2"));
		assertEquals(List.of("2 bot(s) retiré(s)"), run("bot remove all"));
		PaperServer.awaitTicks(2);
		assertEquals(List.of("Aucun bot"), run("bot list"));
		PaperServer.onMain(() -> {
			assertFalse(Bukkit.getOnlinePlayers().stream().anyMatch(p -> p.getName().startsWith("Bot_")),
					"a removed bot is still online");
			return null;
		});
	}

	@Test
	void nonOpIsRefused() throws Exception {
		var bot = PaperServer.onMain(() -> Ioc.resolve(BotRoster.class).spawn("Bot_NonOp",
				Bukkit.getWorlds().getFirst().getSpawnLocation().add(0.5, 0, 0.5)));
		PaperServer.awaitTicks(2);
		PaperServer.onMain(() -> {
			var player = bot.player();
			assertFalse(player.isOp(), "the test bot is op");
			Bukkit.dispatchCommand(player, "bot add 1 any");
			Bukkit.dispatchCommand(player, "bot remove all");
			return null;
		});
		PaperServer.awaitTicks(2);
		assertEquals(1, rosterSize());
	}

	private static int rosterSize() throws Exception {
		return PaperServer.onMain(() -> Ioc.resolve(BotRoster.class).list().size());
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
