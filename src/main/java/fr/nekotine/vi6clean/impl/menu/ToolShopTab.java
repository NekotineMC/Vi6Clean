package fr.nekotine.vi6clean.impl.menu;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.menu.MenuItem;
import fr.nekotine.core.util.ItemStackUtil;

public class ToolShopTab {

	private List<MenuItem> items = new LinkedList<>();
	
	private MenuItem icon;
	
	/**
	 * Créée le tab à partir de son icon et l'ajout à la liste du layout donné.
	 * @param icon
	 * @param layout
	 */
	public ToolShopTab(ItemStack icon) {
		this.icon = new MenuItem(icon, null);
	}
	
	public List<MenuItem> getItems() {
		return items;
	}
	
	public MenuItem getIcon() {
		return icon;
	}
	
	public void displayActive(boolean active) {
		var item = icon.getItemStack();
		if (active) {
			item.addUnsafeEnchantment(Enchantment.MENDING, 1);
			ItemStackUtil.hideAllFlags(item);
		}else {
			ItemStackUtil.clearEnchants(item);
		}
	}
	
}
