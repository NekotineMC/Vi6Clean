package fr.nekotine.vi6clean.impl.tool;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import fr.nekotine.vi6clean.Vi6Main;

public abstract class Tool {

	private static int UNIQUE_ITEM_ID_COUNTER = 0;
	
	private static NamespacedKey UNIQUE_ITEM_ID_NAMESPACEDKEY;
	
	public static final NamespacedKey getUniqueItemIdNamespacedKey() {
		if (UNIQUE_ITEM_ID_NAMESPACEDKEY == null) {
			UNIQUE_ITEM_ID_NAMESPACEDKEY = new NamespacedKey(Vi6Main.getOneVi6Main(), "toolUniqueId");
		}
		return UNIQUE_ITEM_ID_NAMESPACEDKEY;
	}
	
	/**
	 * L'{@link org.bukkit.inventory.ItemStack ItemStack} qui est lié à cet outil et le représente visuellement.
	 */
	private final ItemStack itemStack;
	
	private boolean markedForRemoval;
	
	protected Tool(Material material) {
		this.itemStack = new ItemStack(material);
		itemStack.getItemMeta().getPersistentDataContainer().set(getUniqueItemIdNamespacedKey(), PersistentDataType.INTEGER, ++UNIQUE_ITEM_ID_COUNTER);
	}
	
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
