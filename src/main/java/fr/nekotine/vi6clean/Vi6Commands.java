package fr.nekotine.vi6clean;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.executors.ExecutorType;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.wrapper.LobbyPhasePlayerWrapper;

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
		gameC.withSubcommands(lobby, team, ready);
		gameC.register();
	}

}
