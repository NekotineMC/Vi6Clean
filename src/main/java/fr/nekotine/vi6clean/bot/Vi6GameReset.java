package fr.nekotine.vi6clean.bot;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;

import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.configuration.ConfigManager;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;
import fr.nekotine.vi6clean.wrapper.LobbyPhasePlayerWrapper;

/**
 * Puts the game back to a clean lobby: shared by the integration-test hooks and
 * Story 4.4's unattended run command.
 */
public final class Vi6GameReset {

	private Vi6GameReset() {
	}

	/**
	 * {@link #clearBots()}, then clears the KoTH override and reloads
	 * {@code config.yml} (undoing in-memory duration changes). The test hooks'
	 * reset.
	 *
	 * @throws IllegalStateException
	 *             off the main thread
	 */
	public static void reset() {
		clearBots();
		var game = Ioc.resolve(Vi6Game.class);
		game.setForcedKoths(null);
		Ioc.resolve(ConfigManager.class).tryLoad();
	}

	/**
	 * Aborts the active unattended run, removes every bot through the roster (which
	 * deletes their saved files), forgets their Vi6 brains, goes back to the lobby
	 * and resets the game scoreboard entries of the removed bots. Humans stay in
	 * the game, unready, so the bots' quits cannot start a game for the humans
	 * left; the live config and the KoTH override are kept. The run command's
	 * reset.
	 *
	 * @throws IllegalStateException
	 *             off the main thread
	 */
	public static void clearBots() {
		if (!Bukkit.isPrimaryThread()) {
			throw new IllegalStateException("Vi6GameReset must run on the main thread");
		}
		var modules = Ioc.resolve(ModuleManager.class);
		modules.get(Vi6Run.class).abort();
		var removedNames = new ArrayList<String>();
		var roster = Ioc.resolve(BotRoster.class);
		if (roster != null && roster.isAvailable()) {
			// Removed before the lobby, as a plain quit mid-round
			for (var bot : roster.list()) {
				removedNames.add(bot.name());
				roster.remove(bot);
			}
		}
		modules.get(Vi6Bots.class).forgetAllBut(List.of());
		var game = Ioc.resolve(Vi6Game.class);
		game.getPhaseMachine().goTo(Vi6PhaseLobby.class, null);
		var wrapping = Ioc.resolve(WrappingModule.class);
		for (var player : game.getPlayerList()) {
			wrapping.getWrapperOptional(player, LobbyPhasePlayerWrapper.class)
					.ifPresent(w -> w.setReadyForNextPhase(false));
		}
		var scoreboard = game.getScoreboard();
		for (var name : removedNames) {
			scoreboard.resetScores(name);
			var team = scoreboard.getEntryTeam(name);
			if (team != null) {
				team.removeEntry(name);
			}
		}
	}
}
