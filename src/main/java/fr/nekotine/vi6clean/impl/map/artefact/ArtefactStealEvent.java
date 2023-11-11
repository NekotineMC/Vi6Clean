package fr.nekotine.vi6clean.impl.map.artefact;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ArtefactStealEvent extends Event{
	private final Artefact artefact;
	private final Player thief;
	public ArtefactStealEvent(Artefact artefact, Player thief) {
		this.artefact = artefact;
		this.thief = thief;
	}
	
	//
	
	public Artefact getArtefact() {
		return artefact;
	}
	public Player getThief() {
		return thief;
	}
	
	//
	
	private static final HandlerList handlers = new HandlerList();
	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}
	public static HandlerList getHandlerList() {
	    return handlers;
	}
}
