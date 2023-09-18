package fr.nekotine.vi6clean.impl.tool;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.util.AssertUtil;
import fr.nekotine.core.util.InventoryUtil;

public abstract class Tool {
	
	private ItemStack itemStack;
	
	private Player owner;
	
	protected abstract ItemStack makeInitialItemStack();
	
	protected abstract void cleanup();
	
	public final ItemStack getItemStack() {
		if (itemStack == null) {
			itemStack = makeInitialItemStack();
		}
		return itemStack;
	}
	
	public final void setItemStack(ItemStack stack) {
		AssertUtil.nonNull(stack);
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
