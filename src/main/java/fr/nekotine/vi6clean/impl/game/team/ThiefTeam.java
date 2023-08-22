package fr.nekotine.vi6clean.impl.game.team;

import fr.nekotine.core.game.team.Team;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;

public class ThiefTeam extends Team{

	public void spawnInMinimap() {
		var map = Vi6Main.IOC.resolve(Vi6Game.class).getMap();
		var spawns = map.getGuardSpawns();
		if (spawns.size() < 1) {
			throw new RuntimeException("Impossible de teleporter les voleurs dans la minimap, aucun spawn n'est configure");
		}
		var spawnsIte = spawns.iterator();
		for(var thief : this){
			var loc = spawnsIte.next();
			thief.teleport(loc.toLocation(thief.getWorld()));
			if (!spawnsIte.hasNext()) {
				spawnsIte = spawns.iterator();
			}
		}
	}

}
