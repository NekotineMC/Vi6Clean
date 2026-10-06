package fr.nekotine.vi6clean.game.phase;

import org.bukkit.entity.Player;

public interface Phase<Wrapper extends PlayerWrapper> {

	void onStart();
	
	void onEnd();
	
	Wrapper wrapperFor(Player player);
	
}
