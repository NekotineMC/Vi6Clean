package fr.nekotine.vi6clean.impl.game.team;

import org.bukkit.GameMode;

import fr.nekotine.core.game.team.Team;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.InMapState;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseInMap;
import fr.nekotine.vi6clean.impl.wrapper.InMapPhasePlayerWrapper;

public class GuardTeam extends Team{

	public void spawnInMap() {
		var game = Ioc.resolve(Vi6Game.class);
		var inMapPhase = game.getPhaseMachine().getPhase(Vi6PhaseInMap.class);
		var map = inMapPhase.getMap();
		var spawns = map.getGuardSpawns();
		if (spawns.size() < 1) {
			throw new RuntimeException("Impossible de teleporter les gardes dans la carte, aucun spawn n'est configure");
		}
		var spawnsIte = spawns.iterator();
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		for(var guard : this){
			var wrap = wrappingModule.getWrapper(guard, InMapPhasePlayerWrapper.class);
			wrap.setState(InMapState.INSIDE);
			var loc = spawnsIte.next();
			guard.teleport(loc.toLocation(game.getWorld()));
			guard.setGameMode(GameMode.ADVENTURE); // On change le gamemode dans le nouveau monde
			if (!spawnsIte.hasNext()) {
				spawnsIte = spawns.iterator();
			}
		}
	}

}
