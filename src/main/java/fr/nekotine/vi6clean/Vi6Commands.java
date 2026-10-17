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

	/**
	 * Only the read and the removal: {@code addBots} keeps its own wording, which
	 * names what an absent registry cost the operator, and the two new commands
	 * have their own because naming an addition there would be a lie.
	 */
	private static final String REGISTRY_UNAVAILABLE = "Le registre de bots n'est pas disponible pour cette "
			+ "session. Redémarrez le serveur avec Citizens installé et activé.";

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
	 * {@code /game bot add <count> [guard|thief]}, {@code /game bot list} and
	 * {@code /game bot remove [<count>] [guard|thief]}.
	 * <p>
	 * The count is bounded by the argument itself, so {@code 0}, a negative number
	 * and a non number are a syntax error and never reach the roster: nothing
	 * partial is ever created, and nothing partial is ever destroyed. Both of
	 * {@code remove}'s arguments are optional because CommandAPI refuses a required
	 * argument after an optional one, and that is also what makes the bare form
	 * mean "all of them".
	 */
	private static CommandAPICommand botCommands() {
		var bot = new CommandAPICommand("bot");
		var add = new CommandAPICommand("add")
				.withArguments(
						new IntegerArgument("count", BotService.MIN_BOTS_PER_COMMAND, BotService.MAX_BOTS_PER_COMMAND))
				.withOptionalArguments(new MultiLiteralArgument("teamName", "guard", "thief"))
				.executes(Vi6Commands::addBots, ExecutorType.ALL);
		var list = new CommandAPICommand("list").executes(Vi6Commands::listBots, ExecutorType.ALL);
		// Same two argument kinds as add, same order, so the two commands read alike:
		// remove is every bot, remove 3 is the three newest, remove 3 thief the three
		// newest thieves.
		var remove = new CommandAPICommand("remove")
				.withOptionalArguments(
						new IntegerArgument("count", BotService.MIN_BOTS_PER_COMMAND, BotService.MAX_BOTS_PER_COMMAND))
				.withOptionalArguments(new MultiLiteralArgument("teamName", "guard", "thief"))
				.executes(Vi6Commands::removeBots, ExecutorType.ALL);
		bot.withSubcommands(add, list, remove);
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

	/**
	 * The read-only half of the roster: what is in it, on which team, and whether
	 * Citizens currently has it spawned. Gates and resolves exactly like
	 * {@link #addBots}, for the same reason: this class is linked at
	 * {@code onLoad}, long before {@code onEnable} could have checked anything.
	 */
	private static void listBots(CommandSender sender, CommandArguments args) {
		var availability = CitizensAvailability.check();
		if (availability != CitizensAvailability.READY) {
			say(sender, availability.describe(), NamedTextColor.RED);
			return;
		}
		Ioc.getProvider().tryResolve(BotService.class).ifPresentOrElse(service -> {
			var report = service.listBots();
			say(sender, report.header(), NamedTextColor.GREEN);
			for (var bot : report.bots()) {
				say(sender, bot.describe(), NamedTextColor.WHITE);
			}
		}, () -> say(sender, REGISTRY_UNAVAILABLE, NamedTextColor.RED));
	}

	/**
	 * The destructive half. There is no phase gate here, unlike {@code add}: taking
	 * a bot out mid round has to be possible, because a roster that grew in the
	 * lobby is exactly the thing an operator may want to shrink once the game is
	 * running.
	 */
	private static void removeBots(CommandSender sender, CommandArguments args) {
		var availability = CitizensAvailability.check();
		if (availability != CitizensAvailability.READY) {
			say(sender, availability.describe(), NamedTextColor.RED);
			return;
		}
		Ioc.getProvider().tryResolve(BotService.class).ifPresentOrElse(service -> {
			// A bare remove is the whole roster. That count is the roster's own size rather
			// than MAX_BOTS_PER_COMMAND, because add is not capped and a roster can be far
			// wider than one request.
			var optionalCount = args.getOptional("count");
			var count = optionalCount.isPresent() ? (Integer) optionalCount.get() : service.size();
			var teamName = args.getOptional("teamName").map(Object::toString).orElse(null);
			say(sender, service.removeBots(count, teamName).describe(), NamedTextColor.GREEN);
		}, () -> say(sender, REGISTRY_UNAVAILABLE, NamedTextColor.RED));
	}

	private static void say(CommandSender sender, String message, NamedTextColor color) {
		sender.sendMessage(Component.text(message, color));
	}

}
