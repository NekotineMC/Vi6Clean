package fr.nekotine.vi6clean.impl.tool;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerSellToolEvent extends Event{
	private static final HandlerList handlers = new HandlerList();
	public static HandlerList getHandlerList() {
	    return handlers;
	}
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}
	
	//
	
	private Tool tool;
	private int price;
	private boolean removeCancelled = false; //Only cancel the removal of the tool, not the money given
	private boolean allCancelled = false; //Also cancel the money being given
	public PlayerSellToolEvent(Tool tool, int price) {
		this.tool = tool;
		this.price = price;
	}
	
	public Tool getTool() {
		return tool;
	}
	public void setTool(Tool tool) {
		this.tool = tool;
	}
	public int getPrice() {
		return price;
	}
	public void setPrice(int price) {
		this.price = price;
	}
	public boolean isRemoveCancelled() {
		return removeCancelled;
	}
	public void setRemoveCancelled(boolean removeCancelled) {
		this.removeCancelled = removeCancelled;
	}
	public boolean isAllCancelled() {
		return allCancelled;
	}
	public void setAllCancelled(boolean allCancelled) {
		this.allCancelled = allCancelled;
	}
}
