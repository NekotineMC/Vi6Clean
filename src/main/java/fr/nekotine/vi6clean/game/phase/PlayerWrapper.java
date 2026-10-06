package fr.nekotine.vi6clean.game.phase;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import fr.nekotine.core.wrapper.WrapperBase;
import fr.nekotine.vi6clean.game.Vi6Team;
import fr.nekotine.vi6clean.game.phase.event.PlayerChangeTeamEvent;

public class PlayerWrapper extends WrapperBase<Player>{

	private Vi6Team _team = Vi6Team.None;
	
	public PlayerWrapper(Player wrapped) {
		super(wrapped);
	}
	
	public Vi6Team team() {
		return _team;
	}
	
	public void team(Vi6Team team) {
		if (_team != team) {
			var oldteam = _team;
			_team = team;
			var evt = new PlayerChangeTeamEvent(wrapped, oldteam, team);
			Bukkit.getPluginManager().callEvent(evt);
			if (evt.isCancelled()) {
				_team = oldteam;
				return;
			}
		}
	}

}
