package fr.nekotine.vi6clean.bot;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.Nullable;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.bot.BotTickEvent;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.module.IPluginModule;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.game.phase.Vi6PhasePreparation;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public class Vi6Bots implements IPluginModule, Listener {

	private final ComponentLogger logger = NekotineLogger.make();

	private final Map<Bot, BotTeamIntent> intents = new HashMap<>();

	private final Map<Bot, BotBrain> brains = new HashMap<>();

	public Vi6Bots() {
		EventUtil.register(this);
	}

	/** Records the bot's team intent and hands it to a new lobby brain. */
	public void setIntent(Bot bot, BotTeamIntent intent) {
		intents.put(bot, intent);
		brains.put(bot, new LobbyBrain(intent));
	}

	public @Nullable BotTeamIntent getIntent(Bot bot) {
		return intents.get(bot);
	}

	/** Replaces the bot's brain; {@code null} leaves the bot idle. */
	public void setBrain(Bot bot, @Nullable BotBrain brain) {
		if (brain == null) {
			brains.remove(bot);
		} else {
			brains.put(bot, brain);
		}
	}

	// Kicked bots and lost connections leave the roster without a command
	public void forgetAllBut(Collection<Bot> rosterBots) {
		intents.keySet().retainAll(rosterBots);
		brains.keySet().retainAll(rosterBots);
	}

	/** @return the team a bot's lobby brain asked for in this lobby, if any */
	@Nullable BotTeamIntent pickedTeam(Player player) {
		for (var entry : brains.entrySet()) {
			if (entry.getValue() instanceof LobbyBrain lobby && entry.getKey().player().equals(player)) {
				return lobby.pickedTeam();
			}
		}
		return null;
	}

	// Every bot exit fires the quit, including removals that bypass /bot
	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		var player = event.getPlayer();
		intents.keySet().removeIf(b -> b.player().equals(player));
		brains.keySet().removeIf(b -> b.player().equals(player));
	}

	@EventHandler
	public void onBotTick(BotTickEvent event) {
		var bot = event.getBot();
		var brain = brains.get(bot);
		if (brain == null) {
			return;
		}
		try {
			brain.tick(bot);
		} catch (RuntimeException | LinkageError e) {
			brains.remove(bot);
			logger.error(bot.name() + " : cerveau arrêté après une erreur (équipe: " + team(bot) + ", phase: " + phase()
					+ ")", e);
		}
	}

	private static String team(Bot bot) {
		var game = Ioc.resolve(Vi6Game.class);
		var player = bot.player();
		return game.getGuards().contains(player) ? "guard" : game.getThiefs().contains(player) ? "thief" : "aucune";
	}

	private static String phase() {
		var machine = Ioc.resolve(Vi6Game.class).getPhaseMachine();
		for (var type : new Class<?>[]{Vi6PhaseInfiltration.class, Vi6PhasePreparation.class, Vi6PhaseLobby.class}) {
			if (machine.getPhase(type) != null) {
				return type.getSimpleName();
			}
		}
		return "aucune";
	}

	@Override
	public void unload() {
		EventUtil.unregister(this);
		intents.clear();
		brains.clear();
	}
}
