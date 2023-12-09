package fr.nekotine.vi6clean.impl.status.event;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class EntityEmpStartEvent extends Event{
	private static final HandlerList handlers = new HandlerList();
	public static HandlerList getHandlerList() {
	    return handlers;
	}
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}
	
	//
	
	private final LivingEntity entity;
	public EntityEmpStartEvent(LivingEntity entity) {
		this.entity = entity;
	}
	public LivingEntity getEntity() {
		return entity;
	}
}
