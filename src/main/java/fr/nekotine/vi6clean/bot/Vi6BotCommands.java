package fr.nekotine.vi6clean.bot;

import java.util.LinkedList;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.bot.BotState;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.vi6clean.game.Vi6Game;
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
							sender.sendMessage(
									Component.text("Le bot " + name + " n'a pas pu rejoindre, voir le log du serveur",
											NamedTextColor.RED));
							return;
						}
						sender.sendMessage(
								Component.text(name + " ajouté (" + intent.code() + ")", NamedTextColor.GREEN));
					}
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
		new CommandAPICommand("bot").withPermission(CommandPermission.OP).withSubcommands(add, list, remove).register();
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

	private static boolean isNameTaken(String name, BotRoster roster) {
		return Bukkit.getPlayerExact(name) != null
				|| roster.list().stream().anyMatch(b -> b.name().equalsIgnoreCase(name));
	}
}
