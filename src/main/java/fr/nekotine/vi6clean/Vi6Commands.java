package fr.nekotine.vi6clean;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.executors.ExecutorType;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.bot.Vi6BotCommands;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.Vi6PhasePreparation;
import fr.nekotine.vi6clean.map.ThiefSpawn;
import fr.nekotine.vi6clean.wrapper.LobbyPhasePlayerWrapper;
import fr.nekotine.vi6clean.wrapper.PlayerWrapper;
import fr.nekotine.vi6clean.wrapper.PreparationPhasePlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Vi6Commands {

	public static void registerCommands() {
		gameCommands();
		Vi6BotCommands.register();
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
		var entrance = new CommandAPICommand("entrance")
				.withArguments(new GreedyStringArgument("name")
						.replaceSuggestions(ArgumentSuggestions.strings(_ -> entranceNames().toArray(String[]::new))))
				.executesPlayer((p, args) -> {
					selectEntrance(p, (String) args.get("name"));
				});
		gameC.withSubcommands(lobby, team, ready, entrance);
		gameC.register();
	}

	/**
	 * @return the entrance names of the running preparation's map, sorted, or
	 *         nothing outside preparation; main thread only
	 */
	public static List<String> entranceNames() {
		var spawns = preparationThiefSpawns();
		return spawns == null ? List.of() : spawns.keySet().stream().sorted().toList();
	}

	private static @Nullable Map<String, ThiefSpawn> preparationThiefSpawns() {
		var preparation = Ioc.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhasePreparation.class);
		return preparation == null ? null : preparation.getParent().getMap().getThiefSpawns();
	}

	/**
	 * Selects {@code name} as {@code player}'s entrance, as clicking its minimap
	 * ArmorStand does; refuses with a red message outside preparation, for a guard
	 * and for an unknown name.
	 */
	private static void selectEntrance(Player player, String name) {
		var wrapping = Ioc.resolve(WrappingModule.class);
		var spawns = preparationThiefSpawns();
		var prepWrapper = wrapping.getWrapperOptional(player, PreparationPhasePlayerWrapper.class);
		if (spawns == null || prepWrapper.isEmpty()) {
			player.sendMessage(Component.text("Une entrée se choisit seulement pendant la préparation de la partie",
					NamedTextColor.RED));
			return;
		}
		if (!wrapping.getWrapper(player, PlayerWrapper.class).isThief()) {
			player.sendMessage(Component.text("Seuls les voleurs choisissent une entrée", NamedTextColor.RED));
			return;
		}
		var spawn = spawns.get(name);
		if (spawn == null) {
			player.sendMessage(Component.text("Entrée inconnue: " + name + ". Entrées: "
					+ String.join(", ", spawns.keySet().stream().sorted().toList()), NamedTextColor.RED));
			return;
		}
		prepWrapper.get().setSelectedSpawn(spawn);
	}

}
