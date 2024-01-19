package fr.nekotine.vi6clean.impl.game.phase;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.GameMode;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import fr.nekotine.core.game.phase.CollectionPhase;
import fr.nekotine.core.game.phase.IPhaseMachine;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.util.collection.ObservableCollection;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.map.ThiefSpawn;
import fr.nekotine.vi6clean.impl.map.koth.AbstractKothEffect;
import fr.nekotine.vi6clean.impl.map.koth.Koth;
import fr.nekotine.vi6clean.impl.map.koth.effect.EmpKothEffect;
import fr.nekotine.vi6clean.impl.map.koth.effect.LightKothEffect;
import fr.nekotine.vi6clean.impl.wrapper.InMapPhasePlayerWrapper;
import fr.nekotine.vi6clean.impl.wrapper.InfiltrationPhasePlayerWrapper;
import io.papermc.paper.util.Tick;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;

public class Vi6PhaseInfiltration extends CollectionPhase<Vi6PhaseInMap, Player> {

	private Logger logger = new NekotineLogger(getClass());
	
	public Vi6PhaseInfiltration(IPhaseMachine machine) {
		super(machine);
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
		
		var world = game.getWorld();
		var map = getParent().getMap();
		List<AbstractKothEffect> kothEffects = new LinkedList<>(Arrays.asList(new EmpKothEffect(), new LightKothEffect()));
		List<Koth> koths = new LinkedList<>(map.getKoths().values());
		var limit = Ioc.resolve(JavaPlugin.class).getConfig().getInt("koth.limit", 0);
		Collections.shuffle(kothEffects);
		Collections.shuffle(koths);
		var random = new Random();
		var count = 0;
		while(count < limit && kothEffects.size() > 0 && koths.size() > 0) {
			count++;
			var effect = kothEffects.remove(0);
			var probability = effect.getProbability();
			if(random.nextDouble() > probability) {
				continue;
			}
			var koth = koths.remove(0);
			koth.setup(effect, world);
			logger.log(Level.INFO, "Spawning koth at "+koth.getBoundingBox().getCenter());
		}
	}

	@Override
	protected void globalTearDown() {
	}

	@Override
	public void itemSetup(Player item) {
		Ioc.resolve(WrappingModule.class).makeWrapper(item, InfiltrationPhasePlayerWrapper::new);
		item.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 30, 20, false, false, true));
	}

	@Override
	public void itemTearDown(Player item) {
		Ioc.resolve(WrappingModule.class).removeWrapper(null, InfiltrationPhasePlayerWrapper.class);
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
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var game = Ioc.resolve(Vi6Game.class);
		if (game.getThiefs().stream()
				.allMatch(guard -> !wrappingModule.getWrapper(guard, InMapPhasePlayerWrapper.class).isInside())) {
			try {
			game.sendMessage(Component.text("La partie est finie", NamedTextColor.GOLD));
			game.showTitle(Title.title(Component.text("Fin de partie", NamedTextColor.GOLD), Component.empty(),
					Times.times(Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(1))));
			var spectatorTimeSeconds = Duration
					.ofSeconds(Ioc.resolve(Configuration.class).getLong("game_end_spectator_time", 5));
			var inMapPhase = Ioc.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhaseInMap.class);
			var map = inMapPhase.getMap();
			var spawns = map.getGuardSpawns();
			if (spawns.size() < 1) {
				return;
			}
			new BukkitRunnable() {

				@Override
				public void run() {
					var spawnsIte = spawns.iterator();
					for(var player : getItemCollection()){
						var loc = spawnsIte.next();
						player.teleport(loc.toLocation(player.getWorld()));
						player.setGameMode(GameMode.ADVENTURE);
						if (!spawnsIte.hasNext()) {
							spawnsIte = spawns.iterator();
						}
					}
				}

			}.runTaskLater(Ioc.resolve(JavaPlugin.class), Tick.tick().fromDuration(spectatorTimeSeconds));
			}finally {
				complete();
			}
		}
	}

}
