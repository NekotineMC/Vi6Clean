package fr.nekotine.vi6clean.bot;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.module.IPluginModule;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.game.phase.Vi6PhaseInfiltration;
import fr.nekotine.vi6clean.game.phase.Vi6RoundEndEvent;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

/**
 * The unattended run started by {@code /bot run}: logs one summary line for
 * each of its games once the game is back in the lobby. Games loop until the
 * next run or a reset, which {@link #abort() aborts} it.
 */
public class Vi6Run implements IPluginModule, Listener {

	private final ComponentLogger logger = NekotineLogger.make();

	private @Nullable Run active;

	public Vi6Run() {
		EventUtil.register(this);
	}

	private static final class Run {

		private final int requested;

		private final int added;

		private final long seed;

		private int games;

		// The command, then each lobby return
		private long gameStartNanos = System.nanoTime();

		private Run(int requested, int added, long seed) {
			this.requested = requested;
			this.added = added;
			this.seed = seed;
		}
	}

	/**
	 * Starts tracking a run, replacing any active one silently, and logs its start
	 * line.
	 */
	public void begin(int requested, int added, long seed) {
		active = new Run(requested, added, seed);
		logger.info("Run started: requested=" + requested + ", added=" + added + ", seed=" + seed);
	}

	/** Forgets the active run, if any: its game in progress logs nothing. */
	public void abort() {
		active = null;
	}

	/** @return whether a run is active */
	public boolean isActive() {
		return active != null;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onRoundEnd(Vi6RoundEndEvent event) {
		var run = active;
		if (run == null) {
			return;
		}
		var endNanos = System.nanoTime();
		var stolen = event.getArtefactScores().values().stream().filter(s -> s < 3).count();
		var escaped = event.getArtefactScores().values().stream().filter(s -> s == 1).count();
		var reason = event.getReason().code();
		// The phase goes back to the lobby right after the event, in the same tick
		Bukkit.getScheduler().runTask(Ioc.resolve(JavaPlugin.class), () -> {
			if (active != run
					|| Ioc.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhaseInfiltration.class) != null) {
				return;
			}
			run.games++;
			var seconds = (endNanos - run.gameStartNanos) / 1_000_000_000L;
			run.gameStartNanos = endNanos;
			logger.info("Game ended: game=" + run.games + ", reason=" + reason + ", stolen=" + stolen + ", escaped="
					+ escaped + ", duration=" + seconds + "s, seed=" + run.seed);
		});
	}

	@Override
	public void unload() {
		EventUtil.unregister(this);
		active = null;
	}
}
