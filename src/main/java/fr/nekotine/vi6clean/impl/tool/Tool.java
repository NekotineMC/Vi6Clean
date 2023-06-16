package fr.nekotine.vi6clean.impl.tool;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public abstract class Tool {
	
	private final ItemStack itemStack = new ItemStack(Material.STONE);
	
	private boolean markedForRemoval;
	
	public final ItemStack getItemStack() {
		return itemStack;
	}
	
	public final void setMarkedForRemoval(boolean markedForRemoval) {
		this.markedForRemoval = markedForRemoval;
	}
	
	public final boolean isMarkedForRemoval() {
		return markedForRemoval;
	}
	
}
