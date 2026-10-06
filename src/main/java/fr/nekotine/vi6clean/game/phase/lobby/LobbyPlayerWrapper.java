package fr.nekotine.vi6clean.game.phase.lobby;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import fr.nekotine.vi6clean.game.phase.PlayerWrapper;
import fr.nekotine.vi6clean.game.phase.lobby.event.LobbyPlayerReadyEvent;

public class LobbyPlayerWrapper extends PlayerWrapper {

	private boolean _isReady;
	
	public LobbyPlayerWrapper(Player wrapped) {
		super(wrapped);
	}
	
	public boolean isReady() {
		return _isReady;
	}
	
	public void setReady(boolean ready) {
		if (!_isReady && ready) {
			_isReady = ready;
			var evt = new LobbyPlayerReadyEvent(wrapped);
			Bukkit.getPluginManager().callEvent(evt);
			if (evt.isCancelled()) {
				_isReady = false;
				return;
			}
		}
	}

}
