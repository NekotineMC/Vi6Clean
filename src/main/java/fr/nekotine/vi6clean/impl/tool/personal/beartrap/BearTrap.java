package fr.nekotine.vi6clean.impl.tool.personal.beartrap;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;

public class BearTrap extends Tool{
	
	public BearTrap(ToolHandler<?> handler) {
		super(handler);
	}
	
	private Location location;
	
	private ArmorStand trap;
	
	private boolean armed;
	
	public Location getLocation() {
		return location;
	}
	
	public void setLocation(Location location) {
		this.location = location;
	}
	
	public boolean isArmed() {
		return armed;
	}
	
	public void setArmed(boolean value) {
		armed = value;
	}
	
	public ArmorStand getTrap() {
		return trap;
	}
	
	public void setTrap(ArmorStand value) {
		trap = value;
	}
	
	public boolean isPlaced() {
		return location != null;
	}
	
}
