package fr.nekotine.vi6clean.impl.tool;

import org.bukkit.entity.Player;

public abstract class Tool {

	private static int lastId = 0;
	
	private Player owner;
	
	private final int id = ++lastId;
	
	private final ToolHandler<?> handler;
	
	public Tool(ToolHandler<?> handler) {
		this.handler = handler;
	}
	
	public ToolHandler<?> getHandler(){
		return handler;
	}
	
	public Player getOwner() {
		return owner;
	}
	
	public void setOwner(Player player) {
		owner = player;
	}

	public int getId() {
		return id;
	}

}
