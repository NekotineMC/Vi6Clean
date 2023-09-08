package fr.nekotine.vi6clean.impl.wrapper;

import org.bukkit.entity.Player;

import fr.nekotine.core.wrapper.WrapperBase;
import fr.nekotine.vi6clean.constant.Vi6Team;

public class PlayerWrapper extends WrapperBase<Player> {

	private Vi6Team team;
	
	public PlayerWrapper(Player wrapped) {
		super(wrapped);
	}
	
	public boolean isThief() {
		return team == Vi6Team.THIEF;
	}
	
	public boolean isGuard() {
		return team == Vi6Team.GUARD;
	}

	public Vi6Team getTeam() {
		return team;
	}

	public void setTeam(Vi6Team team) {
		this.team = team;
	}

}
