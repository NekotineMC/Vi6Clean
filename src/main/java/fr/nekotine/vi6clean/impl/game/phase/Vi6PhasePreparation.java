package fr.nekotine.vi6clean.impl.game.phase;

import org.bukkit.entity.Player;

import fr.nekotine.core.game.phase.CollectionPhase;

public class Vi6PhasePreparation extends CollectionPhase<Player>{
	
	@Override
	public void globalSetup() {
	}

	@Override
	public void globalTearDown() {
	}

	@Override
	public void itemSetup(Player item) {
		item.getInventory().clear();
	}

	@Override
	public void itemTearDown(Player item) {
		item.getInventory().clear();
	}

}
