package fr.nekotine.vi6clean.bot;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;

import fr.nekotine.core.bot.BotRoster;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.vi6clean.configuration.ConfigManager;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseLobby;

/**
 * Puts the game back to a clean lobby: shared by the integration-test hooks and
 * Story 4.4's unattended run command.
 */
public final class Vi6GameReset {

	private Vi6GameReset() {
	}

	/**
	 * Removes every bot through the roster (which deletes their saved files),
	 * forgets their Vi6 brains, goes back to the lobby, clears the KoTH override,
	 * reloads {@code config.yml} (undoing in-memory duration changes) and resets
	 * the game scoreboard entries of the removed bots. Humans stay in the game.
	 *
	 * @throws IllegalStateException
	 *             off the main thread
	 */
	public static void reset() {
		if (!Bukkit.isPrimaryThread()) {
			throw new IllegalStateException("Vi6GameReset.reset() must run on the main thread");
		}
		var removedNames = new ArrayList<String>();
		var roster = Ioc.resolve(BotRoster.class);
		if (roster != null && roster.isAvailable()) {
			// Removed before the lobby, as a plain quit mid-round
			for (var bot : roster.list()) {
				removedNames.add(bot.name());
				roster.remove(bot);
			}
		}
		Ioc.resolve(ModuleManager.class).get(Vi6Bots.class).forgetAllBut(List.of());
		var game = Ioc.resolve(Vi6Game.class);
		game.getPhaseMachine().goTo(Vi6PhaseLobby.class, null);
		game.setForcedKoths(null);
		Ioc.resolve(ConfigManager.class).tryLoad();
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
