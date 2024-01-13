package fr.nekotine.vi6clean.impl.wrapper;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;

import org.bukkit.entity.Player;

import fr.nekotine.core.bar.actionbar.ActionBarComponent;
import fr.nekotine.core.bar.actionbar.SharedActionBar;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.wrapper.WrapperBase;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Team;
import fr.nekotine.vi6clean.impl.game.Vi6Game;

public class PlayerWrapper extends WrapperBase<Player> {

	private Vi6Team team;
	
	//Used for invisibility
	private double squared_walked_distance = 0;
	
	private SharedActionBar actionBar;
	private ActionBarComponent artefactComponent;
	
	//
	
	public PlayerWrapper(Player wrapped) {
		super(wrapped);
		if(wrapped!=null) {
			actionBar = new SharedActionBar(wrapped);
			artefactComponent = new ActionBarComponent();
			actionBar.addComponent(artefactComponent);
		}
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
		var game = Ioc.resolve(Vi6Game.class);
		switch(team) {
		case GUARD:
			return game.getThiefs();
		case THIEF:
			return game.getGuards();
		default:
			return Collections.emptySet();
		}
	}
	
	public Stream<Player> ennemiTeamInMap() {
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		return ennemiTeam().stream().filter(e -> {
			var opt = wrappingModule.getWrapperOptional(e, InMapPhasePlayerWrapper.class);
			if (opt.isPresent()) {
				return opt.get().isInside();
			}
			return false;
		});
	}
	
	public Collection<Player> ourTeam() {
		var game = Ioc.resolve(Vi6Game.class);
		switch(team) {
		case GUARD:
			return game.getGuards();
		case THIEF:
			return game.getThiefs();
		default:
			return Collections.emptySet();
		}
	}
	
	public double getSquaredWalkedDistance() {
		return squared_walked_distance;
	}
	public void setSquaredWalkedDistance(double distance) {
		this.squared_walked_distance = distance;
	}
	
	public ActionBarComponent getArtefactComponent() {
		return artefactComponent;
	}
}
