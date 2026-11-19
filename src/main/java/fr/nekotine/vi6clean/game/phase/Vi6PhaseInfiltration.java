package fr.nekotine.vi6clean.game.phase;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import fr.nekotine.core.game.phase.CollectionPhase;
import fr.nekotine.core.game.phase.IPhaseMachine;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.state.RegisteredEventListenerState;
import fr.nekotine.core.state.State;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.collection.ObservableCollection;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.configuration.ConfigManager;
import fr.nekotine.vi6clean.constant.InMapState;
import fr.nekotine.vi6clean.game.Vi6Game;
import fr.nekotine.vi6clean.map.ThiefSpawn;
import fr.nekotine.vi6clean.map.artefact.Artefact;
import fr.nekotine.vi6clean.map.artefact.ArtefactStealEvent;
import fr.nekotine.vi6clean.wrapper.InMapPhasePlayerWrapper;
import fr.nekotine.vi6clean.wrapper.InMapPhasePlayerWrapper.LeaveState;
import fr.nekotine.vi6clean.wrapper.InfiltrationPhasePlayerWrapper;
import io.papermc.paper.util.Tick;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;

public class Vi6PhaseInfiltration extends CollectionPhase<Vi6PhaseInMap, Player> implements Listener {

	private final ComponentLogger logger = NekotineLogger.make();

	private final BossBar bossbarGuard = BossBar.bossBar(
			Component.text("Infiltration", NamedTextColor.LIGHT_PURPLE)
					.append(Component.text(" - ", NamedTextColor.WHITE)).append(Component
							.text("Défendez les artefacts", NamedTextColor.GRAY).decorate(TextDecoration.ITALIC)),
			0, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
	private final BossBar bossbarThief = BossBar.bossBar(
			Component.text("Infiltration", NamedTextColor.LIGHT_PURPLE)
					.append(Component.text(" - ", NamedTextColor.WHITE)).append(Component
							.text("Volez puis échappez vous", NamedTextColor.GRAY).decorate(TextDecoration.ITALIC)),
			0, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
	private final int infiltration_lost_duration_ticks = Tick.tick()
			.fromDuration(Ioc.resolve(ConfigManager.class).getConfig().game().infiltration().lostDuration());
	private final int endSpectatorTicks = Tick.tick()
			.fromDuration(Ioc.resolve(ConfigManager.class).getConfig().game().infiltration().endSpectatorTime());
	private int stealDurationTicks = 0;
	private boolean isHandlingCompletion = false;
	// Why the round ends, set when completion starts
	private boolean endedOnLostTimer;
	// Ends the round after the end-spectator time; a teardown before then cancels
	// it
	private @Nullable BukkitTask completionTask;
	// Pending death delays and the tick each is due. Kept by the phase, not the
	// player, so a dead thief who quits still has his artefacts lost and is waited
	// for
	private final Map<BukkitRunnable, Integer> pendingDeathDelays = new LinkedHashMap<>();

	public Vi6PhaseInfiltration(IPhaseMachine machine) {
		super(machine);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
	}

	@Override
	public Class<Vi6PhaseInMap> getParentType() {
		return Vi6PhaseInMap.class;
	}

	@Override
	public ObservableCollection<Player> getItemCollection() {
		return Ioc.resolve(Vi6Game.class).getPlayerList();
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void globalSetup(Object inputData) {
		var game = Ioc.resolve(Vi6Game.class);
		game.getThiefs().spawnInMap((Map<Player, ThiefSpawn>) inputData);
		game.sendMessage(Component.text("La phase d'infiltration débute.", NamedTextColor.GOLD));
		game.getThiefs().forEach(p -> p.showBossBar(bossbarThief));
		game.getGuards().forEach(p -> p.showBossBar(bossbarGuard));
	}

	@Override
	protected void globalTearDown() {
		var game = Ioc.resolve(Vi6Game.class);
		game.getThiefs().forEach(p -> p.hideBossBar(bossbarThief));
		game.getGuards().forEach(p -> p.hideBossBar(bossbarGuard));
		if (completionTask != null) {
			completionTask.cancel();
			completionTask = null;
		}
		// The round is over: a death delay left must not touch the next one
		for (var task : pendingDeathDelays.keySet()) {
			task.cancel();
		}
		pendingDeathDelays.clear();
	}

	/**
	 * Runs {@code action} after {@code delayTicks}, unless the phase is torn down
	 * first. The round does not end before it runs.
	 */
	public void scheduleDeathDelay(Runnable action, int delayTicks) {
		var task = new BukkitRunnable() {

			@Override
			public void run() {
				pendingDeathDelays.remove(this);
				action.run();
			}
		};
		task.runTaskLater(Ioc.resolve(JavaPlugin.class), delayTicks);
		pendingDeathDelays.put(task, Bukkit.getCurrentTick() + delayTicks);
	}

	/**
	 * @return the ticks left before the last pending death delay runs, 0 if none
	 */
	public int getPendingDeathDelayTicks() {
		var now = Bukkit.getCurrentTick();
		return pendingDeathDelays.values().stream().mapToInt(due -> Math.max(0, due - now)).max().orElse(0);
	}

	@Override
	protected List<State> makeAppliedStates() {
		var list = new LinkedList<State>();
		list.add(new RegisteredEventListenerState(this));
		return list;
	}

	@Override
	public void itemSetup(Player item) {
		Ioc.resolve(WrappingModule.class).makeWrapper(item, InfiltrationPhasePlayerWrapper::new);
		item.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30, 20, false, false, true));
	}

	@Override
	public void itemTearDown(Player item) {
		item.spigot().respawn();
		item.getInventory().clear();
		Ioc.resolve(WrappingModule.class).removeWrapper(null, InfiltrationPhasePlayerWrapper.class);
		item.setGameMode(GameMode.ADVENTURE);
	}

	public void setIngameScannerDelay() {
	}

	@Override
	protected Object handleComplete() {
		var game = Ioc.resolve(Vi6Game.class);
		for (var player : game.getPlayerList()) {
			player.setGameMode(GameMode.SPECTATOR);
		}
		return null;
	}

	public void checkForCompletion() {
		if (isHandlingCompletion) {
			return;
		}
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var game = Ioc.resolve(Vi6Game.class);
		if (game.getThiefs().stream()
				.allMatch(thief -> wrappingModule.getWrapper(thief, InMapPhasePlayerWrapper.class).hasLeft())
				|| stealDurationTicks >= infiltration_lost_duration_ticks) {
			if (isHandlingCompletion) {
				return;
			}
			isHandlingCompletion = true;
			endedOnLostTimer = stealDurationTicks >= infiltration_lost_duration_ticks;
			game.sendMessage(Component.text("La partie est finie", NamedTextColor.GOLD));
			game.showTitle(Title.title(Component.text("Fin de partie", NamedTextColor.GOLD), Component.empty(),
					Times.times(Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(1))));
			// After the pending death delays, so their artefacts are lost first
			var delay = endSpectatorTicks;
			if (!pendingDeathDelays.isEmpty()) {
				delay = Math.max(delay, getPendingDeathDelayTicks() + 1);
			}
			completionTask = new BukkitRunnable() {

				@Override
				public void run() {
					completionTask = null;
					// Logged now: leaves later in the same tick and pending deaths are on the
					// check-list
					logThenComplete(() -> logRoundResult(endedOnLostTimer), Vi6PhaseInfiltration.this::complete,
							e -> logger.warn("Could not log the round result", e));
				}
			}.runTaskLater(Ioc.resolve(JavaPlugin.class), delay);
		}
	}

	/**
	 * Runs {@code log}, then {@code complete} whatever {@code log} throws: an
	 * exception goes to {@code warn}, an {@link Error} is rethrown after
	 * {@code complete} ran.
	 */
	static void logThenComplete(Runnable log, Runnable complete, Consumer<Exception> warn) {
		try {
			log.run();
		} catch (Exception e) {
			warn.accept(e);
		} finally {
			complete.run();
		}
	}

	/**
	 * Logs the round's result on one line: why it ended and each artefact's state,
	 * read from the thieves' check-list. Then fires {@link Vi6RoundEndEvent}.
	 */
	private void logRoundResult(boolean lostTimer) {
		var inMap = getParent();
		var scores = new LinkedHashMap<String, Integer>();
		inMap.getMap().getArtefacts().values().stream().sorted(Comparator.comparing(Artefact::getName))
				.forEach(a -> scores.put(a.getName(), inMap.getThiefArtefactScore(a)));
		var artefacts = scores.entrySet().stream().map(e -> e.getKey() + "=" + artefactState(e.getValue()))
				.collect(Collectors.joining(", "));
		var reason = lostTimer ? Vi6RoundEndEvent.Reason.LOST_TIMER : Vi6RoundEndEvent.Reason.ALL_THIEVES_LEFT;
		logger.info("Round ended: reason=" + reason.code() + ", artefacts=[" + artefacts + "]");
		EventUtil.call(new Vi6RoundEndEvent(reason, scores));
	}

	private static String artefactState(int thiefScore) {
		return switch (thiefScore) {
			case 3 -> "safe";
			case 2 -> "stolen";
			case 1 -> "escaped";
			case 0 -> "lost";
			default -> "unknown(" + thiefScore + ")";
		};
	}

	@EventHandler
	protected void onArtefactSteal(ArtefactStealEvent evt) {
		stealDurationTicks = 0;
	}

	@EventHandler
	protected void onTick(TickElapsedEvent evt) {
		if (isHandlingCompletion) {
			return;
		}
		stealDurationTicks++;
		if (stealDurationTicks >= infiltration_lost_duration_ticks) {
			var game = Ioc.resolve(Vi6Game.class);
			var wrappingModule = Ioc.resolve(WrappingModule.class);
			for (var thief : game.getThiefs()) {
				var inMapWrap = wrappingModule.getWrapper(thief, InMapPhasePlayerWrapper.class);
				if (inMapWrap.getState() == InMapState.ENTERING || inMapWrap.isInside()) {
					inMapWrap.thiefLeaveMap(LeaveState.LOST);
				}
			}
		}
		if (evt.timeStampReached(TickTimeStamp.Second)) {
			float progress = Math.clamp((float) stealDurationTicks / (float) infiltration_lost_duration_ticks, 0f, 1f);
			bossbarGuard.progress(progress);
			bossbarThief.progress(progress);
		}
	}
}
