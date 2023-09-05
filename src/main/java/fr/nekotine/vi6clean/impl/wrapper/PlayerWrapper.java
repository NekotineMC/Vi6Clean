package fr.nekotine.vi6clean.impl.wrapper;

import org.bukkit.entity.Player;

import fr.nekotine.core.wrapper.WrapperBase;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;

public class PlayerWrapper extends WrapperBase<Player> {

	public PlayerWrapper(Player wrapped) {
		super(wrapped);
	}
	
	public boolean isThief() {
		return Vi6Main.IOC.resolve(Vi6Game.class).getThiefs().contains(wrapped);
	}

}
