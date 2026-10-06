package fr.nekotine.vi6clean.game.phase;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;

public abstract class AbstractPhase<Wrapper extends PlayerWrapper> implements Phase<Wrapper>{

	private final Map<Player,Wrapper> wrappers = new HashMap<Player, Wrapper>();
	
	public void setWrapperFor(Player player, Wrapper wrapper) {
		wrappers.put(player, wrapper);
	}
	
	@Override
	public Wrapper wrapperFor(Player player) {
		return wrappers.get(player);
	}
	
}
