package fr.nekotine.vi6clean;

import org.bukkit.command.CommandSender;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.executors.ExecutorType;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.bot.BotService;
import fr.nekotine.vi6clean.bot.CitizensAvailability;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.wrapper.LobbyPhasePlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Vi6Commands {

	public static void registerCommands() {
		gameCommands();
	}

	private static void gameCommands() {
		var gameC = new CommandAPICommand("game");
		var lobby = new CommandAPICommand("lobby").executes(_ -> {
			Ioc.resolve(Vi6Game.class).start();
		}, ExecutorType.ALL);
		var team = new CommandAPICommand("team").withArguments(new MultiLiteralArgument("teamName", "guard", "thief"))
				.executesPlayer((p, args) -> {
					var game = Ioc.resolve(Vi6Game.class);
					switch ((String) args.get("teamName")) {
						case "guard" :
							game.addPlayerInGuards(p);
							break;
						case "thief" :
							game.addPlayerInThiefs(p);
							break;
					}
				});
		var ready = new CommandAPICommand("ready").executesPlayer(info -> {
			var p = info.sender();
			var wrapOpt = Ioc.resolve(WrappingModule.class).getWrapperOptional(p, LobbyPhasePlayerWrapper.class);
			if (wrapOpt.isPresent()) {
				var wrap = wrapOpt.get();
				wrap.setReadyForNextPhase(!wrap.isReadyForNextPhase());
			}
		});
		gameC.withSubcommands(lobby, team, ready, botCommands());
		gameC.register();
	}

	/**
	 * {@code /game bot add <count> [guard|thief]}. The count is bounded by the
	 * argument itself, so {@code 0}, a negative number and a non number are a
	 * syntax error and never reach the roster: nothing partial is ever created.
	 */
	private static CommandAPICommand botCommands() {
		var bot = new CommandAPICommand("bot");
		var add = new CommandAPICommand("add")
				.withArguments(
						new IntegerArgument("count", BotService.MIN_BOTS_PER_COMMAND, BotService.MAX_BOTS_PER_COMMAND))
				.withOptionalArguments(new MultiLiteralArgument("teamName", "guard", "thief"))
				.executes(Vi6Commands::addBots, ExecutorType.ALL);
		bot.withSubcommand(add);
		return bot;
	}

	/**
	 * Runs on the server thread, after the parser has already refused an out of
	 * range count. Citizens is only looked up here, never at registration time:
	 * commands register in {@code onLoad}, which links this class before
	 * {@code onEnable} has had a chance to check anything.
	 * <p>
	 * The service is resolved with {@code tryResolve}, never {@code resolve},
	 * because availability and registration are two independent signals: Citizens
	 * can be enabled now while the roster was never built, either because it was
	 * unusable at boot or because loading it failed. Resolving would throw instead
	 * of explaining.
	 */
	private static void addBots(CommandSender sender, CommandArguments args) {
		var availability = CitizensAvailability.check();
		if (availability != CitizensAvailability.READY) {
			say(sender, availability.describe(), NamedTextColor.RED);
			return;
		}
		var count = (Integer) args.get("count");
		var teamName = args.getOptional("teamName").map(Object::toString).orElse(null);
		Ioc.getProvider().tryResolve(BotService.class).ifPresentOrElse(
				service -> say(sender, service.addBots(count, teamName).describe(), NamedTextColor.GREEN),
				() -> say(sender,
						"Le registre de bots n'est pas disponible pour cette session, aucun bot n'a été "
								+ "ajouté. Redémarrez le serveur avec Citizens installé et activé.",
						NamedTextColor.RED));
	}

	private static void say(CommandSender sender, String message, NamedTextColor color) {
		sender.sendMessage(Component.text(message, color));
	}

}
