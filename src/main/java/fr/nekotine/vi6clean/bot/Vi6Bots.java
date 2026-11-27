package fr.nekotine.vi6clean.bot;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.bot.BotTickEvent;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.module.IPluginModule;
import fr.nekotine.core.navigation.ControlPriority;
import fr.nekotine.core.navigation.PathFinder;
import fr.nekotine.core.navigation.astar.AStarPathFinder;
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

	// One source for every brain: bots tick in roster order, so a seed gives the
	// same picks
	private final Random random = new Random();

	// One finder for every roaming brain, built on first use
	private @Nullable PathFinder finder;

	public Vi6Bots() {
		EventUtil.register(this);
	}

	/** Records the bot's team intent and hands it to a new standard brain. */
	public void setIntent(Bot bot, BotTeamIntent intent) {
		intents.put(bot, intent);
		swap(bot, PhaseBrain.standard(intent, random));
	}

	public @Nullable BotTeamIntent getIntent(Bot bot) {
		return intents.get(bot);
	}

	/** @return the bot's brain, or {@code null} if it has none */
	public @Nullable BotBrain getBrain(Bot bot) {
		return brains.get(bot);
	}

	/**
	 * Reseeds the random source of every brain's choices, today the thief bots'
	 * entrance picks and the roaming brains' area picks (team picks are
	 * deterministic). The rest of a game, KoTHs included, stays unseeded until a
	 * run's seed drives it too (planned, with KoTH map generation).
	 */
	public void seedRandom(long seed) {
		random.setSeed(seed);
	}

	/**
	 * @return a new roaming in-map brain for one bot, drawing from the shared
	 *         random source and searching with the shared path finder
	 */
	public RoamingBrain roamingBrain() {
		if (finder == null) {
			finder = new AStarPathFinder(Ioc.resolve(JavaPlugin.class));
		}
		return new RoamingBrain(random, finder);
	}

	/**
	 * Replaces the bot's brain, {@link BotBrain#halt halting} the one it replaces;
	 * {@code null} leaves the bot idle. Main thread only.
	 */
	public void setBrain(Bot bot, @Nullable BotBrain brain) {
		swap(bot, brain);
	}

	private void swap(Bot bot, @Nullable BotBrain brain) {
		var outgoing = brain == null ? brains.remove(bot) : brains.put(bot, brain);
		if (outgoing == null || outgoing == brain) {
			return;
		}
		try {
			outgoing.halt(bot);
		} catch (RuntimeException e) {
			logger.error(bot.name() + " : arrêt du cerveau remplacé en erreur", e);
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
			if (!entry.getKey().player().equals(player)) {
				continue;
			}
			var brain = entry.getValue();
			if (brain instanceof PhaseBrain phases) {
				brain = phases.lobby();
			}
			return brain instanceof LobbyBrain lobby ? lobby.pickedTeam() : null;
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
			release(bot, e);
			logger.error(bot.name() + " : cerveau arrêté après une erreur (équipe: " + team(bot) + ", phase: " + phase()
					+ ")", e);
		}
	}

	// A failed brain may leave keys held: release movement this tick, above any
	// strategy
	private static void release(Bot bot, Throwable failure) {
		try {
			var controls = bot.body().controls(ControlPriority.BRAIN);
			controls.setHeading(null);
			controls.setForward(false);
			controls.setBackward(false);
			controls.setLeft(false);
			controls.setRight(false);
			controls.setJump(false);
			controls.setSneak(false);
			controls.setSprint(false);
		} catch (RuntimeException e) {
			failure.addSuppressed(e);
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
		finder = null;
	}
}
