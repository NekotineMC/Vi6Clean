package fr.nekotine.vi6clean.impl.tool;

import org.bukkit.inventory.ItemStack;

public abstract class Tool {
	
	/**
	 * L'{@link org.bukkit.inventory.ItemStack ItemStack} qui est lié à cet outil et le représente visuellement.
	 */
	private final ItemStack itemStack;
	
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
