package fr.nekotine.vi6clean.impl.wrapper;

import java.util.Collection;
import java.util.Collections;

import org.bukkit.entity.Player;

import fr.nekotine.core.wrapper.WrapperBase;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.game.Vi6Game;

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
	
	public Collection<Player> ennemiTeam() {
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		switch(team) {
		case GUARD:
			return game.getThiefs();
		case THIEF:
			return game.getGuards();
		default:
			return Collections.emptySet();
		}
	}
	
	public Collection<Player> ourTeam() {
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		switch(team) {
		case GUARD:
			return game.getGuards();
		case THIEF:
			return game.getThiefs();
		default:
			return Collections.emptySet();
		}
	}

}
