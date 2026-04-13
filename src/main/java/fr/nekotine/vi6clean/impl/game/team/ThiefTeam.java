package fr.nekotine.vi6clean.impl.game.team;

import java.util.Map;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import fr.nekotine.core.game.team.Team;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.InMapState;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseInMap;
import fr.nekotine.vi6clean.impl.map.ThiefSpawn;
import fr.nekotine.vi6clean.impl.wrapper.InMapPhasePlayerWrapper;

public class ThiefTeam extends Team{

	public void spawnInMinimap() {
		var game = Ioc.resolve(Vi6Game.class);
		var inMapPhase = game.getPhaseMachine().getPhase(Vi6PhaseInMap.class);
		var map = inMapPhase.getMap();
		var spawns = map.getThiefMinimapSpawns();
		if (spawns.size() < 1) {
			throw new RuntimeException("Impossible de teleporter les voleurs dans la minimap, aucun spawn n'est configure");
		}
		var spawnsIte = spawns.iterator();
		for(var thief : this){
			var loc = spawnsIte.next();
			thief.teleport(loc.toLocation(game.getWorld()));
			thief.setGameMode(GameMode.ADVENTURE); // On change le gamemode dans le nouveau monde
			if (!spawnsIte.hasNext()) {
				spawnsIte = spawns.iterator();
			}
		}
	}
	
	public void spawnInMap(Map<Player, ThiefSpawn> spawnMap) {
		var game = Ioc.resolve(Vi6Game.class);
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		for(var thief : this){
			var loc = spawnMap.get(thief).getSpawnPoint().toLocation(game.getWorld());
			thief.teleport(loc);
			var wrap = wrappingModule.getWrapper(thief, InMapPhasePlayerWrapper.class);
			wrap.setCanLeaveMap(true);
			wrap.setState(InMapState.ENTERING);
			for (var guard : game.getGuards()) {
				guard.hideEntity(Ioc.resolve(JavaPlugin.class), thief);
			}
		}
	}

}
