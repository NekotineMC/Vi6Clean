package fr.nekotine.vi6clean.impl.tool;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.util.InventoryUtil;

public abstract class Tool {
	
	private ItemStack itemStack = makeInitialItemStack();
	
	private Player owner;
	
	protected abstract ItemStack makeInitialItemStack();
	
	public final ItemStack getItemStack() {
		return itemStack;
	}
	
	public final void setItemStack(ItemStack stack) {
		if (owner != null) {
			InventoryUtil.replaceItem(owner.getInventory(), itemStack, stack);
		}
		itemStack = stack;
	}

	public Player getOwner() {
		return owner;
	}

	public void setOwner(Player owner) {
		this.owner = owner;
	}
	
}
