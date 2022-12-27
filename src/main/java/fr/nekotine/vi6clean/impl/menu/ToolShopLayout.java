package fr.nekotine.vi6clean.impl.menu;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.menu.InvalidLayoutSizeException;
import fr.nekotine.core.inventory.menu.MenuItem;
import fr.nekotine.core.inventory.menu.MenuLayout;
import fr.nekotine.core.util.InventoryUtil;
import net.kyori.adventure.text.Component;

/**
 * Layout composé d'une rangée de {@link fr.nekotine.core.inventory.menu.MenuItem MenuItem} de control et de plusieurs tabs de {@link fr.nekotine.core.inventory.menu.MenuItem MenuItem}
 * @author XxGoldenbluexX
 *
 */
public class ToolShopLayout extends MenuLayout {

	private static final int MINIMAL_NB_ROW = 3;
	
	private List<MenuItem> controlItems;
	
	private List<ToolShopTab> tabs;
	
	private ItemStack separator;
	
	private ToolShopTab activeTab;
	
	public void addControlButton(MenuItem item) {
		controlItems.add(item);
	}
	
	public void addTab(ToolShopTab tab) {
		tabs.add(tab);
		if (activeTab == null) {
			activeTab = tab;
			activeTab.displayActive(true);
		}
	}
	
	public void setActiveTab(ToolShopTab tab) {
		if (activeTab != null) {
			activeTab.displayActive(false);
		}
		activeTab = tab;
		activeTab.displayActive(true);
	}
	
	public ToolShopLayout(Material separator) {
		super();
		this.separator = new ItemStack(separator);
		var meta = this.separator.getItemMeta();
		meta.displayName(Component.text(""));
		this.separator.setItemMeta(meta);
	}
	
	@Override
	public void arrange(Inventory inventory, int nbRow) {
		if (nbRow < MINIMAL_NB_ROW) {
			throw new InvalidLayoutSizeException(
					String.format("Le nombre de lignes du menu (%s) est inférieur à la valeur minimale utilisée par le layout (%s).",
							nbRow,
							MINIMAL_NB_ROW)
					);
		}
		inventory.clear();
		separateSections(inventory, nbRow);
		fillControlSection(inventory, nbRow);
		fillTabsSection(inventory, nbRow);
		fillActiveTab(inventory, nbRow);
	}

	@Override
	public MenuItem toMenuItem(ItemStack item) {
		var first = controlItems.stream().filter(i -> i.getItemStack().equals(item)).findFirst();
		if (first.isPresent()) {
			return first.get();
		}else {
			for (var tab : tabs) {
				var firstFromTab = tab.getItems().stream().filter(i -> i.getItemStack().equals(item)).findFirst();
				if (firstFromTab.isPresent()) {
					return firstFromTab.get();
				}
			}
		}
		return null;
	}
	
	private void separateSections(Inventory inventory, int nbRow) {
		InventoryUtil.paintRectangle(inventory, separator, 1, 0, 1, nbRow - 1);
		InventoryUtil.paintRectangle(inventory, separator, 2, 1, 8, 1);
	}
	
	private void fillControlSection(Inventory inventory, int nbRow) {
		InventoryUtil.fillRectangle(inventory, controlItems.stream().map(i -> i.getItemStack()).toList(), 0, 0, 0, nbRow - 1);
	}
	
	private void fillTabsSection(Inventory inventory, int nbRow) {
		InventoryUtil.fillRectangle(inventory, tabs.stream().map(i -> i.getIcon().getItemStack()).toList(), 2, 0, 8, 0);
	}
	
	private void fillActiveTab(Inventory inventory, int nbRow) {
		InventoryUtil.fillRectangle(inventory, activeTab.getItems().stream().map(i -> i.getItemStack()).toList(), 2, 2, 8, nbRow - 1);
	}

}
