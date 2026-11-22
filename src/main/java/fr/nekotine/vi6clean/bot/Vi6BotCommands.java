package fr.nekotine.vi6clean.bot;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.LongArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.bot.BotState;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public class Vi6BotCommands {

	public static final int MAX_PLAYERS = 6;

	private static final ComponentLogger logger = NekotineLogger.make();

	public static void register() {
		var add = new CommandAPICommand("add").withPermission(CommandPermission.OP)
				.withArguments(new IntegerArgument("count", 1, MAX_PLAYERS),
						new MultiLiteralArgument("intent", "guard", "thief", "any"))
				.executes((sender, args) -> {
					var roster = availableRoster(sender);
					if (roster == null) {
						return;
					}
					var count = (int) args.get("count");
					var intent = BotTeamIntent.fromCode((String) args.get("intent"));
					// The lobby puts every online player in the game
					var players = Bukkit.getOnlinePlayers().size();
					if (players + count > MAX_PLAYERS) {
						sender.sendMessage(
								Component.text(
										"Ajout refusé: la partie aurait " + (players + count) + " joueurs, "
												+ MAX_PLAYERS + " au maximum (" + players + " en ligne)",
										NamedTextColor.RED));
						return;
					}
					var bots = Ioc.resolve(ModuleManager.class).get(Vi6Bots.class);
					bots.forgetAllBut(roster.list());
					for (var name : spawnBots(sender, roster, bots, count, intent)) {
						sender.sendMessage(
								Component.text(name + " ajouté (" + intent.code() + ")", NamedTextColor.GREEN));
					}
				});
		var run = new CommandAPICommand("run").withPermission(CommandPermission.OP)
				.withArguments(new IntegerArgument("count")).withOptionalArguments(new LongArgument("seed"))
				.executes((sender, args) -> {
					var roster = availableRoster(sender);
					if (roster == null) {
						return;
					}
					var count = (int) args.get("count");
					if (count != 4 && count != 6) {
						sender.sendMessage(Component.text("Run refusé: " + count + " bots demandés, 4 ou 6 seulement",
								NamedTextColor.RED));
						return;
					}
					var game = Ioc.resolve(Vi6Game.class);
					if (game.getPhaseMachine().getPhase(Vi6PhaseLobby.class) == null) {
						sender.sendMessage(
								Component.text("Run refusé: la partie n'est pas dans le lobby", NamedTextColor.RED));
						return;
					}
					// The reset removes bots only: humans stay and take places
					var rosterPlayers = roster.list().stream().map(Bot::player).toList();
					var humans = (int) Bukkit.getOnlinePlayers().stream().filter(p -> !rosterPlayers.contains(p))
							.count();
					var room = MAX_PLAYERS - humans;
					if (room <= 0) {
						sender.sendMessage(Component.text("Run refusé: aucune place pour un bot (" + humans
								+ " joueurs en ligne, " + MAX_PLAYERS + " au maximum)", NamedTextColor.RED));
						return;
					}
					var toAdd = Math.min(count, room);
					// Today the seed replays the bots' picks only (KoTH placement and effects are
					// unseeded); later it will drive more of a run, like KoTH map generation
					var seed = args.getOptional("seed").map(Long.class::cast).orElseGet(System::nanoTime);
					Vi6GameReset.clearBots();
					var modules = Ioc.resolve(ModuleManager.class);
					var bots = modules.get(Vi6Bots.class);
					bots.seedRandom(seed);
					var added = spawnBots(sender, roster, bots, toAdd, BotTeamIntent.ANY);
					if (added.size() < toAdd) {
						// A partial run is not repeatable: back to a clean roster
						Vi6GameReset.clearBots();
						sender.sendMessage(Component.text(
								"Run annulé: " + added.size() + "/" + toAdd + " bots ajoutés, roster vidé",
								NamedTextColor.RED));
						return;
					}
					modules.get(Vi6Run.class).begin(count, added.size(), seed);
					game.start();
					sender.sendMessage(
							Component.text(
									"Run démarré: " + added.size() + " bot(s) ajouté(s) sur " + count
											+ " demandés, seed " + seed + " (rejoue les choix des bots, pas les KoTH)",
									NamedTextColor.GREEN));
				});
		var list = new CommandAPICommand("list").withPermission(CommandPermission.OP).executes((sender, _) -> {
			var roster = availableRoster(sender);
			if (roster == null) {
				return;
			}
			var rosterBots = roster.list();
			if (rosterBots.isEmpty()) {
				sender.sendMessage(Component.text("Aucun bot"));
				return;
			}
			var bots = Ioc.resolve(ModuleManager.class).get(Vi6Bots.class);
			bots.forgetAllBut(rosterBots);
			var game = Ioc.resolve(Vi6Game.class);
			for (var bot : rosterBots) {
				var intent = bots.getIntent(bot);
				var player = bot.player();
				var team = game.getGuards().contains(player)
						? "guard"
						: game.getThiefs().contains(player) ? "thief" : "aucune";
				var state = bot.state() == BotState.FAILED ? "en échec" : "actif";
				sender.sendMessage(Component.text(bot.name() + " - intention: "
						+ (intent == null ? "aucune" : intent.code()) + ", équipe: " + team + ", état: " + state));
			}
		});
		var remove = new CommandAPICommand("remove").withPermission(CommandPermission.OP)
				.withArguments(new StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings(_ -> {
					var roster = Ioc.resolve(BotRoster.class);
					var names = new LinkedList<String>();
					names.add("all");
					if (roster != null && roster.isAvailable()) {
						roster.list().forEach(b -> names.add(b.name()));
					}
					return names.toArray(String[]::new);
				}))).executes((sender, args) -> {
					var roster = availableRoster(sender);
					if (roster == null) {
						return;
					}
					var name = (String) args.get("name");
					var matching = roster.list().stream()
							.filter(b -> name.equalsIgnoreCase("all") || b.name().equalsIgnoreCase(name)).toList();
					if (matching.isEmpty()) {
						sender.sendMessage(Component.text("Aucun bot nommé " + name, NamedTextColor.RED));
						return;
					}
					for (var bot : matching) {
						roster.remove(bot);
					}
					Ioc.resolve(ModuleManager.class).get(Vi6Bots.class).forgetAllBut(roster.list());
					sender.sendMessage(Component.text(matching.size() + " bot(s) retiré(s)", NamedTextColor.GREEN));
				});
		new CommandAPICommand("bot").withPermission(CommandPermission.OP).withSubcommands(add, list, remove, run)
				.register();
	}

	private static @Nullable BotRoster availableRoster(CommandSender sender) {
		var roster = Ioc.resolve(BotRoster.class);
		if (roster == null || !roster.isAvailable()) {
			sender.sendMessage(Component.text("Bots indisponibles (bots unavailable), voir le log du serveur",
					NamedTextColor.RED));
			return null;
		}
		return roster;
	}

	/**
	 * Spawns {@code count} bots named {@code Bot_n} (the first free numbers) at the
	 * main world's spawn, each with {@code intent}. Stops at the first failure,
	 * which it logs and reports to {@code sender}.
	 *
	 * @return the names of the bots spawned, in order
	 */
	private static List<String> spawnBots(CommandSender sender, BotRoster roster, Vi6Bots bots, int count,
			BotTeamIntent intent) {
		var spawned = new ArrayList<String>();
		var location = Bukkit.getWorlds().getFirst().getSpawnLocation().add(0.5, 0, 0.5);
		var index = 1;
		for (var i = 0; i < count; i++) {
			while (isNameTaken("Bot_" + index, roster)) {
				index++;
			}
			var name = "Bot_" + index;
			try {
				var bot = roster.spawn(name, location);
				bots.setIntent(bot, intent);
			} catch (RuntimeException e) {
				logger.error("Le bot " + name + " n'a pas pu rejoindre", e);
				sender.sendMessage(Component.text("Le bot " + name + " n'a pas pu rejoindre, voir le log du serveur",
						NamedTextColor.RED));
				break;
			}
			spawned.add(name);
		}
		return spawned;
	}

	private static boolean isNameTaken(String name, BotRoster roster) {
		return Bukkit.getPlayerExact(name) != null
				|| roster.list().stream().anyMatch(b -> b.name().equalsIgnoreCase(name));
	}
}
