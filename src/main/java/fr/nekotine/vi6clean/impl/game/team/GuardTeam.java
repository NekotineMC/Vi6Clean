package fr.nekotine.vi6clean.impl.game.team;

import fr.nekotine.core.game.team.Team;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseInMap;

public class GuardTeam extends Team{

	public void spawnInMap() {
		var inMapPhase = Vi6Main.IOC.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhaseInMap.class);
		var map = inMapPhase.getMap();
		var spawns = map.getGuardSpawns();
		if (spawns.size() < 1) {
			throw new RuntimeException("Impossible de teleporter les gardes dans la carte, aucun spawn n'est configure");
		}
		var spawnsIte = spawns.iterator();
		for(var guard : this){
			var loc = spawnsIte.next();
			guard.teleport(loc.toLocation(guard.getWorld()));
			if (!spawnsIte.hasNext()) {
				spawnsIte = spawns.iterator();
			}
		}
	}

}
