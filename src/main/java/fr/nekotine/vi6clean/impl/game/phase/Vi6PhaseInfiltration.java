package fr.nekotine.vi6clean.impl.game.phase;

import fr.nekotine.core.game.phase.CollectionPhase;
import fr.nekotine.core.game.phase.IPhaseMachine;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.collection.ObservableCollection;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.InMapState;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.map.ThiefSpawn;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;
import fr.nekotine.vi6clean.impl.map.artefact.ArtefactStealEvent;
import fr.nekotine.vi6clean.impl.wrapper.InMapPhasePlayerWrapper;
import fr.nekotine.vi6clean.impl.wrapper.InfiltrationPhasePlayerWrapper;
import fr.nekotine.vi6clean.impl.wrapper.InMapPhasePlayerWrapper.LeaveState;
import io.papermc.paper.util.Tick;
import java.time.Duration;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.GameMode;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class Vi6PhaseInfiltration extends CollectionPhase<Vi6PhaseInMap, Player> implements Listener {

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
	private final int GAME_INFILTRATION_LOST_SECONDS;
	private int stealDurationTicks = 0;
	private boolean isHandlingCompletion = false;

	public Vi6PhaseInfiltration(IPhaseMachine machine) {
		super(machine);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
		EventUtil.register(this);
		GAME_INFILTRATION_LOST_SECONDS = 20
				* Ioc.resolve(Configuration.class).getInt("game_infitration_lost_seconds", 5 * 60);

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
		getParent().getMap().getArtefacts().values().forEach(Artefact::unglow);
		game.getThiefs().forEach(p -> p.showBossBar(bossbarThief));
		game.getGuards().forEach(p -> p.showBossBar(bossbarGuard));
	}

	@Override
	protected void globalTearDown() {
		var game = Ioc.resolve(Vi6Game.class);
		game.getThiefs().forEach(p -> p.hideBossBar(bossbarThief));
		game.getGuards().forEach(p -> p.hideBossBar(bossbarGuard));
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
				.allMatch(thief -> wrappingModule.getWrapper(thief, InMapPhasePlayerWrapper.class).hasLeft())) {
			if (isHandlingCompletion) {
				return;
			}
			isHandlingCompletion = true;
			game.sendMessage(Component.text("La partie est finie", NamedTextColor.GOLD));
			game.showTitle(Title.title(Component.text("Fin de partie", NamedTextColor.GOLD), Component.empty(),
					Times.times(Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(1))));
			var spectatorTimeSeconds = Duration
					.ofSeconds(Ioc.resolve(Configuration.class).getLong("game_end_spectator_time", 5));
			new BukkitRunnable() {

				@Override
				public void run() {
					complete();
				}
			}.runTaskLater(Ioc.resolve(JavaPlugin.class), Tick.tick().fromDuration(spectatorTimeSeconds));
		}
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
		if (stealDurationTicks >= GAME_INFILTRATION_LOST_SECONDS) {
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
			float progress = Math.clamp(stealDurationTicks / GAME_INFILTRATION_LOST_SECONDS, 0f, 1f);
			bossbarGuard.progress(progress);
			bossbarThief.progress(progress);
		}
	}
}
